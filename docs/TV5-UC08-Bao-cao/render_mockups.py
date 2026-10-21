"""Render UC08 design mockups from synthetic data; never connects to the app DB.

Run: python docs/TV5-UC08-Bao-cao/render_mockups.py
Requires Pillow. All displayed KPI values are derived from sample-report.json.
"""
from collections import Counter
from datetime import datetime, timedelta
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
SCALE = 2
W, H = 1366, 768
NAVY, MUTED, BLUE = '#172b44', '#53647b', '#2463d3'
BG, LINE = '#f3f6fb', '#dce4ef'
REG = Path('C:/Windows/Fonts/segoeui.ttf')
BOLD = Path('C:/Windows/Fonts/segoeuib.ttf')


def dt(value):
    return datetime.fromisoformat(value) if value else None


def summarize(data):
    now = dt(data['as_of'])
    begin = now - timedelta(days=data['days'])
    rows = [r for r in data['tickets'] if begin <= dt(r['created_at']) <= now]
    samples, resolutions, assessed = [], [], []
    overdue = 0
    for row in rows:
        created = dt(row['created_at'])
        response, resolved = dt(row['first_response_at']), dt(row['resolved_at'])
        rd, sd = dt(row['response_due']), dt(row['resolution_due'])
        if response:
            samples.append((response - created).total_seconds())
        if resolved:
            resolutions.append((resolved - created).total_seconds())
        for actual, due in [(response, rd), (resolved, sd)]:
            if actual is not None or due <= now:
                assessed.append(actual is not None and actual <= due)
        overdue += int((response is None and now > rd) or (resolved is None and now > sd))
    scores = [r['score'] for r in rows if r['score'] is not None]
    avg = lambda seq: sum(seq) / len(seq) if seq else None
    alerts = sorted((a for a in data['alerts'] if begin <= dt(a['created_at']) <= now),
                    key=lambda a: a['created_at'], reverse=True)[:30]
    return dict(total=len(rows), open=sum(r['status'] not in ('RESOLVED', 'CLOSED') for r in rows),
                overdue=overdue, avg_response_seconds=avg(samples), avg_resolution_seconds=avg(resolutions),
                response_sample_count=len(samples), resolution_sample_count=len(resolutions),
                sla_compliance=100 * sum(assessed) / len(assessed) if assessed else None,
                assessed_count=len(assessed), met_count=sum(assessed), csat=avg(scores), rating_count=len(scores),
                statuses=dict(Counter(r['status'] for r in rows)), categories=dict(Counter(r['category'] for r in rows)),
                alerts=alerts)


def font(size, bold=False):
    return ImageFont.truetype(str(BOLD if bold else REG), size * SCALE)


def canvas():
    im = Image.new('RGB', (W*SCALE, H*SCALE), BG)
    return im, ImageDraw.Draw(im)


def box(d, x, y, w, h, fill='white', radius=12, outline=LINE):
    d.rounded_rectangle(tuple(int(v*SCALE) for v in (x,y,x+w,y+h)), radius=radius*SCALE,
                        fill=fill, outline=outline, width=SCALE)


def text(d, x, y, label, size=24, color=NAVY, bold=False):
    d.text((x*SCALE,y*SCALE), str(label), font=font(size,bold), fill=color)


def base(d, sla=False):
    text(d,40,18,'ĐỒ ÁN HELPDESK  /  TV5 · UC08',16,BLUE,True)
    text(d,40,44,'Báo cáo ticket, SLA và CSAT',32,bold=True)
    text(d,1000,24,'MÔ PHỎNG THIẾT KẾ',18,MUTED,True)
    box(d,40,106,210,46,BLUE if not sla else 'white')
    text(d,62,113,'Tổng quan',24,'white' if not sla else NAVY,True)
    box(d,266,106,210,46,BLUE if sla else 'white')
    text(d,286,113,'Chi tiết SLA',24,'white' if sla else NAVY,True)
    box(d,1056,106,270,46)
    text(d,1074,113,'7 ngày gần nhất',24)
    d.polygon([(1294*SCALE,125*SCALE),(1306*SCALE,125*SCALE),(1300*SCALE,133*SCALE)],fill=MUTED)
    text(d,40,165,'Ticket/SLA/CSAT: theo ngày tạo ticket · Ảnh chụp số liệu 21/10/2026 12:00 (UTC+7)',18,MUTED)


def kpis(d, r):
    val = lambda value, suffix='': '—' if value is None else f'{value:g}{suffix}'
    cards = [('Tổng ticket', str(r['total']), 'Tạo trong kỳ đã chọn'),
             ('Ticket đang mở', str(r['open']), 'Chưa giải quyết / đóng'),
             ('Tuân thủ SLA', val(r['sla_compliance'],'%'), f"{r['met_count']}/{r['assessed_count']} mốc đúng hạn"),
             ('CSAT trung bình', val(r['csat'],' / 5'), f"{r['rating_count']} lượt đánh giá")]
    for i,(title,value,note) in enumerate(cards):
        x=40+i*326
        box(d,x,202,308,120)
        text(d,x+18,214,title,22,MUTED)
        text(d,x+18,242,value,32,BLUE,True)
        text(d,x+18,287,note,18,MUTED)
    box(d,40,338,1286,62,fill='#eaf0ff')
    duration=lambda sec: '—' if sec is None else f'{sec/3600:g} giờ'
    text(d,58,353,'Phản hồi TB: '+duration(r['avg_response_seconds']),22,bold=True)
    text(d,466,353,'Giải quyết TB: '+duration(r['avg_resolution_seconds']),22,bold=True)
    text(d,956,353,f"Đang quá hạn: {r['overdue']}",22,bold=True)


def distributions(d, r):
    box(d,40,418,630,276)
    box(d,688,418,638,276)
    text(d,58,432,'Phân bố trạng thái',24,bold=True)
    text(d,708,432,'Phân bố danh mục',24,bold=True)
    states=[('NEW','Mới'),('IN_PROGRESS','Đang xử lý'),('WAITING_CUSTOMER','Chờ khách hàng'),
            ('RESOLVED','Đã giải quyết'),('CLOSED','Đã đóng')]
    if not r['total']:
        text(d,58,500,'Chưa có ticket trong kỳ.',24,MUTED)
        text(d,708,500,'Chưa có dữ liệu phân loại.',24,MUTED)
        text(d,58,550,'Đổi kỳ để xem dữ liệu khác.',20,MUTED)
        return
    for i,(code,label) in enumerate(states):
        y=476+i*39
        count=r['statuses'].get(code,0)
        text(d,58,y,label,22)
        box(d,294,y+8,290,13,fill='#e8eef8',radius=6,outline=None)
        if count: box(d,294,y+8,290*count/r['total'],13,fill=BLUE,radius=6,outline=None)
        text(d,612,y,str(count),22,bold=True)
    for i,(code,label) in enumerate([('ACCOUNT','Tài khoản'),('PAYMENT','Thanh toán'),('TECHNICAL','Kỹ thuật')]):
        y=480+i*55
        count=r['categories'].get(code,0)
        text(d,708,y,label,22)
        box(d,912,y+9,304,13,fill='#e8eef8',radius=6,outline=None)
        if count: box(d,912,y+9,304*count/r['total'],13,fill='#42958b',radius=6,outline=None)
        text(d,1250,y,str(count),22,bold=True)
    text(d,708,654,'Số mẫu thời gian: phản hồi 5 · giải quyết 2',18,MUTED)


def sla_table(d,r):
    box(d,40,418,1286,276)
    text(d,58,430,'Lịch sử cảnh báo SLA',24,bold=True)
    text(d,58,467,'Theo ngày phát sinh cảnh báo · tối đa 30 bản ghi · không phải danh sách đang quá hạn',18,MUTED)
    for x,label in [(58,'Ticket'),(258,'Loại hạn'),(508,'Mức cảnh báo'),(852,'Thời điểm (UTC+7)')]:
        text(d,x,508,label,21,MUTED,True)
    for i,a in enumerate(r['alerts']):
        y=549+i*43
        text(d,58,y,f"#{a['ticket_id']}  →",22,BLUE,True)
        text(d,258,y,'Giải quyết' if a['deadline_type']=='RESOLUTION' else 'Phản hồi',22)
        text(d,508,y,'Đã quá hạn' if a['level']=='OVERDUE' else 'Gần đến hạn',22,
             '#ad4d30' if a['level']=='OVERDUE' else '#8e670f')
        text(d,852,y,dt(a['created_at']).strftime('%d/%m/%Y %H:%M'),22)


def save(im,name):
    im.resize((W,H),Image.Resampling.LANCZOS).save(ROOT/(name+'.png'))


def render():
    data=json.loads((ROOT/'sample-report.json').read_text(encoding='utf-8'))
    r=summarize(data)
    (ROOT/'sample-metrics.json').write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for sla in [False,True]:
        im,d=canvas(); base(d,sla); kpis(d,r)
        if sla: sla_table(d,r)
        else: distributions(d,r)
        text(d,40,722,'Dữ liệu tổng hợp minh họa từ 6 ticket · Không phải số liệu vận hành thực tế',18,MUTED)
        save(im,'ui-r02-sla' if sla else 'ui-r01-tong-quan')
    empty=summarize(dict(data,tickets=[],alerts=[]))
    im,d=canvas(); base(d); kpis(d,empty); distributions(d,empty)
    text(d,40,722,'Trạng thái rỗng · Thiếu mẫu không được hiển thị thành 0% SLA hoặc 0 điểm CSAT',18,MUTED)
    save(im,'ui-r01-rong')
    for mode in ['loi','dang-tai','tu-choi']:
        im,d=canvas(); base(d)
        box(d,190,252,986,310)
        if mode=='loi':
            text(d,232,288,'Không tải được báo cáo',30,'#9c3e32',True)
            text(d,232,346,'Chưa có kết quả đầy đủ. Vui lòng thử lại.',24)
            text(d,232,390,'Khoảng thời gian đã chọn được giữ nguyên.',22,MUTED)
            box(d,232,454,180,52,BLUE)
            text(d,264,463,'Thử lại',24,'white',True)
        elif mode=='dang-tai':
            text(d,232,286,'Đang tổng hợp báo cáo…',30,bold=True)
            text(d,232,344,'Vui lòng chờ; chỉ số sẽ xuất hiện khi tải xong.',24,MUTED)
            for i in range(3): box(d,232+i*286,426,260,70,fill='#e9eef7')
        else:
            text(d,232,286,'Bạn không có quyền xem báo cáo',30,bold=True)
            text(d,232,346,'Chức năng dành cho Supervisor hoặc Admin.',24,MUTED)
            box(d,232,444,340,52,BLUE)
            text(d,254,453,'Về trang được phép',24,'white',True)
        text(d,40,722,'Mô phỏng trạng thái giao diện · Bộ lọc không làm thay đổi dữ liệu ticket',18,MUTED)
        save(im,'ui-r01-'+mode)
    print(json.dumps({k:v for k,v in r.items() if k not in ('alerts','statuses','categories')},ensure_ascii=True))


if __name__=='__main__':
    render()
