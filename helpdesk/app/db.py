"""SQLite persistence and additive, versioned schema setup."""
from contextlib import contextmanager
from pathlib import Path
import os
import sqlite3
import time

from .security import hash_password

DEFAULT_PATH = Path(__file__).resolve().parent.parent / "data" / "helpdesk.sqlite3"


def connect(path=None):
    db_path = Path(path or os.getenv("HELPDESK_DB", str(DEFAULT_PATH)))
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(db_path), timeout=15)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys=ON")
    conn.execute("PRAGMA busy_timeout=15000")
    return conn


@contextmanager
def transaction(path=None):
    conn = connect(path)
    try:
        conn.execute("BEGIN IMMEDIATE")
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


SCHEMA = """
CREATE TABLE IF NOT EXISTS schema_versions(version INTEGER PRIMARY KEY, applied_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS teams(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE);
CREATE TABLE IF NOT EXISTS users(
 id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE COLLATE NOCASE,
 password_hash TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('CUSTOMER','AGENT','SUPERVISOR','ADMIN')),
 team_id INTEGER REFERENCES teams(id), active INTEGER NOT NULL DEFAULT 1, available INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id), expires_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS categories(code TEXT PRIMARY KEY, name TEXT NOT NULL, team_id INTEGER NOT NULL REFERENCES teams(id));
CREATE TABLE IF NOT EXISTS sla_policies(id INTEGER PRIMARY KEY, version INTEGER NOT NULL, priority TEXT NOT NULL,
 response_seconds INTEGER NOT NULL, resolution_seconds INTEGER NOT NULL, UNIQUE(version,priority));
CREATE TABLE IF NOT EXISTS tickets(
 id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER NOT NULL REFERENCES users(id),
 subject TEXT NOT NULL, channel TEXT NOT NULL CHECK(channel IN ('WEB','EMAIL')),
 category TEXT REFERENCES categories(code), team_id INTEGER REFERENCES teams(id), assignee_id INTEGER REFERENCES users(id),
 status TEXT NOT NULL DEFAULT 'NEW' CHECK(status IN ('NEW','IN_PROGRESS','WAITING_CUSTOMER','RESOLVED','CLOSED')),
 priority TEXT NOT NULL CHECK(priority IN ('URGENT','HIGH','NORMAL','LOW')),
 created_at REAL NOT NULL, updated_at REAL NOT NULL, version INTEGER NOT NULL DEFAULT 1,
 human_requested INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS messages(
 id INTEGER PRIMARY KEY AUTOINCREMENT, ticket_id INTEGER NOT NULL REFERENCES tickets(id), sender_id INTEGER REFERENCES users(id),
 sender_type TEXT NOT NULL, visibility TEXT NOT NULL CHECK(visibility IN ('PUBLIC','INTERNAL')),
 body TEXT NOT NULL, delivery_status TEXT NOT NULL DEFAULT 'DELIVERED', created_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS ticket_events(id INTEGER PRIMARY KEY, ticket_id INTEGER NOT NULL REFERENCES tickets(id),
 actor_id INTEGER REFERENCES users(id), description TEXT NOT NULL, created_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS channel_events(id INTEGER PRIMARY KEY, channel TEXT NOT NULL, external_event_id TEXT NOT NULL,
 ticket_id INTEGER NOT NULL REFERENCES tickets(id), UNIQUE(channel,external_event_id));
CREATE TABLE IF NOT EXISTS ticket_sla(ticket_id INTEGER PRIMARY KEY REFERENCES tickets(id),
 policy_id INTEGER NOT NULL REFERENCES sla_policies(id), response_due REAL NOT NULL, resolution_due REAL NOT NULL,
 first_response_at REAL, resolved_at REAL);
CREATE TABLE IF NOT EXISTS sla_alerts(id INTEGER PRIMARY KEY, ticket_id INTEGER NOT NULL REFERENCES tickets(id),
 deadline_type TEXT NOT NULL, level TEXT NOT NULL, created_at REAL NOT NULL, UNIQUE(ticket_id,deadline_type,level));
CREATE TABLE IF NOT EXISTS kb_articles(id INTEGER PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL,
 status TEXT NOT NULL CHECK(status IN ('DRAFT','PUBLISHED','RETIRED')), category TEXT NOT NULL REFERENCES categories(code),
 version INTEGER NOT NULL DEFAULT 1, author_id INTEGER NOT NULL REFERENCES users(id), updated_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS kb_versions(article_id INTEGER NOT NULL REFERENCES kb_articles(id), version INTEGER NOT NULL,
 title TEXT NOT NULL, body TEXT NOT NULL, status TEXT NOT NULL, created_at REAL NOT NULL, PRIMARY KEY(article_id,version));
CREATE TABLE IF NOT EXISTS jobs(id INTEGER PRIMARY KEY, ticket_id INTEGER NOT NULL REFERENCES tickets(id),
 status TEXT NOT NULL DEFAULT 'QUEUED', created_at REAL NOT NULL, updated_at REAL NOT NULL, error TEXT);
CREATE UNIQUE INDEX IF NOT EXISTS one_active_job ON jobs(ticket_id) WHERE status IN ('QUEUED','RUNNING');
CREATE TABLE IF NOT EXISTS ai_runs(id INTEGER PRIMARY KEY, ticket_id INTEGER NOT NULL REFERENCES tickets(id),
 category TEXT, confidence REAL NOT NULL, answer TEXT, sources TEXT NOT NULL, mode TEXT NOT NULL,
 fallback_reason TEXT, latency_ms REAL NOT NULL, created_at REAL NOT NULL, ticket_version INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS ratings(ticket_id INTEGER PRIMARY KEY REFERENCES tickets(id),
 customer_id INTEGER NOT NULL REFERENCES users(id), score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 5), comment TEXT NOT NULL, created_at REAL NOT NULL);
CREATE INDEX IF NOT EXISTS ticket_queue ON tickets(team_id,status,updated_at);
CREATE INDEX IF NOT EXISTS ticket_customer ON tickets(customer_id,updated_at);
CREATE INDEX IF NOT EXISTS messages_ticket ON messages(ticket_id,created_at);
"""

MIGRATION_2 = """
CREATE TABLE IF NOT EXISTS faq_rules(
 id INTEGER PRIMARY KEY, article_id INTEGER NOT NULL REFERENCES kb_articles(id),
 article_version INTEGER NOT NULL, question TEXT NOT NULL, normalized_question TEXT NOT NULL UNIQUE,
 active INTEGER NOT NULL DEFAULT 1, approved_by INTEGER NOT NULL REFERENCES users(id), updated_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS faq_deliveries(
 ticket_id INTEGER PRIMARY KEY REFERENCES tickets(id), rule_id INTEGER NOT NULL REFERENCES faq_rules(id),
 message_id INTEGER NOT NULL UNIQUE REFERENCES messages(id), created_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS outbox(
 id INTEGER PRIMARY KEY, message_id INTEGER NOT NULL UNIQUE REFERENCES messages(id),
 status TEXT NOT NULL DEFAULT 'QUEUED' CHECK(status IN ('QUEUED','DELIVERED','FAILED','CANCELLED')),
 attempts INTEGER NOT NULL DEFAULT 0, last_error TEXT, created_at REAL NOT NULL, updated_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS channel_simulation(channel TEXT PRIMARY KEY, failures_remaining INTEGER NOT NULL DEFAULT 0);
INSERT OR IGNORE INTO channel_simulation VALUES('EMAIL',0);
"""


def initialize(path=None):
    conn = connect(path)
    try:
        conn.execute("PRAGMA journal_mode=WAL")
        conn.executescript(SCHEMA)
        conn.execute("INSERT OR IGNORE INTO schema_versions VALUES(1,?)", (time.time(),))
        conn.commit()
        # Additive migration: preserve all v1 tickets, sessions and KB versions.
        conn.executescript('BEGIN IMMEDIATE;'+MIGRATION_2)
        conn.execute("INSERT OR IGNORE INTO schema_versions VALUES(2,?)", (time.time(),))
        conn.commit()
        conn.execute('BEGIN IMMEDIATE')
        if not conn.execute('SELECT version FROM schema_versions WHERE version=3').fetchone():
            # Freeze the version approved when an answer was enqueued. Legacy
            # in-flight answers default to 0 and will be cancelled, never sent.
            conn.execute('ALTER TABLE faq_deliveries ADD COLUMN article_version INTEGER NOT NULL DEFAULT 0')
            conn.execute('INSERT INTO schema_versions VALUES(3,?)',(time.time(),))
        conn.commit()
    finally:
        conn.close()
    with transaction(path) as conn:
        if conn.execute("SELECT COUNT(*) FROM users").fetchone()[0]:
            return
        conn.executemany("INSERT INTO teams VALUES(?,?)", [(1, "Kỹ thuật"), (2, "Thanh toán"), (3, "Tài khoản")])
        password = hash_password(os.getenv("HELPDESK_DEMO_PASSWORD", "Demo@20261"))
        users = [
            (1,"Quản trị viên","admin@helpdesk.local","ADMIN",None),
            (2,"Trưởng nhóm hỗ trợ","supervisor@helpdesk.local","SUPERVISOR",None),
            (3,"Minh Anh","agent@helpdesk.local","AGENT",1),
            (4,"Thu Hà","billing@helpdesk.local","AGENT",2),
            (5,"Đức Huy","account@helpdesk.local","AGENT",3),
            (6,"Nguyễn Linh","customer@helpdesk.local","CUSTOMER",None),
            (7,"Trần Nam","customer2@helpdesk.local","CUSTOMER",None),
        ]
        conn.executemany("INSERT INTO users(id,name,email,role,team_id,password_hash) VALUES(?,?,?,?,?,?)", [u+(password,) for u in users])
        conn.executemany("INSERT INTO categories VALUES(?,?,?)", [
            ("TECHNICAL","Lỗi kỹ thuật",1),("PAYMENT","Thanh toán",2),("ACCOUNT","Tài khoản",3)])
        for priority, response, resolution in [("URGENT",900,14400),("HIGH",3600,28800),("NORMAL",14400,86400),("LOW",28800,172800)]:
            conn.execute("INSERT INTO sla_policies(version,priority,response_seconds,resolution_seconds) VALUES(1,?,?,?)", (priority,response,resolution))
        now = time.time()
        articles = [
            ("Đặt lại mật khẩu tài khoản", "Để đặt lại mật khẩu, mở trang đăng nhập và chọn Quên mật khẩu. Nhập email đã đăng ký, sau đó mở liên kết xác nhận trong hộp thư. Tạo mật khẩu mới và đăng nhập lại. Nếu không nhận được email sau 5 phút, kiểm tra thư rác hoặc liên hệ nhân viên hỗ trợ.","ACCOUNT"),
            ("Kiểm tra thanh toán và hóa đơn", "Để kiểm tra thanh toán, vào mục Thanh toán trong tài khoản và mở Lịch sử giao dịch. Hóa đơn được gửi đến email đã đăng ký sau khi giao dịch thành công. Nếu đã trừ tiền nhưng chưa kích hoạt dịch vụ, gửi mã giao dịch cho nhân viên; không gửi số thẻ hoặc mật khẩu ngân hàng.","PAYMENT"),
            ("Khắc phục lỗi kết nối và tải trang", "Khi trang không tải hoặc báo lỗi kết nối, kiểm tra kết nối internet, tải lại trang và thử cửa sổ riêng tư. Xóa bộ nhớ đệm của trình duyệt nếu lỗi vẫn xảy ra. Nếu màn hình báo lỗi máy chủ hoặc lỗi 500, gửi mã lỗi và thời điểm xảy ra cho nhân viên kỹ thuật.","TECHNICAL"),
        ]
        for title,body,category in articles:
            aid = conn.execute("INSERT INTO kb_articles(title,body,status,category,author_id,updated_at) VALUES(?,?,'PUBLISHED',?,1,?)", (title,body,category,now)).lastrowid
            conn.execute("INSERT INTO kb_versions VALUES(?,1,?,?,'PUBLISHED',?)", (aid,title,body,now))
        for customer,subject,body,category,team,assignee,priority,age,channel in [
            (6,"Không đăng nhập được sau khi đổi mật khẩu","Tôi quên mật khẩu tài khoản, làm sao để đặt lại?","ACCOUNT",3,5,"NORMAL",1800,"WEB"),
            (7,"Đã thanh toán nhưng dịch vụ chưa kích hoạt","Tôi đã chuyển khoản nhưng chưa nhận hóa đơn.","PAYMENT",2,4,"HIGH",4200,"EMAIL"),
            (6,"Trang báo lỗi 500 khi mở báo cáo","Máy chủ báo lỗi 500 và trang không tải.","TECHNICAL",1,3,"HIGH",900,"WEB"),
        ]:
            created = now-age
            tid = conn.execute("INSERT INTO tickets(customer_id,subject,channel,category,team_id,assignee_id,priority,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)", (customer,subject,channel,category,team,assignee,priority,created,created)).lastrowid
            conn.execute("INSERT INTO messages(ticket_id,sender_id,sender_type,visibility,body,created_at) VALUES(?,?,'CUSTOMER','PUBLIC',?,?)", (tid,customer,body,created))
            policy = conn.execute("SELECT * FROM sla_policies WHERE priority=?", (priority,)).fetchone()
            conn.execute("INSERT INTO ticket_sla(ticket_id,policy_id,response_due,resolution_due) VALUES(?,?,?,?)", (tid,policy['id'],created+policy['response_seconds'],created+policy['resolution_seconds']))
            conn.execute("INSERT INTO ticket_events(ticket_id,description,created_at) VALUES(?,?,?)", (tid,"Tạo ticket dữ liệu demo",created))
