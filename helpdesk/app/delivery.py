"""Transactional outbox for local portal and simulated email delivery.

No SMTP/network send occurs. Retries use the original message, preserving identity.
"""
import time

from . import db


class PortalAdapter:
    def send(self, conn, message):
        return 'PORTAL'


class SimulatedEmailAdapter:
    def send(self, conn, message):
        failures=conn.execute("SELECT failures_remaining FROM channel_simulation WHERE channel='EMAIL'").fetchone()[0]
        if failures:
            conn.execute("UPDATE channel_simulation SET failures_remaining=failures_remaining-1 WHERE channel='EMAIL'")
            raise OSError('Lỗi gửi email mô phỏng; chưa chuyển tới khách hàng.')
        return 'SIMULATED_EMAIL'


def enqueue(conn,ticket_id,sender_id,sender_type,body):
    now=time.time()
    mid=conn.execute("INSERT INTO messages(ticket_id,sender_id,sender_type,visibility,body,delivery_status,created_at) VALUES(?,?,?,'PUBLIC',?,'PENDING',?)",(ticket_id,sender_id,sender_type,body,now)).lastrowid
    conn.execute('INSERT INTO outbox(message_id,created_at,updated_at) VALUES(?,?,?)',(mid,now,now))
    return mid


def process_one(path=None):
    with db.transaction(path) as conn:
        item=conn.execute("SELECT o.id AS outbox_id,o.message_id,m.ticket_id,t.channel FROM outbox o JOIN messages m ON m.id=o.message_id JOIN tickets t ON t.id=m.ticket_id WHERE o.status='QUEUED' ORDER BY o.id LIMIT 1").fetchone()
        if not item:
            return False
        now=time.time()
        approval=conn.execute('SELECT f.active,f.article_version,a.version,a.status,d.article_version AS queued_version FROM faq_deliveries d JOIN faq_rules f ON f.id=d.rule_id JOIN kb_articles a ON a.id=f.article_id WHERE d.message_id=?',(item['message_id'],)).fetchone()
        if approval:
            ticket=conn.execute('SELECT * FROM tickets WHERE id=?',(item['ticket_id'],)).fetchone()
            messages=conn.execute("SELECT COUNT(*) FROM messages WHERE ticket_id=? AND (sender_type='CUSTOMER' OR sender_type NOT IN ('CUSTOMER','BOT'))",(item['ticket_id'],)).fetchone()[0]
            if not approval['active'] or approval['status']!='PUBLISHED' or approval['article_version']!=approval['version'] or approval['queued_version']!=approval['version'] or ticket['human_requested'] or ticket['status']!='NEW' or ticket['priority'] not in ('NORMAL','LOW') or messages!=1:
                reason='Hủy FAQ vì phê duyệt, nguồn hoặc hội thoại đã thay đổi trước khi gửi.'
                conn.execute("UPDATE outbox SET status='CANCELLED',last_error=?,updated_at=? WHERE id=?",(reason,now,item['outbox_id']))
                conn.execute("UPDATE messages SET delivery_status='CANCELLED' WHERE id=?",(item['message_id'],))
                conn.execute('UPDATE tickets SET version=version+1,updated_at=? WHERE id=?',(now,item['ticket_id']))
                conn.execute('INSERT INTO ticket_events(ticket_id,description,created_at) VALUES(?,?,?)',(item['ticket_id'],reason,now))
                return True
        adapter=PortalAdapter() if item['channel']=='WEB' else SimulatedEmailAdapter()
        conn.execute('UPDATE outbox SET attempts=attempts+1,updated_at=? WHERE id=?',(now,item['outbox_id']))
        try:
            adapter.send(conn,item)
        except Exception:
            conn.execute("UPDATE outbox SET status='FAILED',last_error=?,updated_at=? WHERE id=?",('Kênh mô phỏng chưa gửi thành công. Có thể thử lại.',now,item['outbox_id']))
            conn.execute("UPDATE messages SET delivery_status='FAILED' WHERE id=?",(item['message_id'],))
            description='Gửi tin nhắn thất bại; chưa tính SLA phản hồi'
        else:
            conn.execute("UPDATE outbox SET status='DELIVERED',last_error=NULL,updated_at=? WHERE id=?",(now,item['outbox_id']))
            conn.execute("UPDATE messages SET delivery_status='DELIVERED' WHERE id=?",(item['message_id'],))
            conn.execute('UPDATE ticket_sla SET first_response_at=COALESCE(first_response_at,?) WHERE ticket_id=?',(now,item['ticket_id']))
            description='Đã gửi tin nhắn qua cổng web' if item['channel']=='WEB' else 'Đã gửi email qua adapter mô phỏng'
        conn.execute('UPDATE tickets SET version=version+1,updated_at=? WHERE id=?',(now,item['ticket_id']))
        conn.execute('INSERT INTO ticket_events(ticket_id,description,created_at) VALUES(?,?,?)',(item['ticket_id'],description,now))
        return True
