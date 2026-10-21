import json
import io
import time

import pytest
from fastapi.testclient import TestClient

from app import ai, db, delivery, services
from app.main import create_app


@pytest.fixture
def setup(tmp_path, monkeypatch):
    monkeypatch.delenv('HELPDESK_OLLAMA_MODEL',raising=False)
    monkeypatch.setenv('HELPDESK_DEMO_PASSWORD','Demo@20261')
    path=tmp_path/'test.sqlite3'
    with TestClient(create_app(path,run_worker=False)) as client:
        yield client,path


def login(client,role='supervisor'):
    response=client.post('/api/auth/login',json={'email':role+'@helpdesk.local','password':'Demo@20261'})
    assert response.status_code==200,response.text
    return response.json()


def create(client,subject='Quên mật khẩu tài khoản',body='Tôi quên mật khẩu, làm sao để đặt lại?'):
    response=client.post('/api/tickets',json={'subject':subject,'body':body,'priority':'NORMAL'})
    assert response.status_code==201,response.text
    return response.json()['id']


def test_session_and_logout(setup):
    client,_=setup
    assert client.get('/api/tickets').status_code==401
    login(client)
    assert client.get('/api/auth/me').status_code==200
    assert client.cookies.get('helpdesk_session')
    client.post('/api/auth/logout')
    assert client.get('/api/auth/me').status_code==401


def test_customer_isolation_and_internal_notes(setup):
    client,_=setup
    login(client)
    detail=client.get('/api/tickets/1').json()
    assert client.post('/api/tickets/1/messages',json={'body':'SECRET STAFF NOTE','visibility':'INTERNAL','version':detail['version']}).status_code==201
    login(client,'customer')
    assert client.get('/api/tickets/2').status_code==404
    own=client.get('/api/tickets/1').json()
    assert all(m['body']!='SECRET STAFF NOTE' for m in own['messages'])
    assert 'events' not in own and 'ai' not in own
    assert client.get('/api/reports').status_code==403
    assert client.post('/api/tickets/1/messages',json={'body':'fake note','visibility':'INTERNAL','version':own['version']}).status_code==403


def test_agent_team_boundary(setup):
    client,_=setup
    login(client,'agent')
    assert client.get('/api/tickets/1').status_code==403
    assert client.get('/api/tickets/3').status_code==200
    ids={t['id'] for t in client.get('/api/tickets').json()['items']}
    assert ids=={3}
    assert client.patch('/api/tickets/3/assignment',json={'team_id':1,'assignee_id':3,'version':1}).status_code==403


def test_duplicate_email_is_atomic(setup):
    client,path=setup
    login(client)
    data={'customer_id':6,'subject':'Hóa đơn thanh toán','body':'Tôi muốn xem hóa đơn thanh toán','external_event_id':'message-001'}
    first=client.post('/api/channels/email/events',json=data)
    second=client.post('/api/channels/email/events',json=data)
    assert first.status_code==second.status_code==200
    assert first.json()['id']==second.json()['id']
    assert second.json()['duplicate'] is True
    with db.transaction(path) as conn:
        assert conn.execute('SELECT COUNT(*) FROM messages WHERE ticket_id=?',(first.json()['id'],)).fetchone()[0]==1


def test_full_ticket_ai_reply_resolution_rating(setup):
    client,path=setup
    login(client,'customer')
    tid=create(client)
    assert services.process_one_job(path)
    login(client)
    ticket=client.get(f'/api/tickets/{tid}').json()
    assert ticket['category']=='ACCOUNT'
    assert ticket['team_id']==3
    assert ticket['ai']['answer'] and ticket['ai']['sources']
    assert len(ticket['messages'])==1  # AI drafts are not sent automatically.
    assert client.patch(f'/api/tickets/{tid}/status',json={'status':'IN_PROGRESS','version':ticket['version']}).status_code==200
    ticket=client.get(f'/api/tickets/{tid}').json()
    assert client.post(f'/api/tickets/{tid}/messages',json={'body':'Vui lòng làm theo hướng dẫn đặt lại mật khẩu.','version':ticket['version']}).status_code==201
    assert delivery.process_one(path)
    ticket=client.get(f'/api/tickets/{tid}').json()
    assert ticket['first_response_at'] is not None
    assert client.patch(f'/api/tickets/{tid}/status',json={'status':'RESOLVED','version':ticket['version']}).status_code==200
    login(client,'customer')
    assert client.post(f'/api/tickets/{tid}/ratings',json={'score':5,'comment':'Đã đăng nhập được'}).status_code==201
    assert client.post(f'/api/tickets/{tid}/ratings',json={'score':4}).status_code==409
    ticket=client.get(f'/api/tickets/{tid}').json()
    assert client.patch(f'/api/tickets/{tid}/status',json={'status':'CLOSED','version':ticket['version']}).status_code==200
    ticket=client.get(f'/api/tickets/{tid}').json()
    assert client.post(f'/api/tickets/{tid}/messages',json={'body':'Tin nhắn sau đóng','version':ticket['version']}).status_code==409
    login(client)
    report=client.get('/api/reports').json()
    assert report['csat']==5 and report['rating_count']==1


def test_stale_update_and_invalid_transition(setup):
    client,_=setup
    login(client)
    assert client.patch('/api/tickets/3/status',json={'status':'CLOSED','version':1}).status_code==409
    assert client.patch('/api/tickets/3/status',json={'status':'IN_PROGRESS','version':1}).status_code==200
    assert client.patch('/api/tickets/3/status',json={'status':'RESOLVED','version':1}).status_code==409
    assert client.get('/api/tickets/3').json()['status']=='IN_PROGRESS'


def test_waiting_and_resolved_reopen(setup):
    client,_=setup
    login(client)
    for target in ['IN_PROGRESS','WAITING_CUSTOMER']:
        t=client.get('/api/tickets/1').json()
        assert client.patch('/api/tickets/1/status',json={'status':target,'version':t['version']}).status_code==200
    login(client,'customer')
    t=client.get('/api/tickets/1').json()
    assert client.post('/api/tickets/1/messages',json={'body':'Tôi đã thử rồi','version':t['version']}).status_code==201
    assert client.get('/api/tickets/1').json()['status']=='IN_PROGRESS'
    login(client)
    t=client.get('/api/tickets/1').json()
    client.patch('/api/tickets/1/status',json={'status':'RESOLVED','version':t['version']})
    login(client,'customer')
    t=client.get('/api/tickets/1').json()
    client.post('/api/tickets/1/messages',json={'body':'Vẫn còn lỗi đăng nhập','version':t['version']})
    assert client.get('/api/tickets/1').json()['resolved_at'] is None


def test_sla_scan_deduplicates_and_snapshot(setup):
    client,path=setup
    with db.transaction(path) as conn:
        before=dict(conn.execute('SELECT * FROM ticket_sla WHERE ticket_id=1').fetchone())
    services.scan_sla(path,now=time.time()+200000)
    services.scan_sla(path,now=time.time()+200000)
    with db.transaction(path) as conn:
        rows=conn.execute('SELECT * FROM sla_alerts').fetchall()
        keys={(r['ticket_id'],r['deadline_type'],r['level']) for r in rows}
        assert len(rows)==len(keys)==6
    login(client,'admin')
    assert client.post('/api/sla/policies',json={'priority':'NORMAL','response_seconds':600,'resolution_seconds':1200}).status_code==201
    with db.transaction(path) as conn:
        after=dict(conn.execute('SELECT * FROM ticket_sla WHERE ticket_id=1').fetchone())
    assert before==after


def test_unknown_question_and_model_failure_fallback(setup,monkeypatch):
    client,path=setup
    login(client,'customer')
    tid=create(client,'Xin hỏi vận chuyển quốc tế','Có giao bưu phẩm sang Nam Cực hay không?')
    services.process_one_job(path)
    login(client)
    t=client.get(f'/api/tickets/{tid}').json()
    assert t['ai']['answer'] is None and t['ai']['fallback_reason']
    monkeypatch.setenv('HELPDESK_OLLAMA_MODEL','test-model')
    def unavailable(*args,**kwargs):
        raise OSError('network unavailable')
    monkeypatch.setattr(ai,'urlopen',unavailable)
    login(client,'customer')
    tid=create(client)
    services.process_one_job(path)
    login(client)
    t=client.get(f'/api/tickets/{tid}').json()
    assert t['ai']['mode']=='OLLAMA_FAILED'
    assert t['ai']['answer'] is None and t['status']=='NEW'


def test_kb_publication_permissions_and_source_retirement(setup):
    client,path=setup
    login(client,'customer')
    tid=create(client)
    services.process_one_job(path)
    login(client)
    t=client.get(f'/api/tickets/{tid}').json()
    source=t['ai']['sources'][0]
    assert client.post(f'/api/kb/articles/{source["id"]}/retire').status_code==200
    t=client.get(f'/api/tickets/{tid}').json()
    assert t['ai']['answer'] is None and t['ai']['stale']
    login(client,'agent')
    draft=client.post('/api/kb/articles',json={'title':'Bản nháp kỹ thuật','body':'Nội dung này đang được chuẩn bị và chưa xuất bản.','category':'TECHNICAL'}).json()
    assert client.post(f'/api/kb/articles/{draft["id"]}/publish').status_code==403
    login(client,'customer')
    ids={a['id'] for a in client.get('/api/kb/articles').json()}
    assert source['id'] not in ids and draft['id'] not in ids


def test_ai_does_not_save_result_for_modified_ticket(setup,monkeypatch):
    client,path=setup
    login(client,'customer')
    tid=create(client)
    original=ai.suggest
    def slow_suggest(*args,**kwargs):
        with db.transaction(path) as conn:
            conn.execute('UPDATE tickets SET version=version+1 WHERE id=?',(tid,))
        return original(*args,**kwargs)
    monkeypatch.setattr(ai,'suggest',slow_suggest)
    services.process_one_job(path)
    with db.transaction(path) as conn:
        assert conn.execute('SELECT status FROM jobs WHERE ticket_id=?',(tid,)).fetchone()[0]=='STALE'
        assert conn.execute('SELECT COUNT(*) FROM ai_runs WHERE ticket_id=?',(tid,)).fetchone()[0]==0


def test_html_assets_and_cross_origin_rejection(setup):
    client,_=setup
    assert client.get('/').status_code==200
    assert client.get('/static/app.js').status_code==200
    assert client.get('/static/styles.css').status_code==200
    res=client.post('/api/auth/login',headers={'Origin':'https://evil.example'},json={'email':'admin@helpdesk.local','password':'Demo@20261'})
    assert res.status_code==403


def test_admin_user_creation_and_assignment_validation(setup):
    client,_=setup
    login(client,'admin')
    payload={'name':'Agent mới','email':'new@helpdesk.local','password':'NewPassword123','role':'AGENT','team_id':1}
    assert client.post('/api/users',json=payload).status_code==201
    assert client.post('/api/users',json=payload).status_code==409
    assert client.patch('/api/tickets/3/assignment',json={'team_id':2,'assignee_id':3,'version':1}).status_code==422


def test_blank_input_rejected_and_cookie_flags(setup):
    client,_=setup
    response=client.post('/api/auth/login',json={'email':'customer@helpdesk.local','password':'Demo@20261'})
    assert 'HttpOnly' in response.headers['set-cookie']
    assert 'SameSite=strict' in response.headers['set-cookie']
    assert client.post('/api/tickets',json={'subject':'   ','body':'      '}).status_code==422


def test_ollama_adapter_citations(setup,monkeypatch):
    _,path=setup
    with db.transaction(path) as conn:
        articles=[dict(a) for a in conn.execute("SELECT * FROM kb_articles WHERE status='PUBLISHED'")]
    monkeypatch.setenv('HELPDESK_OLLAMA_MODEL','fixture-model')
    monkeypatch.setattr(ai,'urlopen',lambda *args,**kwargs:io.BytesIO(json.dumps({'response':'Chọn Quên mật khẩu trên trang đăng nhập [1]'}).encode()))
    result=ai.suggest('Làm sao để đặt lại mật khẩu tài khoản?',articles)
    assert result['mode']=='OLLAMA_DRAFT' and result['answer']
    monkeypatch.setattr(ai,'urlopen',lambda *args,**kwargs:io.BytesIO(json.dumps({'response':'Thực hiện hoàn tiền [999]'}).encode()))
    result=ai.suggest('Làm sao để đặt lại mật khẩu tài khoản?',articles)
    assert result['mode']=='OLLAMA_FAILED' and result['answer'] is None


def test_routing_keeps_ticket_queued_when_no_agent_available(setup):
    client,path=setup
    login(client,'customer')
    tid=create(client)
    with db.transaction(path) as conn:
        conn.execute("UPDATE users SET available=0 WHERE role='AGENT'")
    services.process_one_job(path)
    login(client)
    ticket=client.get(f'/api/tickets/{tid}').json()
    assert ticket['team_id']==3 and ticket['category']=='ACCOUNT'
    assert ticket['assignee_id'] is None and ticket['status']=='NEW'


FAQ_QUESTION='Làm sao để đặt lại mật khẩu tài khoản?'


def approve_rule(client):
    login(client)
    response=client.post('/api/kb/articles/1/faq-rules',json={'question':FAQ_QUESTION,'article_version':1})
    assert response.status_code==201,response.text
    return response.json()['id']


def test_faq_approval_permissions_and_duplicate_normalization(setup):
    client,_=setup
    for who in ['customer','agent']:
        login(client,who)
        assert client.post('/api/kb/articles/1/faq-rules',json={'question':FAQ_QUESTION,'article_version':1}).status_code==403
    approve_rule(client)
    assert client.post('/api/kb/articles/1/faq-rules',json={'question':'LAM SAO DE DAT LAI MAT KHAU TAI KHOAN','article_version':1}).status_code==409
    assert client.post('/api/kb/articles/1/faq-rules',json={'question':'Tôi cần hoàn tiền giao dịch','article_version':1}).status_code==422
    assert client.post('/api/kb/articles/1/faq-rules',json={'question':'Một câu hỏi khác cần duyệt','article_version':99}).status_code==409


def test_approved_faq_delivery_is_once_and_does_not_resolve(setup):
    client,path=setup
    approve_rule(client)
    login(client,'customer')
    tid=create(client,body=FAQ_QUESTION)
    services.process_one_job(path)
    # Queued responses are not yet visible to customers and do not stop SLA.
    ticket=client.get(f'/api/tickets/{tid}').json()
    assert len(ticket['messages'])==1 and ticket['first_response_at'] is None
    assert delivery.process_one(path)
    ticket=client.get(f'/api/tickets/{tid}').json()
    assert len(ticket['messages'])==2 and ticket['messages'][1]['sender_type']=='BOT'
    assert 'KB #1, v1' in ticket['messages'][1]['body']
    assert ticket['first_response_at'] is not None and ticket['status']=='NEW'
    with db.transaction(path) as conn:
        services.queue_ai(conn,tid)
    services.process_one_job(path)
    assert not delivery.process_one(path)
    with db.transaction(path) as conn:
        assert conn.execute('SELECT COUNT(*) FROM faq_deliveries WHERE ticket_id=?',(tid,)).fetchone()[0]==1


@pytest.mark.parametrize('change',['disable','retire','edit'])
def test_faq_old_approval_cannot_send_after_kb_change(setup,change):
    client,path=setup
    fid=approve_rule(client)
    if change=='disable':
        client.patch(f'/api/faq-rules/{fid}',json={'active':False,'article_version':1})
    elif change=='retire':
        client.post('/api/kb/articles/1/retire')
    else:
        assert client.put('/api/kb/articles/1',json={'title':'Hướng dẫn mới','body':'Nội dung thay đổi cần được trưởng nhóm đọc lại trước khi cho tự trả lời.','category':'ACCOUNT','version':1}).status_code==200
        client.post('/api/kb/articles/1/publish')
    login(client,'customer')
    tid=create(client,body=FAQ_QUESTION)
    services.process_one_job(path)
    with db.transaction(path) as conn:
        assert conn.execute('SELECT COUNT(*) FROM faq_deliveries WHERE ticket_id=?',(tid,)).fetchone()[0]==0


@pytest.mark.parametrize('subject,body,priority',[
    ('Tôi muốn gặp nhân viên',FAQ_QUESTION,'NORMAL'),
    ('Trợ giúp mật khẩu',FAQ_QUESTION,'HIGH'),
    ('Trợ giúp mật khẩu',FAQ_QUESTION+' Vui lòng hoàn tiền.','NORMAL'),
])
def test_faq_respects_human_request_priority_and_exact_match(setup,subject,body,priority):
    client,path=setup
    approve_rule(client)
    login(client,'customer')
    response=client.post('/api/tickets',json={'subject':subject,'body':body,'priority':priority})
    tid=response.json()['id']
    services.process_one_job(path)
    with db.transaction(path) as conn:
        assert conn.execute('SELECT COUNT(*) FROM faq_deliveries WHERE ticket_id=?',(tid,)).fetchone()[0]==0


def test_failed_delivery_retry_sla_and_customer_visibility(setup):
    client,path=setup
    login(client)
    tid=client.post('/api/channels/email/events',json={'customer_id':6,'subject':'Cần hỗ trợ thanh toán','body':'Tôi cần kiểm tra hóa đơn.','external_event_id':'delivery-test'}).json()['id']
    assert client.post('/api/channels/email/simulation',json={'failures_remaining':1}).status_code==200
    sent=client.post(f'/api/tickets/{tid}/messages',json={'body':'Đây là phản hồi kiểm thử.','version':1})
    assert sent.status_code==201
    mid=sent.json()['message_id']
    delivery.process_one(path)
    ticket=client.get(f'/api/tickets/{tid}').json()
    assert ticket['messages'][-1]['delivery_status']=='FAILED'
    assert ticket['first_response_at'] is None
    assert ticket['outbox'][0]['attempts']==1
    client.patch(f'/api/tickets/{tid}/status',json={'status':'IN_PROGRESS','version':ticket['version']})
    ticket=client.get(f'/api/tickets/{tid}').json()
    assert client.patch(f'/api/tickets/{tid}/status',json={'status':'RESOLVED','version':ticket['version']}).status_code==409
    login(client,'customer')
    assert len(client.get(f'/api/tickets/{tid}').json()['messages'])==1
    assert client.post(f'/api/messages/{mid}/retry').status_code==403
    login(client)
    assert client.post(f'/api/messages/{mid}/retry').status_code==200
    assert client.post(f'/api/messages/{mid}/retry').status_code==409
    assert delivery.process_one(path)
    assert not delivery.process_one(path)
    ticket=client.get(f'/api/tickets/{tid}').json()
    assert ticket['first_response_at'] is not None
    assert ticket['outbox'][0]['attempts']==2 and ticket['outbox'][0]['status']=='DELIVERED'
    login(client,'customer')
    assert len(client.get(f'/api/tickets/{tid}').json()['messages'])==2


def test_faq_approval_revoked_during_analysis_is_rechecked(setup,monkeypatch):
    client,path=setup
    fid=approve_rule(client)
    login(client,'customer')
    tid=create(client,body=FAQ_QUESTION)
    original=ai.classify
    def revoke(text):
        with db.transaction(path) as conn:
            conn.execute('UPDATE faq_rules SET active=0 WHERE id=?',(fid,))
        return original(text)
    monkeypatch.setattr(ai,'classify',revoke)
    services.process_one_job(path)
    with db.transaction(path) as conn:
        assert conn.execute('SELECT COUNT(*) FROM faq_deliveries WHERE ticket_id=?',(tid,)).fetchone()[0]==0


def test_additive_migrations_keep_existing_data(setup):
    client,path=setup
    login(client)
    before=client.get('/api/tickets/1').json()
    with db.transaction(path) as conn:
        for table in ['faq_deliveries','faq_rules','outbox','channel_simulation']:
            conn.execute('DROP TABLE '+table)
        conn.execute('DELETE FROM schema_versions WHERE version>=2')
    db.initialize(path)
    after=client.get('/api/tickets/1').json()
    assert before['subject']==after['subject'] and before['messages']==after['messages']
    with db.transaction(path) as conn:
        assert conn.execute('SELECT MAX(version) FROM schema_versions').fetchone()[0]==3
        assert conn.execute('SELECT COUNT(*) FROM users').fetchone()[0]==7


@pytest.mark.parametrize('change',['retire','disable','customer-message','replace-and-reapprove'])
def test_faq_is_cancelled_if_invalidated_while_queued(setup,change):
    client,path=setup
    fid=approve_rule(client)
    login(client,'customer')
    tid=create(client,body=FAQ_QUESTION)
    services.process_one_job(path)
    if change=='customer-message':
        t=client.get(f'/api/tickets/{tid}').json()
        client.post(f'/api/tickets/{tid}/messages',json={'body':'Tôi muốn gặp nhân viên','version':t['version']})
    else:
        login(client)
        if change=='retire':
            client.post('/api/kb/articles/1/retire')
        elif change=='disable':
            client.patch(f'/api/faq-rules/{fid}',json={'active':False,'article_version':1})
        else:
            client.put('/api/kb/articles/1',json={'title':'Phiên bản hướng dẫn mới','body':'Nội dung mới phải không làm sống lại câu trả lời cũ còn trong hàng đợi.','category':'ACCOUNT','version':1})
            client.post('/api/kb/articles/1/publish')
            assert client.patch(f'/api/faq-rules/{fid}',json={'active':True,'article_version':3}).status_code==200
    assert delivery.process_one(path)
    login(client)
    t=client.get(f'/api/tickets/{tid}').json()
    assert t['messages'][1]['delivery_status']=='CANCELLED'
    assert t['first_response_at'] is None
    assert client.post(f'/api/messages/{t["messages"][1]["id"]}/retry').status_code==409


def test_upgrade_from_v2_cancels_legacy_faq_without_snapshot(setup):
    client,path=setup
    approve_rule(client)
    login(client,'customer')
    tid=create(client,body=FAQ_QUESTION)
    services.process_one_job(path)
    with db.transaction(path) as conn:
        conn.execute('ALTER TABLE faq_deliveries DROP COLUMN article_version')
        conn.execute('DELETE FROM schema_versions WHERE version=3')
    db.initialize(path)
    assert delivery.process_one(path)
    with db.transaction(path) as conn:
        assert conn.execute('SELECT status FROM outbox').fetchone()[0]=='CANCELLED'
        assert conn.execute('SELECT first_response_at FROM ticket_sla WHERE ticket_id=?',(tid,)).fetchone()[0] is None
