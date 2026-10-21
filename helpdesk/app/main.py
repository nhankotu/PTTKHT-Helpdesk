from contextlib import asynccontextmanager
from pathlib import Path
import json
import os
import secrets
import sqlite3
import threading
import time
from typing import Literal
from urllib.parse import urlsplit

from fastapi import Depends, FastAPI, HTTPException, Query, Request, Response
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, field_validator

from . import ai, db, delivery, services
from .security import check_password, hash_password, token_hash

Priority = Literal['URGENT','HIGH','NORMAL','LOW']
Status = Literal['NEW','IN_PROGRESS','WAITING_CUSTOMER','RESOLVED','CLOSED']


class Login(BaseModel):
    email: str = Field(max_length=254)
    password: str = Field(min_length=1,max_length=256)


class NewTicket(BaseModel):
    subject: str = Field(min_length=3,max_length=200)
    body: str = Field(min_length=5,max_length=10000)
    priority: Priority = 'NORMAL'
    customer_id: int | None = None

    @field_validator('subject','body')
    @classmethod
    def nonblank_text(cls,value,info):
        value=value.strip()
        if len(value)<(3 if info.field_name=='subject' else 5):
            raise ValueError('Nội dung quá ngắn hoặc chỉ chứa khoảng trắng')
        return value


class EmailEvent(NewTicket):
    external_event_id: str = Field(min_length=1,max_length=200)
    customer_id: int


class Message(BaseModel):
    body: str = Field(min_length=1,max_length=10000)
    visibility: Literal['PUBLIC','INTERNAL'] = 'PUBLIC'
    version: int


class StateUpdate(BaseModel):
    status: Status
    version: int


class Assignment(BaseModel):
    team_id: int
    assignee_id: int | None = None
    category: Literal['ACCOUNT','PAYMENT','TECHNICAL'] | None = None
    version: int


class Article(BaseModel):
    title: str = Field(min_length=3,max_length=200)
    body: str = Field(min_length=20,max_length=20000)
    category: Literal['ACCOUNT','PAYMENT','TECHNICAL']
    version: int | None = None

    @field_validator('title','body')
    @classmethod
    def nonblank_text(cls,value,info):
        value=value.strip()
        if len(value)<(3 if info.field_name=='title' else 20):
            raise ValueError('Nội dung quá ngắn hoặc chỉ chứa khoảng trắng')
        return value


class Rating(BaseModel):
    score: int = Field(ge=1,le=5)
    comment: str = Field(default='',max_length=1000)


class UserCreate(BaseModel):
    name: str = Field(min_length=2,max_length=100)
    email: str = Field(min_length=5,max_length=254,pattern=r'^[^\s@]+@[^\s@]+\.[^\s@]+$')
    password: str = Field(min_length=10,max_length=256)
    role: Literal['CUSTOMER','AGENT','SUPERVISOR','ADMIN']
    team_id: int | None = None

    @field_validator('name')
    @classmethod
    def nonblank_name(cls,value):
        value=value.strip()
        if len(value)<2:
            raise ValueError('Tên cần ít nhất hai ký tự')
        return value


class Availability(BaseModel):
    available: bool


class PolicyUpdate(BaseModel):
    priority: Priority
    response_seconds: int = Field(ge=60,le=2592000)
    resolution_seconds: int = Field(ge=60,le=2592000)


class FaqApproval(BaseModel):
    question: str = Field(min_length=8,max_length=500)
    article_version: int


class FaqSwitch(BaseModel):
    active: bool
    article_version: int


class EmailSimulation(BaseModel):
    failures_remaining: int = Field(ge=0,le=5)


SELECT_TICKETS = """SELECT t.*,u.name AS customer_name,u.email AS customer_email,
 a.name AS assignee_name,tm.name AS team_name,s.response_due,s.resolution_due,s.first_response_at,s.resolved_at
 FROM tickets t JOIN users u ON u.id=t.customer_id LEFT JOIN users a ON a.id=t.assignee_id
 LEFT JOIN teams tm ON tm.id=t.team_id JOIN ticket_sla s ON s.ticket_id=t.id"""


def create_app(database_path=None, run_worker=True):
    @asynccontextmanager
    async def lifespan(app):
        db.initialize(database_path)
        with db.transaction(database_path) as conn:
            conn.execute("UPDATE jobs SET status='QUEUED' WHERE status='RUNNING'")
        stop = threading.Event()
        thread = threading.Thread(target=services.worker,args=(stop,database_path),daemon=True)
        if run_worker:
            thread.start()
        yield
        stop.set()
        if run_worker:
            thread.join(timeout=18)

    app = FastAPI(title='Helpdesk 20261',version='0.1.0',lifespan=lifespan)
    static = Path(__file__).parent.parent/'static'

    @app.middleware('http')
    async def security_headers(request: Request, call_next):
        origin = request.headers.get('origin')
        if request.method in ('POST','PUT','PATCH','DELETE') and origin:
            origin_parts = urlsplit(origin)
            if origin_parts.netloc != request.headers.get('host') or origin_parts.scheme != request.url.scheme:
                return JSONResponse({'detail':'Nguồn yêu cầu không hợp lệ'},status_code=403)
        response = await call_next(request)
        response.headers['X-Content-Type-Options']='nosniff'
        response.headers['Referrer-Policy']='same-origin'
        if not request.url.path.startswith(('/docs','/redoc','/openapi')):
            response.headers['Content-Security-Policy']="default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
        if request.url.path.startswith('/api/'):
            response.headers['Cache-Control']='no-store'
        return response

    def connection():
        with db.transaction(database_path) as conn:
            yield conn

    def current_user(request: Request, conn=Depends(connection)):
        token=request.cookies.get('helpdesk_session','')
        row=conn.execute('SELECT u.* FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>? AND u.active=1',(token_hash(token),time.time())).fetchone()
        if not row:
            raise HTTPException(401,'Vui lòng đăng nhập')
        return dict(row)

    def staff(user=Depends(current_user)):
        if user['role'] not in services.STAFF:
            raise HTTPException(403,'Chỉ dành cho nhân viên')
        return user

    def manager(user=Depends(current_user)):
        if user['role'] not in services.MANAGERS:
            raise HTTPException(403,'Cần quyền trưởng nhóm hoặc quản trị')
        return user

    def admin(user=Depends(current_user)):
        if user['role']!='ADMIN':
            raise HTTPException(403,'Cần quyền quản trị')
        return user

    def safe_user(user):
        return {k:v for k,v in dict(user).items() if k!='password_hash'}

    @app.get('/api/health')
    def health():
        return {'status':'ok','version':'0.1.0'}

    @app.post('/api/auth/login')
    def login(data: Login,response: Response,request: Request,conn=Depends(connection)):
        # In-process throttling for the local demo; production needs a shared limiter.
        now=time.time()
        key=request.client.host if request.client else 'local'
        attempts=app.state.login_attempts.setdefault(key,[])
        attempts[:]=[t for t in attempts if now-t<60]
        if len(attempts)>=15:
            raise HTTPException(429,'Thử lại sau một phút')
        attempts.append(now)
        user=conn.execute('SELECT * FROM users WHERE email=? AND active=1',(data.email.strip().lower(),)).fetchone()
        if not user or not check_password(data.password,user['password_hash']):
            raise HTTPException(401,'Email hoặc mật khẩu không đúng')
        token=secrets.token_urlsafe(32)
        conn.execute('DELETE FROM sessions WHERE expires_at<?',(now,))
        conn.execute('INSERT INTO sessions VALUES(?,?,?)',(token_hash(token),user['id'],now+28800))
        response.set_cookie('helpdesk_session',token,max_age=28800,httponly=True,samesite='strict',secure=os.getenv('HELPDESK_SECURE_COOKIE')=='1')
        return safe_user(user)

    app.state.login_attempts={}

    @app.post('/api/auth/logout')
    def logout(request: Request,response: Response,conn=Depends(connection)):
        conn.execute('DELETE FROM sessions WHERE token_hash=?',(token_hash(request.cookies.get('helpdesk_session','')),))
        response.delete_cookie('helpdesk_session')
        return {'ok':True}

    @app.get('/api/auth/me')
    def me(user=Depends(current_user)):
        return safe_user(user)

    @app.get('/api/meta')
    def metadata(user=Depends(current_user),conn=Depends(connection)):
        return {'teams':[dict(r) for r in conn.execute('SELECT * FROM teams')],
            'categories':[dict(r) for r in conn.execute('SELECT * FROM categories')],
            'staff':[dict(r) for r in conn.execute("SELECT id,name,role,team_id,available FROM users WHERE role!='CUSTOMER' AND active=1")] if user['role'] in services.STAFF else [],
            'customers':[dict(r) for r in conn.execute("SELECT id,name,email FROM users WHERE role='CUSTOMER' AND active=1")] if user['role'] in services.MANAGERS else []}

    def scope(user,alias='t'):
        if user['role']=='CUSTOMER':
            return f'{alias}.customer_id=?',[user['id']]
        if user['role']=='AGENT':
            return f'{alias}.team_id=?',[user['team_id']]
        return '1=1',[]

    @app.get('/api/tickets')
    def tickets(q: str=Query('',max_length=200),status: Status | None=None,priority: Priority | None=None,
                page:int=Query(1,ge=1),user=Depends(current_user),conn=Depends(connection)):
        where,args=scope(user)
        if q:
            where+=' AND (t.subject LIKE ? OR CAST(t.id AS TEXT)=?)'
            args+=['%'+q+'%',q.lstrip('#')]
        if status:
            where+=' AND t.status=?'; args.append(status)
        if priority:
            where+=' AND t.priority=?'; args.append(priority)
        total=conn.execute('SELECT COUNT(*) FROM tickets t WHERE '+where,args).fetchone()[0]
        items=[dict(r) for r in conn.execute(SELECT_TICKETS+' WHERE '+where+' ORDER BY t.updated_at DESC,t.id DESC LIMIT 30 OFFSET ?',args+[(page-1)*30])]
        return {'items':items,'total':total,'page':page,'page_size':30}

    @app.post('/api/tickets',status_code=201)
    def new_ticket(data: NewTicket,user=Depends(current_user),conn=Depends(connection)):
        customer=data.customer_id or user['id']
        if user['role']=='CUSTOMER' and customer!=user['id']:
            raise HTTPException(403,'Không được tạo ticket cho khách khác')
        if user['role']=='AGENT':
            raise HTTPException(403,'Agent tiếp nhận ticket qua hàng đợi')
        tid,_=services.create_ticket(conn,customer,data.subject.strip(),data.body.strip(),data.priority)
        return {'id':tid}

    @app.post('/api/channels/email/events')
    def email_event(data: EmailEvent,user=Depends(manager),conn=Depends(connection)):
        tid,duplicate=services.create_ticket(conn,data.customer_id,data.subject.strip(),data.body.strip(),data.priority,'EMAIL',data.external_event_id)
        return {'id':tid,'duplicate':duplicate,'delivery_mode':'SIMULATED'}

    @app.get('/api/channels/email/simulation')
    def email_simulation(user=Depends(manager),conn=Depends(connection)):
        return dict(conn.execute("SELECT * FROM channel_simulation WHERE channel='EMAIL'").fetchone())

    @app.post('/api/channels/email/simulation')
    def set_email_simulation(data:EmailSimulation,user=Depends(manager),conn=Depends(connection)):
        conn.execute("UPDATE channel_simulation SET failures_remaining=? WHERE channel='EMAIL'",(data.failures_remaining,))
        return {'ok':True,'delivery_mode':'SIMULATED'}

    @app.get('/api/tickets/{ticket_id}')
    def detail(ticket_id:int,user=Depends(current_user),conn=Depends(connection)):
        services.access(conn,ticket_id,user)
        ticket=dict(conn.execute(SELECT_TICKETS+' WHERE t.id=?',(ticket_id,)).fetchone())
        sql='SELECT m.*,u.name AS sender_name FROM messages m LEFT JOIN users u ON u.id=m.sender_id WHERE m.ticket_id=?'
        if user['role']=='CUSTOMER':
            sql+=" AND m.visibility='PUBLIC' AND m.delivery_status='DELIVERED'"
        ticket['messages']=[dict(r) for r in conn.execute(sql+' ORDER BY m.id',(ticket_id,))]
        ticket['rating']=dict(row) if (row:=conn.execute('SELECT * FROM ratings WHERE ticket_id=?',(ticket_id,)).fetchone()) else None
        if user['role'] in services.STAFF:
            ticket['outbox']=[dict(r) for r in conn.execute('SELECT o.* FROM outbox o JOIN messages m ON m.id=o.message_id WHERE m.ticket_id=? ORDER BY o.id',(ticket_id,))]
            ticket['events']=[dict(r) for r in conn.execute('SELECT e.*,u.name AS actor_name FROM ticket_events e LEFT JOIN users u ON u.id=e.actor_id WHERE ticket_id=? ORDER BY e.id',(ticket_id,))]
            job=conn.execute('SELECT * FROM jobs WHERE ticket_id=? ORDER BY id DESC LIMIT 1',(ticket_id,)).fetchone()
            ticket['job']=dict(job) if job else None
            run=conn.execute('SELECT * FROM ai_runs WHERE ticket_id=? ORDER BY id DESC LIMIT 1',(ticket_id,)).fetchone()
            ticket['ai']=dict(run) if run else None
            if run:
                info=ticket['ai']; info['sources']=json.loads(info['sources']); info['stale']=info['ticket_version']!=ticket['version']
                for source in info['sources']:
                    if not conn.execute("SELECT id FROM kb_articles WHERE id=? AND version=? AND status='PUBLISHED'",(source['id'],source['version'])).fetchone():
                        info.update(answer=None,stale=True,fallback_reason='Nguồn KB đã thay đổi hoặc bị thu hồi; chạy lại phân tích.')
                        break
        return ticket

    @app.post('/api/tickets/{ticket_id}/messages',status_code=201)
    def add_message(ticket_id:int,data:Message,user=Depends(current_user),conn=Depends(connection)):
        ticket=services.access(conn,ticket_id,user,write=True)
        services.check_version(ticket,data.version)
        if ticket['status']=='CLOSED':
            raise HTTPException(409,'Ticket đã đóng. Vui lòng tạo yêu cầu mới.')
        if user['role']=='CUSTOMER' and data.visibility!='PUBLIC':
            raise HTTPException(403,'Khách hàng không được ghi chú nội bộ')
        body=data.body.strip()
        if not body:
            raise HTTPException(422,'Nội dung không được để trống')
        now=time.time()
        outbound=user['role']!='CUSTOMER' and data.visibility=='PUBLIC'
        if outbound:
            mid=delivery.enqueue(conn,ticket_id,user['id'],user['role'],body)
        else:
            mid=conn.execute('INSERT INTO messages(ticket_id,sender_id,sender_type,visibility,body,created_at) VALUES(?,?,?,?,?,?)',(ticket_id,user['id'],user['role'],data.visibility,body,now)).lastrowid
        new_status=ticket['status']
        if user['role']=='CUSTOMER' and new_status in ('WAITING_CUSTOMER','RESOLVED'):
            new_status='IN_PROGRESS'
            conn.execute('UPDATE ticket_sla SET resolved_at=NULL WHERE ticket_id=?',(ticket_id,))
        human=ticket['human_requested'] or (user['role']=='CUSTOMER' and 'nhan_vien' in ai.tokens(body))
        conn.execute('UPDATE tickets SET status=?,human_requested=?,version=version+1,updated_at=? WHERE id=?',(new_status,int(human),now,ticket_id))
        services.event(conn,ticket_id,user['id'],'Ghi chú nội bộ' if data.visibility=='INTERNAL' else 'Đưa phản hồi vào hàng đợi gửi' if outbound else 'Khách gửi tin nhắn')
        return {'ok':True,'message_id':mid,'delivery_status':'PENDING' if outbound else 'DELIVERED'}

    @app.post('/api/messages/{message_id}/retry')
    def retry_message(message_id:int,user=Depends(staff),conn=Depends(connection)):
        message=conn.execute('SELECT * FROM messages WHERE id=?',(message_id,)).fetchone()
        if not message:
            raise HTTPException(404,'Không tìm thấy tin nhắn')
        ticket=services.access(conn,message['ticket_id'],user,write=True)
        if ticket['status']=='CLOSED':
            raise HTTPException(409,'Không thể gửi lại sau khi đóng ticket')
        outbox=conn.execute('SELECT * FROM outbox WHERE message_id=?',(message_id,)).fetchone()
        if not outbox or outbox['status']!='FAILED' or message['delivery_status']!='FAILED':
            raise HTTPException(409,'Chỉ thử lại tin nhắn đã gửi thất bại')
        conn.execute("UPDATE outbox SET status='QUEUED',last_error=NULL,updated_at=? WHERE message_id=?",(time.time(),message_id))
        conn.execute("UPDATE messages SET delivery_status='PENDING' WHERE id=?",(message_id,))
        conn.execute('UPDATE tickets SET version=version+1 WHERE id=?',(ticket['id'],))
        services.event(conn,ticket['id'],user['id'],f'Yêu cầu thử gửi lại tin nhắn #{message_id}')
        return {'ok':True,'message_id':message_id}

    @app.patch('/api/tickets/{ticket_id}/status')
    def set_status(ticket_id:int,data:StateUpdate,user=Depends(current_user),conn=Depends(connection)):
        ticket=services.access(conn,ticket_id,user,write=True)
        services.check_version(ticket,data.version)
        if user['role']=='CUSTOMER' and not (ticket['status']=='RESOLVED' and data.status in ('IN_PROGRESS','CLOSED')):
            raise HTTPException(403,'Khách chỉ được xác nhận hoặc mở lại ticket đã giải quyết')
        if data.status not in services.TRANSITIONS[ticket['status']]:
            raise HTTPException(409,'Chuyển trạng thái không hợp lệ')
        if data.status in ('RESOLVED','CLOSED') and conn.execute("SELECT id FROM messages WHERE ticket_id=? AND delivery_status IN ('PENDING','FAILED')",(ticket_id,)).fetchone():
            raise HTTPException(409,'Cần gửi thành công phản hồi đang chờ hoặc bị lỗi trước khi hoàn tất')
        now=time.time()
        conn.execute('UPDATE tickets SET status=?,version=version+1,updated_at=? WHERE id=?',(data.status,now,ticket_id))
        if data.status=='IN_PROGRESS' and user['role']=='AGENT' and ticket['assignee_id'] is None:
            conn.execute('UPDATE tickets SET assignee_id=? WHERE id=?',(user['id'],ticket_id))
        if data.status=='RESOLVED':
            conn.execute('UPDATE ticket_sla SET resolved_at=? WHERE ticket_id=?',(now,ticket_id))
        elif data.status=='IN_PROGRESS' and ticket['status']=='RESOLVED':
            conn.execute('UPDATE ticket_sla SET resolved_at=NULL WHERE ticket_id=?',(ticket_id,))
        services.event(conn,ticket_id,user['id'],f'{ticket["status"]} → {data.status}')
        return {'ok':True}

    @app.patch('/api/tickets/{ticket_id}/assignment')
    def assignment(ticket_id:int,data:Assignment,user=Depends(manager),conn=Depends(connection)):
        ticket=services.access(conn,ticket_id,user)
        services.check_version(ticket,data.version)
        if not conn.execute('SELECT id FROM teams WHERE id=?',(data.team_id,)).fetchone():
            raise HTTPException(422,'Nhóm không hợp lệ')
        if data.assignee_id and not conn.execute("SELECT id FROM users WHERE id=? AND team_id=? AND role='AGENT' AND active=1",(data.assignee_id,data.team_id)).fetchone():
            raise HTTPException(422,'Agent không thuộc nhóm được chọn')
        if data.category and not conn.execute('SELECT code FROM categories WHERE code=? AND team_id=?',(data.category,data.team_id)).fetchone():
            raise HTTPException(422,'Danh mục không thuộc nhóm được chọn')
        conn.execute('UPDATE tickets SET team_id=?,assignee_id=?,category=?,version=version+1,updated_at=? WHERE id=?',(data.team_id,data.assignee_id,data.category,time.time(),ticket_id))
        services.event(conn,ticket_id,user['id'],f'Phân công nhóm {data.team_id}, Agent {data.assignee_id or "chưa gán"}')
        return {'ok':True}

    @app.post('/api/tickets/{ticket_id}/ai-suggestions',status_code=202)
    def ai_suggestion(ticket_id:int,user=Depends(staff),conn=Depends(connection)):
        ticket=services.access(conn,ticket_id,user,write=True)
        if ticket['status'] in ('RESOLVED','CLOSED'):
            raise HTTPException(409,'Ticket đã giải quyết; mở lại trước khi phân tích')
        return {'job_id':services.queue_ai(conn,ticket_id)}

    @app.post('/api/tickets/{ticket_id}/ratings',status_code=201)
    def rate(ticket_id:int,data:Rating,user=Depends(current_user),conn=Depends(connection)):
        ticket=services.access(conn,ticket_id,user)
        if user['role']!='CUSTOMER' or ticket['status'] not in ('RESOLVED','CLOSED'):
            raise HTTPException(403,'Chỉ khách hàng đánh giá ticket đã giải quyết')
        if conn.execute('SELECT ticket_id FROM ratings WHERE ticket_id=?',(ticket_id,)).fetchone():
            raise HTTPException(409,'Ticket đã được đánh giá')
        conn.execute('INSERT INTO ratings VALUES(?,?,?,?,?)',(ticket_id,user['id'],data.score,data.comment,time.time()))
        return {'ok':True}

    @app.get('/api/kb/articles')
    def articles(q:str=Query('',max_length=200),user=Depends(current_user),conn=Depends(connection)):
        sql='SELECT id,title,body,status,category,version,updated_at FROM kb_articles WHERE (title LIKE ? OR body LIKE ?)'
        if user['role']=='CUSTOMER':
            sql+=" AND status='PUBLISHED'"
        return [dict(r) for r in conn.execute(sql+' ORDER BY updated_at DESC',('%'+q+'%','%'+q+'%'))]

    @app.post('/api/kb/articles',status_code=201)
    def new_article(data:Article,user=Depends(staff),conn=Depends(connection)):
        now=time.time()
        aid=conn.execute("INSERT INTO kb_articles(title,body,status,category,author_id,updated_at) VALUES(?,?,'DRAFT',?,?,?)",(data.title.strip(),data.body.strip(),data.category,user['id'],now)).lastrowid
        conn.execute("INSERT INTO kb_versions VALUES(?,1,?,?,'DRAFT',?)",(aid,data.title.strip(),data.body.strip(),now))
        return {'id':aid}

    @app.put('/api/kb/articles/{article_id}')
    def edit_article(article_id:int,data:Article,user=Depends(staff),conn=Depends(connection)):
        old=conn.execute('SELECT * FROM kb_articles WHERE id=?',(article_id,)).fetchone()
        if not old:
            raise HTTPException(404,'Không tìm thấy bài viết')
        if user['role']=='AGENT' and (old['author_id']!=user['id'] or old['status']!='DRAFT'):
            raise HTTPException(403,'Agent chỉ sửa bản nháp do mình tạo')
        if data.version!=old['version']:
            raise HTTPException(409,'Bài viết đã thay đổi')
        now=time.time(); version=old['version']+1
        conn.execute("UPDATE kb_articles SET title=?,body=?,category=?,status='DRAFT',version=?,updated_at=? WHERE id=?",(data.title.strip(),data.body.strip(),data.category,version,now,article_id))
        conn.execute("INSERT INTO kb_versions VALUES(?,?,?,?,'DRAFT',?)",(article_id,version,data.title.strip(),data.body.strip(),now))
        return {'ok':True}

    @app.get('/api/kb/articles/{article_id}/faq-rules')
    def faq_rules(article_id:int,user=Depends(manager),conn=Depends(connection)):
        return [dict(r) for r in conn.execute('SELECT f.*,a.version AS current_version,a.status AS article_status FROM faq_rules f JOIN kb_articles a ON a.id=f.article_id WHERE f.article_id=? ORDER BY f.id',(article_id,))]

    @app.post('/api/kb/articles/{article_id}/faq-rules',status_code=201)
    def approve_faq(article_id:int,data:FaqApproval,user=Depends(manager),conn=Depends(connection)):
        article=conn.execute("SELECT * FROM kb_articles WHERE id=? AND status='PUBLISHED'",(article_id,)).fetchone()
        if not article or article['version']!=data.article_version:
            raise HTTPException(409,'Chỉ duyệt câu hỏi cho đúng phiên bản KB đang xuất bản')
        question=data.question.strip()
        if len(question)<8 or ai.requires_human(question):
            raise HTTPException(422,'FAQ không hợp lệ hoặc cần nhân viên xử lý')
        normalized=ai.normalize_question(question)
        if len(normalized.split())<3:
            raise HTTPException(422,'Câu hỏi cần có nội dung có nghĩa, không chỉ dấu câu')
        if conn.execute('SELECT id FROM faq_rules WHERE normalized_question=?',(normalized,)).fetchone():
            raise HTTPException(409,'Mẫu câu hỏi đã tồn tại; hãy cập nhật phê duyệt hiện có')
        fid=conn.execute('INSERT INTO faq_rules(article_id,article_version,question,normalized_question,approved_by,updated_at) VALUES(?,?,?,?,?,?)',(article_id,data.article_version,question,normalized,user['id'],time.time())).lastrowid
        return {'id':fid}

    @app.patch('/api/faq-rules/{rule_id}')
    def switch_faq(rule_id:int,data:FaqSwitch,user=Depends(manager),conn=Depends(connection)):
        rule=conn.execute('SELECT f.*,a.version AS current_version,a.status AS article_status FROM faq_rules f JOIN kb_articles a ON a.id=f.article_id WHERE f.id=?',(rule_id,)).fetchone()
        if not rule:
            raise HTTPException(404,'Không tìm thấy mẫu FAQ')
        if data.active and (rule['article_status']!='PUBLISHED' or rule['current_version']!=data.article_version):
            raise HTTPException(409,'Cần duyệt lại phiên bản KB đang xuất bản')
        conn.execute('UPDATE faq_rules SET active=?,article_version=?,approved_by=?,updated_at=? WHERE id=?',(int(data.active),data.article_version if data.active else rule['article_version'],user['id'],time.time(),rule_id))
        return {'ok':True}

    @app.post('/api/kb/articles/{article_id}/{action}')
    def publish(article_id:int,action:Literal['publish','retire'],user=Depends(manager),conn=Depends(connection)):
        old=conn.execute('SELECT * FROM kb_articles WHERE id=?',(article_id,)).fetchone()
        if not old:
            raise HTTPException(404,'Không tìm thấy bài viết')
        status='PUBLISHED' if action=='publish' else 'RETIRED'
        version=old['version']+1; now=time.time()
        conn.execute('UPDATE kb_articles SET status=?,version=?,updated_at=? WHERE id=?',(status,version,now,article_id))
        conn.execute('INSERT INTO kb_versions VALUES(?,?,?,?,?,?)',(article_id,version,old['title'],old['body'],status,now))
        return {'ok':True}

    @app.get('/api/reports')
    def reports(days:int=Query(30,ge=1,le=365),user=Depends(manager),conn=Depends(connection)):
        now=time.time()
        rows=[dict(r) for r in conn.execute(SELECT_TICKETS+' WHERE t.created_at>=?',(now-days*86400,))]
        responses=[r['first_response_at']-r['created_at'] for r in rows if r['first_response_at'] is not None]
        resolutions=[r['resolved_at']-r['created_at'] for r in rows if r['resolved_at'] is not None]
        assessed=[]
        for row in rows:
            for actual,due in [(row['first_response_at'],row['response_due']),(row['resolved_at'],row['resolution_due'])]:
                if actual is not None or due<=now:
                    assessed.append(actual is not None and actual<=due)
        ratings=conn.execute('SELECT AVG(r.score),COUNT(*) FROM ratings r JOIN tickets t ON t.id=r.ticket_id WHERE t.created_at>=?',(now-days*86400,)).fetchone()
        counts={s:sum(r['status']==s for r in rows) for s in services.TRANSITIONS}
        return {'total':len(rows),'open':sum(r['status'] not in ('RESOLVED','CLOSED') for r in rows),
            'overdue':sum((r['first_response_at'] is None and now>r['response_due']) or (r['resolved_at'] is None and now>r['resolution_due']) for r in rows),
            'avg_response_seconds':sum(responses)/len(responses) if responses else None,
            'avg_resolution_seconds':sum(resolutions)/len(resolutions) if resolutions else None,
            'sla_compliance':round(100*sum(assessed)/len(assessed),1) if assessed else None,
            'csat':round(ratings[0],1) if ratings[0] is not None else None,'rating_count':ratings[1],
            'statuses':counts,'categories':{c['code']:sum(r['category']==c['code'] for r in rows) for c in conn.execute('SELECT code FROM categories')},
            'alerts':[dict(r) for r in conn.execute('SELECT a.*,t.subject,tm.name AS team_name FROM sla_alerts a JOIN tickets t ON t.id=a.ticket_id LEFT JOIN teams tm ON tm.id=t.team_id WHERE a.created_at>=? ORDER BY a.id DESC LIMIT 30',(now-days*86400,))]}

    @app.get('/api/users')
    def users(user=Depends(admin),conn=Depends(connection)):
        return [safe_user(r) for r in conn.execute('SELECT * FROM users ORDER BY id')]

    @app.post('/api/users',status_code=201)
    def new_user(data:UserCreate,user=Depends(admin),conn=Depends(connection)):
        if data.role=='AGENT' and not data.team_id:
            raise HTTPException(422,'Agent cần thuộc một nhóm')
        if data.team_id and not conn.execute('SELECT id FROM teams WHERE id=?',(data.team_id,)).fetchone():
            raise HTTPException(422,'Nhóm không tồn tại')
        if conn.execute('SELECT id FROM users WHERE email=?',(data.email.lower(),)).fetchone():
            raise HTTPException(409,'Email đã tồn tại')
        uid=conn.execute('INSERT INTO users(name,email,password_hash,role,team_id) VALUES(?,?,?,?,?)',(data.name.strip(),data.email.lower(),hash_password(data.password),data.role,data.team_id)).lastrowid
        return {'id':uid}

    @app.patch('/api/users/{user_id}/availability')
    def availability(user_id:int,data:Availability,user=Depends(staff),conn=Depends(connection)):
        if user['role'] not in services.MANAGERS and user_id!=user['id']:
            raise HTTPException(403,'Không được thay đổi nhân viên khác')
        if not conn.execute("SELECT id FROM users WHERE id=? AND role='AGENT'",(user_id,)).fetchone():
            raise HTTPException(404,'Không tìm thấy Agent')
        conn.execute('UPDATE users SET available=? WHERE id=?',(int(data.available),user_id))
        return {'ok':True}

    @app.get('/api/sla/policies')
    def policies(user=Depends(manager),conn=Depends(connection)):
        return [dict(r) for r in conn.execute('SELECT p.* FROM sla_policies p WHERE version=(SELECT MAX(version) FROM sla_policies WHERE priority=p.priority)')]

    @app.post('/api/sla/policies',status_code=201)
    def new_policy(data:PolicyUpdate,user=Depends(admin),conn=Depends(connection)):
        if data.resolution_seconds<data.response_seconds:
            raise HTTPException(422,'Hạn giải quyết phải sau hạn phản hồi')
        version=conn.execute('SELECT MAX(version)+1 FROM sla_policies WHERE priority=?',(data.priority,)).fetchone()[0] or 1
        conn.execute('INSERT INTO sla_policies(version,priority,response_seconds,resolution_seconds) VALUES(?,?,?,?)',(version,data.priority,data.response_seconds,data.resolution_seconds))
        return {'version':version}

    @app.get('/')
    def index():
        return FileResponse(static/'index.html')

    app.mount('/static',StaticFiles(directory=str(static)),name='static')
    return app


app=create_app()
