"""Ticket workflow, durable AI jobs and periodic SLA monitoring."""
import json
import time

from fastapi import HTTPException

from . import ai, automation, db, delivery

STAFF = {'ADMIN','SUPERVISOR','AGENT'}
MANAGERS = {'ADMIN','SUPERVISOR'}
TRANSITIONS = {
    'NEW':{'IN_PROGRESS'}, 'IN_PROGRESS':{'WAITING_CUSTOMER','RESOLVED'},
    'WAITING_CUSTOMER':{'IN_PROGRESS','RESOLVED'}, 'RESOLVED':{'IN_PROGRESS','CLOSED'}, 'CLOSED':set(),
}


def event(conn, ticket_id, actor_id, description):
    conn.execute("INSERT INTO ticket_events(ticket_id,actor_id,description,created_at) VALUES(?,?,?,?)", (ticket_id,actor_id,description,time.time()))


def access(conn, ticket_id, user, write=False):
    ticket = conn.execute('SELECT * FROM tickets WHERE id=?',(ticket_id,)).fetchone()
    if not ticket:
        raise HTTPException(404,'Không tìm thấy ticket')
    if user['role'] == 'CUSTOMER' and ticket['customer_id'] != user['id']:
        raise HTTPException(404,'Không tìm thấy ticket')
    if user['role'] == 'AGENT':
        allowed = ticket['team_id'] == user['team_id']
        if not allowed or (write and ticket['assignee_id'] not in (None,user['id'])):
            raise HTTPException(403,'Ticket không thuộc phạm vi xử lý của bạn')
    return ticket


def check_version(ticket, version):
    if ticket['version'] != version:
        raise HTTPException(409,{'message':'Ticket đã thay đổi. Tải lại để cập nhật.','version':ticket['version']})


def queue_ai(conn, ticket_id):
    existing = conn.execute("SELECT id FROM jobs WHERE ticket_id=? AND status IN ('QUEUED','RUNNING')",(ticket_id,)).fetchone()
    if existing:
        return existing['id']
    now = time.time()
    return conn.execute("INSERT INTO jobs(ticket_id,created_at,updated_at) VALUES(?,?,?)",(ticket_id,now,now)).lastrowid


def create_ticket(conn, customer_id, subject, body, priority='NORMAL', channel='WEB', external_id=None):
    if external_id:
        old = conn.execute('SELECT ticket_id FROM channel_events WHERE channel=? AND external_event_id=?',(channel,external_id)).fetchone()
        if old:
            return old['ticket_id'],True
    customer = conn.execute("SELECT id FROM users WHERE id=? AND role='CUSTOMER' AND active=1",(customer_id,)).fetchone()
    if not customer:
        raise HTTPException(422,'Khách hàng không hợp lệ')
    now = time.time()
    tid = conn.execute('INSERT INTO tickets(customer_id,subject,channel,priority,created_at,updated_at) VALUES(?,?,?,?,?,?)',(customer_id,subject,channel,priority,now,now)).lastrowid
    conn.execute("INSERT INTO messages(ticket_id,sender_id,sender_type,visibility,body,created_at) VALUES(?,?,'CUSTOMER','PUBLIC',?,?)",(tid,customer_id,body,now))
    policy = conn.execute('SELECT * FROM sla_policies WHERE priority=? ORDER BY version DESC LIMIT 1',(priority,)).fetchone()
    conn.execute('INSERT INTO ticket_sla(ticket_id,policy_id,response_due,resolution_due) VALUES(?,?,?,?)',(tid,policy['id'],now+policy['response_seconds'],now+policy['resolution_seconds']))
    if external_id:
        conn.execute('INSERT INTO channel_events(channel,external_event_id,ticket_id) VALUES(?,?,?)',(channel,external_id,tid))
    event(conn,tid,customer_id,f'Tiếp nhận qua {channel}')
    queue_ai(conn,tid)
    return tid,False


def route(conn, ticket, category):
    rule = conn.execute('SELECT team_id FROM categories WHERE code=?',(category,)).fetchone()
    if not rule:
        return
    agent = conn.execute("SELECT u.id,COUNT(t.id) AS load FROM users u LEFT JOIN tickets t ON t.assignee_id=u.id AND t.status NOT IN ('RESOLVED','CLOSED') WHERE u.role='AGENT' AND u.team_id=? AND u.active=1 AND u.available=1 GROUP BY u.id ORDER BY load,u.id LIMIT 1",(rule['team_id'],)).fetchone()
    conn.execute('UPDATE tickets SET category=?,team_id=?,assignee_id=? WHERE id=?',(category,rule['team_id'],agent['id'] if agent else None,ticket['id']))
    event(conn,ticket['id'],None,f'Phân loại {category}; định tuyến nhóm {rule["team_id"]}')


def process_one_job(path=None):
    with db.transaction(path) as conn:
        job = conn.execute("SELECT * FROM jobs WHERE status='QUEUED' ORDER BY id LIMIT 1").fetchone()
        if not job:
            return False
        jid,tid = job['id'],job['ticket_id']
        conn.execute("UPDATE jobs SET status='RUNNING',updated_at=? WHERE id=?",(time.time(),jid))
        ticket = dict(conn.execute('SELECT * FROM tickets WHERE id=?',(tid,)).fetchone())
        # Do not expose internal notes or staff replies to the model.
        messages = conn.execute("SELECT body FROM messages WHERE ticket_id=? AND sender_type='CUSTOMER' AND visibility='PUBLIC' ORDER BY id DESC LIMIT 3",(tid,)).fetchall()
        text = ticket['subject']+'\n'+'\n'.join(m['body'] for m in reversed(messages))
        latest_body=messages[0]['body'] if messages else ''
        approved=automation.candidate(conn,ticket,latest_body)
        articles = [dict(a) for a in conn.execute("SELECT * FROM kb_articles WHERE status='PUBLISHED'")]
    started = time.monotonic()
    try:
        if approved:
            category,confidence=ai.classify(text)
            result={'category':category,'confidence':confidence,'answer':approved['body'],
                'sources':[{'id':approved['article_id'],'version':approved['article_version'],'title':approved['title'],'score':1.0}],
                'mode':'APPROVED_FAQ','fallback_reason':None}
        else:
            result = ai.suggest(text,articles,bool(ticket['human_requested']))
        with db.transaction(path) as conn:
            current = conn.execute('SELECT * FROM tickets WHERE id=?',(tid,)).fetchone()
            if current['version'] != ticket['version'] or current['status'] in ('RESOLVED','CLOSED'):
                conn.execute("UPDATE jobs SET status='STALE',updated_at=?,error='Ticket changed during analysis' WHERE id=?",(time.time(),jid))
                return True
            for source in result['sources']:
                live = conn.execute("SELECT id FROM kb_articles WHERE id=? AND version=? AND status='PUBLISHED'",(source['id'],source['version'])).fetchone()
                if not live:
                    result.update(answer=None,sources=[],fallback_reason='Nguồn KB đã thay đổi; cần chạy lại phân tích.')
                    break
            if result['category'] and current['team_id'] is None:
                route(conn,current,result['category'])
            auto_message=automation.send_if_approved(conn,current,latest_body) if approved and result['answer'] else None
            if approved and not auto_message:
                result['mode']='LOCAL_EXTRACTIVE'
            now = time.time()
            conn.execute('UPDATE tickets SET version=version+1,updated_at=? WHERE id=?',(now,tid))
            version = current['version']+1
            conn.execute('INSERT INTO ai_runs(ticket_id,category,confidence,answer,sources,mode,fallback_reason,latency_ms,created_at,ticket_version) VALUES(?,?,?,?,?,?,?,?,?,?)',
                (tid,result['category'],result['confidence'],result['answer'],json.dumps(result['sources'],ensure_ascii=False),result['mode'],result['fallback_reason'],round((time.monotonic()-started)*1000,1),now,version))
            event(conn,tid,None,'FAQ đã duyệt được đưa vào hàng đợi gửi' if auto_message else 'AI tạo bản nháp' if result['answer'] else 'AI chuyển Agent: '+str(result['fallback_reason']))
            conn.execute("UPDATE jobs SET status='DONE',updated_at=? WHERE id=?",(now,jid))
    except Exception:
        with db.transaction(path) as conn:
            conn.execute("UPDATE jobs SET status='FAILED',updated_at=?,error='Analysis failed; manual review required' WHERE id=?",(time.time(),jid))
            event(conn,tid,None,'AI lỗi; ticket vẫn chờ nhân viên xử lý')
    return True


def scan_sla(path=None, now=None):
    now = now or time.time()
    with db.transaction(path) as conn:
        rows = conn.execute("SELECT t.id,t.created_at,s.* FROM tickets t JOIN ticket_sla s ON s.ticket_id=t.id WHERE t.status!='CLOSED'").fetchall()
        for row in rows:
            for kind,due,done in [('RESPONSE',row['response_due'],row['first_response_at']),('RESOLUTION',row['resolution_due'],row['resolved_at'])]:
                if done is not None:
                    continue
                if now >= due:
                    level='OVERDUE'
                elif now >= row['created_at']+(due-row['created_at'])*0.8:
                    level='WARNING'
                else:
                    continue
                conn.execute('INSERT OR IGNORE INTO sla_alerts(ticket_id,deadline_type,level,created_at) VALUES(?,?,?,?)',(row['id'],kind,level,now))


def worker(stop, path=None):
    last_scan=0
    while not stop.is_set():
        try:
            delivery.process_one(path)
            process_one_job(path)
            if time.monotonic()-last_scan >= 15:
                scan_sla(path)
                last_scan=time.monotonic()
        except Exception:
            # The durable queue and next scan allow recovery; do not kill intake.
            import logging
            logging.exception('Background worker iteration failed')
        stop.wait(0.5)
