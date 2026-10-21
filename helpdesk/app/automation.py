"""Exact, version-bound FAQ approval. Generated model text is never auto-sent."""
import time

from . import ai, delivery


def candidate(conn,ticket,body):
    if ticket['status']!='NEW' or ticket['priority'] not in ('NORMAL','LOW'):
        return None
    if ai.requires_human(ticket['subject']+'\n'+body,ticket['human_requested']):
        return None
    counts=conn.execute("SELECT COUNT(*),SUM(sender_type!='CUSTOMER') FROM messages WHERE ticket_id=?",(ticket['id'],)).fetchone()
    if counts[0]!=1 or counts[1]:
        return None
    if conn.execute('SELECT ticket_id FROM faq_deliveries WHERE ticket_id=?',(ticket['id'],)).fetchone():
        return None
    rule=conn.execute("SELECT f.*,a.title,a.body FROM faq_rules f JOIN kb_articles a ON a.id=f.article_id WHERE f.normalized_question=? AND f.active=1 AND a.status='PUBLISHED' AND a.version=f.article_version",(ai.normalize_question(body),)).fetchone()
    return dict(rule) if rule else None


def send_if_approved(conn,ticket,body):
    rule=candidate(conn,ticket,body)
    if not rule:
        return None
    answer=f"Trả lời tự động từ FAQ đã duyệt\n\n{rule['body']}\n\nNguồn: {rule['title']} (KB #{rule['article_id']}, v{rule['article_version']})"
    mid=delivery.enqueue(conn,ticket['id'],None,'BOT',answer)
    now=time.time()
    conn.execute('INSERT INTO faq_deliveries(ticket_id,rule_id,message_id,created_at,article_version) VALUES(?,?,?,?,?)',(ticket['id'],rule['id'],mid,now,rule['article_version']))
    conn.execute('INSERT INTO ticket_events(ticket_id,description,created_at) VALUES(?,?,?)',(ticket['id'],f"FAQ đã duyệt #{rule['id']} tạo trả lời tự động; chờ gửi",now))
    return mid
