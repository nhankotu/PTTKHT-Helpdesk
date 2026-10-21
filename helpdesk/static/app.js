'use strict';
const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const statuses = {NEW:'Mới tiếp nhận',IN_PROGRESS:'Đang xử lý',WAITING_CUSTOMER:'Chờ khách hàng',RESOLVED:'Đã giải quyết',CLOSED:'Đã đóng'};
const priorities = {URGENT:'Khẩn cấp',HIGH:'Cao',NORMAL:'Bình thường',LOW:'Thấp'};
const roles = {ADMIN:'Quản trị viên',SUPERVISOR:'Trưởng nhóm',AGENT:'Nhân viên hỗ trợ',CUSTOMER:'Khách hàng'};
const transitions = {NEW:['IN_PROGRESS'],IN_PROGRESS:['WAITING_CUSTOMER','RESOLVED'],WAITING_CUSTOMER:['IN_PROGRESS','RESOLVED'],RESOLVED:['IN_PROGRESS','CLOSED'],CLOSED:[]};
const state = {user:null,meta:null,view:'tickets',page:1,q:'',status:'',priority:'',ticket:null,timer:null,articles:[],days:30};
const manager = () => ['ADMIN','SUPERVISOR'].includes(state.user?.role);
const staff = () => state.user?.role !== 'CUSTOMER';
const date = t => t ? new Date(t*1000).toLocaleString('vi-VN',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'}) : '—';
const duration = seconds => seconds == null ? '—' : seconds < 3600 ? `${Math.round(seconds/60)} phút` : `${(seconds/3600).toFixed(1)} giờ`;
const badge = s => `<span class="badge ${esc(s)}">${esc(statuses[s]||s)}</span>`;
const options = (obj,value,empty) => (empty != null ? `<option value="">${empty}</option>` : '')+Object.entries(obj).map(([k,v])=>`<option value="${esc(k)}" ${String(value)===k?'selected':''}>${esc(v)}</option>`).join('');
const iconPaths = {grid:'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',inbox:'M4 4h16v16H4z M4 13h5l2 3h2l2-3h5',book:'M4 3h13a3 3 0 0 1 3 3v15H7a3 3 0 0 1-3-3z M4 17h16 M8 7h8 M8 11h6',users:'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M22 21v-2a4 4 0 0 0-3-3.9 M16 3a4 4 0 0 1 0 8',clock:'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18 M12 7v6l4 2',plus:'M12 5v14 M5 12h14',logout:'M9 4H4v16h5 M13 8l4 4-4 4 M8 12h13',arrow:'M19 12H5 M11 6l-6 6 6 6',spark:'M12 2l3 7 7 3-7 3-3 7-3-7-7-3 7-3z',check:'M4 12l5 5L20 6'};
const icon = name => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="${iconPaths[name]||iconPaths.inbox}"/></svg>`;
function toast(message,error=false){const el=$('#toast');el.textContent=message;el.className='show'+(error?' error':'');clearTimeout(toast.timer);toast.timer=setTimeout(()=>el.className='',5000);}
async function api(path,{method='GET',body}={}){
  const res=await fetch('/api'+path,{method,credentials:'same-origin',headers:body?{'Content-Type':'application/json'}:{},body:body?JSON.stringify(body):undefined});
  let data;try{data=await res.json();}catch{data={detail:'Không đọc được phản hồi máy chủ'};}
  if(!res.ok){
    if(res.status===401 && !path.includes('/auth/')){state.user=null;renderLogin();}
    const detail=data.detail;
    throw new Error(typeof detail==='string'?detail:Array.isArray(detail)?'Dữ liệu chưa hợp lệ. Kiểm tra các trường nhập.':detail?.message||'Yêu cầu thất bại');
  }
  return data;
}
function renderLogin(){
  clearTimeout(state.timer);
  $('#app').innerHTML=`<main class="login-screen"><section class="login-story"><div class="brand"><div class="brand-mark">d.</div><span>deskflow<small>CARE, CONNECTED.</small></span></div><div><span class="tag">Không gian hỗ trợ khách hàng</span><h1>Mọi yêu cầu.<br>Một nơi kết nối.</h1><p>Tiếp nhận, phối hợp và giải quyết yêu cầu với một quy trình rõ ràng, từ lời nhắn đầu tiên đến khi khách hàng hài lòng.</p></div><div class="story-footer small muted">Đồ án TK&XD PM · Học kỳ 20261</div></section><section class="login-form-wrap"><form id="login-form" class="login-form"><div class="eyebrow">CHÀO MỪNG TRỞ LẠI</div><h2>Đăng nhập Deskflow</h2><p class="intro">Mở không gian làm việc của bạn.</p><label>Email<input name="email" type="email" required autocomplete="username" placeholder="ban@congty.vn"></label><label>Mật khẩu<input name="password" type="password" required autocomplete="current-password" placeholder="Nhập mật khẩu"></label><div class="form-error" id="login-error" role="alert"></div><button class="btn" type="submit">Đăng nhập ${icon('arrow')}</button><div class="demo-box"><span class="small muted">Dữ liệu mẫu để khám phá ứng dụng</span><div class="actions"><button type="button" class="btn secondary small" data-action="demo" data-role="supervisor">Trưởng nhóm</button><button type="button" class="btn secondary small" data-action="demo" data-role="agent">Agent</button><button type="button" class="btn secondary small" data-action="demo" data-role="customer">Khách hàng</button><button type="button" class="btn secondary small" data-action="demo" data-role="admin">Admin</button></div><p class="small muted">Các tài khoản mẫu dùng mật khẩu <b>Demo@20261</b> khi khởi tạo mặc định.</p></div></form></section></main>`;
}
function shell(){
  const nav=[...(manager()?[['overview','grid','Tổng quan']]:[]),['tickets','inbox',staff()?'Hộp thư hỗ trợ':'Yêu cầu của tôi'],['kb','book','Kho tri thức'],...(manager()?[['sla','clock','Theo dõi SLA']]:[]),...(state.user.role==='ADMIN'?[['users','users','Thành viên']]:[])];
  $('#app').innerHTML=`<div class="shell"><aside class="sidebar"><div class="brand"><div class="brand-mark">d.</div><span>deskflow<small>CARE, CONNECTED.</small></span></div><div class="nav-label">Không gian làm việc</div><nav class="nav" aria-label="Điều hướng chính">${nav.map(([id,i,label])=>`<button data-action="nav" data-view="${id}" class="${state.view===id?'active':''}">${icon(i)}<span>${label}</span></button>`).join('')}</nav><div class="sidebar-bottom"><div class="workspace-note"><strong>Hỗ trợ có căn cứ</strong>Gợi ý từ kho tri thức.<br>Nhân viên luôn có thể tiếp quản.</div><div class="userbox"><div class="avatar">${esc(state.user.name.slice(0,1))}</div><div><div class="name">${esc(state.user.name)}</div><div class="role">${roles[state.user.role]}</div></div><button class="logout" data-action="logout" title="Đăng xuất" aria-label="Đăng xuất">${icon('logout')}</button></div></div></aside><div class="main"><header class="topbar"><div class="breadcrumb">Không gian làm việc &nbsp;/&nbsp; <b>${nav.find(n=>n[0]===state.view)?.[2]||'Ticket'}</b></div><div class="topbar-right"><span class="online"><i class="dot"></i>Ứng dụng cục bộ</span><span class="today">${new Date().toLocaleDateString('vi-VN',{day:'numeric',month:'long'})}</span></div></header><main class="content" id="content"><div class="loading">Đang tải dữ liệu…</div></main></div></div>`;
}
async function navigate(view,reset=true){
  clearTimeout(state.timer);state.ticket=null;state.view=view;
  if(reset){state.page=1;state.q='';state.status='';state.priority='';}
  shell();
  try{if(view==='tickets')await loadTickets();else if(view==='overview'||view==='sla')await loadOverview();else if(view==='kb')await loadKb();else if(view==='users')await loadUsers();}
  catch(e){$('#content').innerHTML=`<div class="panel empty">${esc(e.message)}<br><button class="btn secondary" data-action="nav" data-view="${esc(view)}">Thử lại</button></div>`;}
}
function ticketRows(items){return items.map(t=>{
  const overdue=(!t.first_response_at&&Date.now()/1000>t.response_due)||(!t.resolved_at&&Date.now()/1000>t.resolution_due);
  return `<tr class="clickable" data-action="open-ticket" data-id="${t.id}" tabindex="0"><td><div class="ticket-number">#${String(t.id).padStart(4,'0')} · ${t.channel==='EMAIL'?'Email mô phỏng':'Biểu mẫu web'}</div><div class="subject">${esc(t.subject)}</div></td><td>${esc(t.customer_name)}</td><td>${badge(t.status)}</td><td><span class="priority ${t.priority}">● ${priorities[t.priority]}</span></td><td>${esc(t.assignee_name||'Chờ phân công')}<div class="small muted">${esc(t.team_name||'Chờ phân loại')}</div></td><td><span class="sla-mini ${overdue?'overdue':''}">${overdue?'● Quá hạn':t.resolved_at?'Đã giải quyết':date(t.response_due)}</span></td></tr>`;
}).join('');}
function table(items){return items.length?`<div class="table-wrap"><table class="data-table"><thead><tr><th>YÊU CẦU</th><th>KHÁCH HÀNG</th><th>TRẠNG THÁI</th><th>ƯU TIÊN</th><th>PHỤ TRÁCH</th><th>SLA</th></tr></thead><tbody>${ticketRows(items)}</tbody></table></div>`:'<div class="empty">Chưa có yêu cầu trong danh sách này.<br>Các ticket mới sẽ xuất hiện tại đây.</div>';}
async function loadTickets(){
  const params=new URLSearchParams({page:state.page,q:state.q});if(state.status)params.set('status',state.status);if(state.priority)params.set('priority',state.priority);
  const data=await api('/tickets?'+params);
  $('#content').innerHTML=`<div class="page-head"><div><div class="eyebrow">KẾT NỐI VÀ GIẢI QUYẾT</div><h1>${staff()?'Hộp thư hỗ trợ':'Yêu cầu của tôi'}</h1><p>${data.total} yêu cầu · ${staff()?'Phối hợp để khách hàng nhận được hỗ trợ đúng lúc.':'Theo dõi mọi trao đổi và tiến trình hỗ trợ.'}</p></div><div class="actions">${manager()?'<button class="btn secondary" data-action="new-email">Mô phỏng email</button>':''}${state.user.role!=='AGENT'?`<button class="btn" data-action="new-ticket">${icon('plus')} Tạo yêu cầu</button>`:''}</div></div><section class="panel"><form id="filter-form" class="toolbar"><div class="search"><input name="q" value="${esc(state.q)}" placeholder="Tìm tiêu đề hoặc mã ticket…" aria-label="Tìm ticket"></div><select name="status" aria-label="Lọc trạng thái">${options(statuses,state.status,'Mọi trạng thái')}</select><select name="priority" aria-label="Lọc ưu tiên">${options(priorities,state.priority,'Mọi mức ưu tiên')}</select><button class="btn secondary" type="submit">Lọc</button></form>${table(data.items)}<div class="pagination"><span>Trang ${data.page} · Tối đa 30 yêu cầu mỗi trang</span><div class="actions"><button class="btn secondary small" data-action="page" data-page="${state.page-1}" ${state.page===1?'disabled':''}>Trước</button><button class="btn secondary small" data-action="page" data-page="${state.page+1}" ${state.page*30>=data.total?'disabled':''}>Sau</button></div></div></section>`;
  if(manager()){
    const button=document.createElement('button');button.className='btn secondary';button.dataset.action='email-settings';button.textContent='Kênh email mô phỏng';
    $('.page-head .actions').append(button);
  }
}
async function loadOverview(){
  const [r,list]=await Promise.all([api('/reports?days='+state.days),api('/tickets')]);
  const cards=[['inbox','Tổng yêu cầu',r.total,'Trong '+state.days+' ngày gần nhất'],['clock','Đang cần hỗ trợ',r.open,'Ticket chưa giải quyết'],['spark','Tuân thủ SLA',r.sla_compliance==null?'—':r.sla_compliance+'%','Tính trên các mốc đã đến hạn hoặc hoàn tất'],['check','Mức hài lòng',r.csat==null?'—':r.csat+' / 5',r.rating_count+' lượt đánh giá']];
  $('#content').innerHTML=`<div class="page-head"><div><div class="eyebrow">GÓC NHÌN VẬN HÀNH</div><h1>${state.view==='sla'?'Theo dõi cam kết SLA':'Tổng quan hỗ trợ'}</h1><p>Mỗi yêu cầu được nhìn thấy. Mỗi phản hồi được theo dõi.</p></div><select id="report-days" aria-label="Khoảng thời gian báo cáo">${options({'7':'7 ngày gần nhất','30':'30 ngày gần nhất','90':'90 ngày gần nhất'},String(state.days))}</select></div><div class="cards">${cards.map(([i,label,value,sub])=>`<div class="card"><div class="card-top">${label}<span class="card-icon">${icon(i)}</span></div><div class="metric">${value}</div><div class="card-bottom">${sub}</div></div>`).join('')}</div><div class="hero-strip"><div><h2>${r.overdue?r.overdue+' yêu cầu cần chú ý đến hạn xử lý':'Giữ nhịp hỗ trợ, kết nối khách hàng'}</h2><p>Phản hồi trung bình: ${duration(r.avg_response_seconds)} · Giải quyết trung bình: ${duration(r.avg_resolution_seconds)}</p></div><button class="btn secondary" data-action="nav" data-view="tickets">Xem hộp thư →</button></div><div class="grid2"><section class="panel"><div class="panel-title"><h2>Trạng thái yêu cầu</h2><span class="small muted">${r.total} ticket</span></div><div class="distribution">${Object.entries(r.statuses).map(([s,n])=>`<div class="distribution-row"><span>${statuses[s]}</span><progress value="${n}" max="${Math.max(1,r.total)}"></progress><b>${n}</b></div>`).join('')}</div></section><section class="panel"><div class="panel-title"><h2>Nhóm vấn đề</h2><span class="small muted">Theo phân loại</span></div><div class="distribution">${state.meta.categories.map(c=>`<div class="distribution-row"><span>${esc(c.name)}</span><progress value="${r.categories[c.code]||0}" max="${Math.max(1,r.total)}"></progress><b>${r.categories[c.code]||0}</b></div>`).join('')}</div><div class="note small">SLA tính liên tục 24/7. Email xác nhận tiếp nhận không tính là phản hồi xử lý.</div></section></div>${state.view==='sla'?`<section class="panel"><div class="panel-title"><h2>Cảnh báo và chuyển cấp</h2><span class="badge">Kiểm tra mỗi 15 giây</span></div>${r.alerts.length?`<div class="table-wrap"><table class="data-table"><thead><tr><th>YÊU CẦU</th><th>LOẠI HẠN</th><th>MỨC</th><th>THỜI ĐIỂM</th></tr></thead><tbody>${r.alerts.map(a=>`<tr class="clickable" data-action="open-ticket" data-id="${a.ticket_id}"><td>#${a.ticket_id} · ${esc(a.subject)}</td><td>${a.deadline_type==='RESPONSE'?'Phản hồi':'Giải quyết'}</td><td><span class="badge ${a.level==='OVERDUE'?'WAITING_CUSTOMER':''}">${a.level==='OVERDUE'?'Đã quá hạn':'Gần đến hạn'}</span></td><td>${date(a.created_at)}</td></tr>`).join('')}</tbody></table></div>`:'<div class="empty">Chưa phát sinh cảnh báo.</div>'}</section><section class="panel" id="policy-panel"></section>`:`<section class="panel"><div class="panel-title"><h2>Yêu cầu gần đây</h2><button class="text-btn" data-action="nav" data-view="tickets">Xem tất cả →</button></div>${table(list.items.slice(0,5))}</section>`}`;
  if(state.view==='sla'){
    const policies=await api('/sla/policies');state.policies=policies;
    $('#policy-panel').innerHTML=`<div class="panel-title"><h2>Chính sách hiện hành</h2><span class="small muted">Phiên bản được giữ riêng trên mỗi ticket</span></div><div class="table-wrap"><table class="data-table"><thead><tr><th>ƯU TIÊN</th><th>PHẢN HỒI</th><th>GIẢI QUYẾT</th><th>PHIÊN BẢN</th><th></th></tr></thead><tbody>${policies.map(p=>`<tr><td>${priorities[p.priority]}</td><td>${duration(p.response_seconds)}</td><td>${duration(p.resolution_seconds)}</td><td>v${p.version}</td><td>${state.user.role==='ADMIN'?`<button class="text-btn" data-action="edit-policy" data-id="${p.id}">Cấu hình</button>`:''}</td></tr>`).join('')}</tbody></table></div>`;
  }
}
async function openTicket(id){
  clearTimeout(state.timer);
  const t=await api('/tickets/'+id);
  const same=state.ticket?.id===id;
  const draft=same?$('#reply')?.value||'':'';
  const visibility=same?$('#message-form [name=visibility]')?.value:null;
  const focused=same&&document.activeElement?.id==='reply';
  const cursor=focused?[$('#reply').selectionStart,$('#reply').selectionEnd]:null;
  const scroll=same?window.scrollY:0;
  state.ticket=t;
  const writable=state.user.role!=='AGENT'||!t.assignee_id||t.assignee_id===state.user.id;
  const next=state.user.role==='CUSTOMER'?(t.status==='RESOLVED'?['IN_PROGRESS','CLOSED']:[]):transitions[t.status];
  $('#content').innerHTML=`<div class="page-head"><div><button class="text-btn" data-action="nav" data-view="tickets">← Hộp thư hỗ trợ</button><div class="eyebrow">TICKET #${String(t.id).padStart(4,'0')} · ${t.channel==='EMAIL'?'EMAIL MÔ PHỎNG':'BIỂU MẪU WEB'}</div><h1 class="ticket-title">${esc(t.subject)}</h1></div>${badge(t.status)}</div><div class="ticket-layout"><div><section class="panel"><div class="panel-title"><h2>Hội thoại</h2><span class="small muted">${t.messages.length} tin nhắn</span></div><div class="conversation">${t.messages.map(m=>`<article class="message ${m.visibility==='INTERNAL'?'internal':m.sender_type==='CUSTOMER'?'':'staff'}"><div class="message-head"><b>${esc(m.sender_name||'Hệ thống')}</b><span>${m.visibility==='INTERNAL'?'Ghi chú nội bộ':m.sender_type==='CUSTOMER'?'Khách hàng':'Nhân viên'}</span><time>${date(m.created_at)}</time></div><div class="message-body">${esc(m.body)}</div></article>`).join('')}</div>${t.status==='CLOSED'?'<div class="note">Ticket đã đóng. Tạo yêu cầu mới nếu cần hỗ trợ thêm.</div>':writable?`<form id="message-form"><label for="reply">${staff()?'Soạn phản hồi':'Bổ sung thông tin'}</label><textarea id="reply" name="body" required maxlength="10000" placeholder="Nhập nội dung trao đổi…"></textarea><div class="toolbar"><select name="visibility" aria-label="Phạm vi tin nhắn">${staff()?'<option value="PUBLIC">Gửi khách hàng</option><option value="INTERNAL">Ghi chú nội bộ</option>':'<option value="PUBLIC">Gửi nhân viên</option>'}</select><button class="btn" type="submit">Gửi tin nhắn →</button></div></form>`:'<div class="note">Agent khác đang phụ trách ticket này. Bạn có thể xem nội dung trong nhóm.</div>'}${state.user.role==='CUSTOMER'&&['RESOLVED','CLOSED'].includes(t.status)?t.rating?`<div class="note">Bạn đã đánh giá ${t.rating.score}/5. Cảm ơn bạn!</div>`:`<form id="rating-form"><h3>Trải nghiệm hỗ trợ của bạn thế nào?</h3><label>Điểm đánh giá<select name="score">${options({'5':'5 · Rất hài lòng','4':'4 · Hài lòng','3':'3 · Bình thường','2':'2 · Chưa hài lòng','1':'1 · Không hài lòng'},'5')}</select></label><label>Nhận xét<input name="comment" maxlength="1000" placeholder="Điều gì chúng tôi có thể làm tốt hơn?"></label><button class="btn secondary" type="submit">Gửi đánh giá</button></form>`:''}</section>${staff()?`<section class="panel"><h3>Lịch sử xử lý</h3><div class="history">${t.events.slice().reverse().map(e=>`<div>${esc(e.description)}<time>${esc(e.actor_name||'Hệ thống')} · ${date(e.created_at)}</time></div>`).join('')}</div></section>`:''}</div><aside><section class="panel"><div class="panel-title"><h3>Thông tin yêu cầu</h3></div><div class="detail-meta"><div class="line"><span>Khách hàng</span><b>${esc(t.customer_name)}</b></div><div class="line"><span>Ưu tiên</span><span class="priority ${t.priority}">● ${priorities[t.priority]}</span></div><div class="line"><span>Nhóm</span><span>${esc(t.team_name||'Chờ phân loại')}</span></div><div class="line"><span>Agent</span><span>${esc(t.assignee_name||'Chưa phân công')}</span></div><div class="line"><span>Tạo lúc</span><span>${date(t.created_at)}</span></div><div class="line"><span>Hạn phản hồi</span><span>${date(t.response_due)}</span></div><div class="line"><span>Hạn giải quyết</span><span>${date(t.resolution_due)}</span></div></div><div class="actions">${manager()?'<button class="btn secondary full" data-action="assign">Điều chỉnh phân công</button>':''}${writable?next.map(s=>`<button class="btn ${s==='RESOLVED'?'':'secondary'} full" data-action="status" data-status="${s}">${s==='IN_PROGRESS'?(t.status==='NEW'?'Nhận xử lý':'Mở lại xử lý'):s==='CLOSED'?'Xác nhận hoàn tất':statuses[s]}</button>`).join(''):''}</div></section>${staff()?renderAi(t,writable):'<div class="note">Bạn sẽ được hỗ trợ tại đây. Hãy cung cấp mã lỗi hoặc thời điểm xảy ra để chúng tôi xử lý nhanh hơn.</div>'}</aside></div>`;
  if($('#reply'))$('#reply').value=draft;
  if(visibility&&$('#message-form [name=visibility]'))$('#message-form [name=visibility]').value=visibility;
  if(focused&&$('#reply')){$('#reply').focus({preventScroll:true});$('#reply').setSelectionRange(...cursor);}
  if(same)window.scrollTo(0,scroll);
  enhanceDeliveryUI(t,writable);
  if((t.job&&['QUEUED','RUNNING'].includes(t.job.status))||t.messages.some(m=>m.delivery_status==='PENDING')||(!staff()&&!['RESOLVED','CLOSED'].includes(t.status)))state.timer=setTimeout(()=>{if(state.ticket?.id===id)openTicket(id).catch(e=>toast(e.message,true));},1700);
}
function enhanceDeliveryUI(t,writable){
  document.querySelectorAll('.conversation .message').forEach((element,index)=>{
    const message=t.messages[index];
    if(message.sender_type==='BOT'){
      element.querySelector('.message-head b').textContent='Trợ lý FAQ';
      element.querySelector('.message-head span').textContent='Tự động · FAQ đã duyệt';
    }
    if(staff()&&message.delivery_status!=='DELIVERED'){
      const note=document.createElement('div');note.className='note'+(message.delivery_status==='FAILED'?' danger':'');
      note.textContent=message.delivery_status==='CANCELLED'?'Đã hủy vì phê duyệt hoặc hội thoại thay đổi':message.delivery_status==='FAILED'?'Gửi thất bại · Chưa tính SLA phản hồi':'Đang chờ gửi · Chưa tính SLA phản hồi';
      element.append(note);
      if(message.delivery_status==='FAILED'&&writable&&t.status!=='CLOSED'){
        const button=document.createElement('button');button.type='button';button.className='btn secondary small';button.dataset.action='retry-message';button.dataset.id=message.id;button.textContent='Thử gửi lại';note.append(button);
      }
    }
  });
  if(t.messages.some(m=>['PENDING','FAILED'].includes(m.delivery_status))){
    document.querySelectorAll('[data-action=status]').forEach(button=>{if(['RESOLVED','CLOSED'].includes(button.dataset.status)){button.disabled=true;button.title='Gửi thành công phản hồi trước khi hoàn tất';}});
  }
  if(t.ai?.mode==='APPROVED_FAQ'){
    $('.ai-panel [data-action=use-draft]')?.remove();
    const label=$('.ai-panel .small.muted');if(label)label.textContent='FAQ đã duyệt · Câu trả lời được lưu trong hội thoại';
  }
}
function renderAi(t,writable){
  const a=t.ai,pending=t.job&&['QUEUED','RUNNING'].includes(t.job.status);
  return `<section class="panel ai-panel"><h3>✧ Trợ lý tri thức</h3><p class="small muted">Gợi ý có nguồn để nhân viên xem xét trước khi gửi.</p>${pending?'<div class="note">Đang phân tích yêu cầu…</div>':a?`<div class="small muted">${a.mode==='LOCAL_EXTRACTIVE'?'Trích xuất KB tại máy':a.mode==='OLLAMA_DRAFT'?'Bản nháp từ Ollama':'Cần nhân viên xử lý'} · ${Math.round(a.latency_ms)} ms</div>${a.stale?'<div class="note warn">Ticket hoặc nguồn đã thay đổi. Chạy lại để lấy gợi ý mới.</div>':''}${a.answer?`<p class="message-body">${esc(a.answer)}</p>${a.sources.map(s=>`<button class="ai-source" data-action="source" data-title="${esc(s.title)}">↗ ${esc(s.title)} · v${s.version}</button>`).join('')}${writable&&!a.stale?'<button class="btn ghost full" data-action="use-draft">Dùng làm bản nháp</button>':''}`:`<div class="note warn">${esc(a.fallback_reason||'Chuyển nhân viên xử lý.')}</div>`}`:'<p class="small muted">Phân tích câu hỏi và tìm hướng dẫn phù hợp trong KB.</p>'}${writable&&!['RESOLVED','CLOSED'].includes(t.status)?`<button class="btn secondary full" data-action="analyze" ${pending?'disabled':''}>${icon('spark')} ${pending?'Đang chạy…':'Phân tích lại'}</button>`:''}</section>`;
}
async function loadKb(){
  const list=await api('/kb/articles?q='+encodeURIComponent(state.q));state.articles=list;
  $('#content').innerHTML=`<div class="page-head"><div><div class="eyebrow">KIẾN THỨC ĐƯỢC CHIA SẺ</div><h1>Kho tri thức</h1><p>Hướng dẫn rõ ràng. Câu trả lời có căn cứ.</p></div>${staff()?`<button class="btn" data-action="new-article">${icon('plus')} Viết bài mới</button>`:''}</div><form class="toolbar" id="kb-search"><div class="search"><input name="q" value="${esc(state.q)}" placeholder="Tìm hướng dẫn hoặc câu hỏi…" aria-label="Tìm bài viết"></div><button class="btn secondary" type="submit">Tìm kiếm</button></form><div class="kb-grid">${list.map(a=>`<article class="kb-card"><span class="badge">${esc(state.meta.categories.find(c=>c.code===a.category)?.name||a.category)}</span> ${staff()?`<span class="badge">${{DRAFT:'Bản nháp',PUBLISHED:'Đã xuất bản',RETIRED:'Đã thu hồi'}[a.status]}</span>`:''}<h2>${esc(a.title)}</h2><div class="body">${esc(a.body)}</div><div class="kb-footer"><span class="small muted">Phiên bản ${a.version} · ${date(a.updated_at)}</span><div class="actions">${staff()?`<button class="text-btn" data-action="edit-article" data-id="${a.id}">Sửa</button>`:''}${manager()?`<button class="btn secondary small" data-action="publish-article" data-id="${a.id}" data-mode="${a.status==='PUBLISHED'?'retire':'publish'}">${a.status==='PUBLISHED'?'Thu hồi':'Xuất bản'}</button>`:''}</div></div></article>`).join('')||'<div class="panel empty">Không tìm thấy bài viết phù hợp.</div>'}</div>`;
  if(manager())document.querySelectorAll('.kb-card').forEach((card,index)=>{
    const button=document.createElement('button');button.className='btn ghost small';button.dataset.action='faq-settings';button.dataset.id=list[index].id;button.textContent='FAQ tự trả lời';
    card.querySelector('.kb-footer .actions').append(button);
  });
}
async function loadUsers(){
  const users=await api('/users');state.users=users;
  $('#content').innerHTML=`<div class="page-head"><div><div class="eyebrow">CÙNG NHAU HỖ TRỢ</div><h1>Thành viên</h1><p>Tài khoản, vai trò và tình trạng nhận việc.</p></div><button class="btn" data-action="new-user">${icon('plus')} Thêm thành viên</button></div><section class="panel"><div class="table-wrap"><table class="data-table"><thead><tr><th>THÀNH VIÊN</th><th>EMAIL</th><th>VAI TRÒ</th><th>NHÓM</th><th>NHẬN TICKET</th></tr></thead><tbody>${users.map(u=>`<tr><td>${esc(u.name)}</td><td>${esc(u.email)}</td><td><span class="badge">${roles[u.role]}</span></td><td>${esc(state.meta.teams.find(t=>t.id===u.team_id)?.name||'—')}</td><td>${u.role==='AGENT'?`<button class="btn ${u.available?'ghost':'secondary'} small" data-action="availability" data-id="${u.id}" data-value="${u.available?'0':'1'}">${u.available?'Đang nhận việc':'Tạm dừng'}</button>`:'—'}</td></tr>`).join('')}</tbody></table></div></section>`;
}
function showDialog(title,content,formId,submit='Lưu'){
  const d=$('#modal');d.innerHTML=`<form id="${formId}"><h2>${title}</h2>${content}<div id="dialog-error" class="form-error" role="alert"></div><div class="dialog-actions"><button class="btn secondary" type="button" data-action="close-modal">Hủy</button><button class="btn" type="submit">${submit}</button></div></form>`;d.showModal();
}
function newTicketDialog(email=false){
  state.emailMode=email;
  showDialog(email?'Mô phỏng email đến':'Tạo yêu cầu hỗ trợ',`${manager()?`<label>Khách hàng<select name="customer_id" required>${state.meta.customers.map(c=>`<option value="${c.id}">${esc(c.name)} · ${esc(c.email)}</option>`).join('')}</select></label>`:''}${email?'<label>Mã sự kiện email<input name="external_event_id" required maxlength="200" placeholder="VD: email-001"></label><div class="note">Gửi lại cùng mã sự kiện sẽ trả về ticket cũ, không tạo trùng.</div>':''}<label>Tiêu đề<input name="subject" required minlength="3" maxlength="200" placeholder="Bạn cần hỗ trợ vấn đề gì?"></label><label>Nội dung<textarea name="body" required minlength="5" maxlength="10000" placeholder="Mô tả chi tiết để chúng tôi hỗ trợ bạn…"></textarea></label><label>Mức ưu tiên<select name="priority">${options(priorities,'NORMAL')}</select></label>`,'new-ticket-form','Gửi yêu cầu');
}
function articleDialog(article){
  state.editArticle=article||null;
  showDialog(article?'Chỉnh sửa bài viết':'Viết bài tri thức',`<label>Tiêu đề<input name="title" required minlength="3" maxlength="200" value="${esc(article?.title||'')}"></label><label>Danh mục<select name="category">${options(Object.fromEntries(state.meta.categories.map(c=>[c.code,c.name])),article?.category||'ACCOUNT')}</select></label><label>Nội dung<textarea name="body" required minlength="20" maxlength="20000" rows="9">${esc(article?.body||'')}</textarea></label><div class="note">Nội dung được lưu thành bản nháp; trưởng nhóm duyệt xuất bản trước khi dùng để trả lời.</div>`,'article-form','Lưu bản nháp');
}
async function faqDialog(articleId){
  const article=state.articles.find(a=>a.id===articleId);state.faqArticle=article;
  const rules=await api(`/kb/articles/${articleId}/faq-rules`);state.faqRules=rules;
  const existing=rules.map(rule=>{
    const valid=rule.active&&rule.article_version===rule.current_version&&rule.article_status==='PUBLISHED';
    return `<div class="note"><b>${esc(rule.question)}</b><p class="small">v${rule.article_version} · ${valid?'Đang bật':rule.active?'Cần duyệt lại phiên bản mới':'Đã tắt'}</p><button type="button" class="btn secondary small" data-action="faq-switch" data-id="${rule.id}" data-active="${valid?'0':'1'}">${valid?'Tắt tự trả lời':'Duyệt và bật lại'}</button></div>`;
  }).join('');
  showDialog('FAQ · '+esc(article.title),`<div class="note">Chỉ tự trả lời khi nội dung khách gửi khớp câu hỏi đã duyệt (bỏ qua dấu, viết hoa và dấu câu), ticket mới có ưu tiên bình thường/thấp. Nội dung gửi là bài KB đã duyệt, không dùng câu sinh tự do từ LLM.</div>${existing}<label>Câu hỏi mẫu mới<textarea name="question" required minlength="8" maxlength="500" placeholder="Nhập nguyên câu hỏi FAQ cho phép tự trả lời"></textarea></label><p class="small muted">Phê duyệt gắn với phiên bản ${article.version}. Sửa hoặc thu hồi bài sẽ dừng tự trả lời cho tới khi duyệt lại.</p>`,'faq-form','Duyệt câu hỏi mới');
}
document.addEventListener('click',async event=>{
  const el=event.target.closest('[data-action]');if(!el||el.disabled)return;
  const a=el.dataset.action;
  try{
    if(a==='demo'){$('#login-form [name=email]').value=el.dataset.role+'@helpdesk.local';$('#login-form [name=password]').value='Demo@20261';return;}
    if(a==='logout'){await api('/auth/logout',{method:'POST'});state.user=null;return renderLogin();}
    if(a==='nav')return await navigate(el.dataset.view);
    if(a==='open-ticket')return await openTicket(Number(el.dataset.id));
    if(a==='new-ticket'||a==='new-email')return newTicketDialog(a==='new-email');
    if(a==='faq-settings')return await faqDialog(Number(el.dataset.id));
    if(a==='faq-switch'){
      await api('/faq-rules/'+el.dataset.id,{method:'PATCH',body:{active:el.dataset.active==='1',article_version:state.faqArticle.version}});
      $('#modal').close();toast('Đã cập nhật phê duyệt FAQ');return await faqDialog(state.faqArticle.id);
    }
    if(a==='email-settings'){
      const config=await api('/channels/email/simulation');
      return showDialog('Kênh email mô phỏng',`<div class="note">Dùng để trình diễn kênh gửi lỗi. Chỉ áp dụng email mô phỏng; không gửi thư ra ngoài. Tin gửi lỗi được giữ lại để Agent thử lại và chưa được tính phản hồi SLA.</div><label>Số lần gửi tiếp theo sẽ thất bại (0-5)<input name="failures_remaining" type="number" min="0" max="5" required value="${config.failures_remaining}"></label>`,'email-settings-form');
    }
    if(a==='retry-message'){await api('/messages/'+el.dataset.id+'/retry',{method:'POST'});toast('Đã đưa vào hàng đợi thử lại');return await openTicket(state.ticket.id);}
    if(a==='close-modal')return $('#modal').close();
    if(a==='page'){state.page=Number(el.dataset.page);return await loadTickets();}
    if(a==='status'){await api(`/tickets/${state.ticket.id}/status`,{method:'PATCH',body:{status:el.dataset.status,version:state.ticket.version}});toast('Đã cập nhật trạng thái');return await openTicket(state.ticket.id);}
    if(a==='analyze'){await api(`/tickets/${state.ticket.id}/ai-suggestions`,{method:'POST'});return await openTicket(state.ticket.id);}
    if(a==='use-draft'){$('#reply').value=state.ticket.ai.answer+'\n\nNguồn: '+state.ticket.ai.sources.map(s=>s.title).join('; ');$('#reply').focus();return;}
    if(a==='source'){clearTimeout(state.timer);state.ticket=null;state.q=el.dataset.title;state.view='kb';shell();return await loadKb();}
    if(a==='assign'){
      const t=state.ticket;
      return showDialog('Điều chỉnh phân công',`<label>Nhóm<select id="assign-team" name="team_id">${options(Object.fromEntries(state.meta.teams.map(x=>[x.id,x.name])),String(t.team_id||1))}</select></label><label>Agent<select id="assign-agent" name="assignee_id"></select></label><label>Danh mục<select name="category" id="assign-category"></select></label>`,'assign-form'),updateAssignmentOptions(t.assignee_id,t.category);
    }
    if(a==='new-article')return articleDialog();
    if(a==='edit-article')return articleDialog(state.articles.find(x=>x.id===Number(el.dataset.id)));
    if(a==='publish-article'){await api(`/kb/articles/${el.dataset.id}/${el.dataset.mode}`,{method:'POST'});toast('Đã cập nhật trạng thái bài viết');return await loadKb();}
    if(a==='availability'){await api(`/users/${el.dataset.id}/availability`,{method:'PATCH',body:{available:el.dataset.value==='1'}});return await loadUsers();}
    if(a==='new-user')return showDialog('Thêm thành viên',`<label>Họ tên<input name="name" required minlength="2" maxlength="100"></label><label>Email<input type="email" name="email" required></label><label>Mật khẩu khởi tạo<input name="password" type="password" required minlength="10" autocomplete="new-password"></label><div class="field-row"><label>Vai trò<select name="role">${options(roles,'AGENT')}</select></label><label>Nhóm (bắt buộc với Agent)<select name="team_id">${options(Object.fromEntries(state.meta.teams.map(t=>[t.id,t.name])),'','Không thuộc nhóm')}</select></label></div>`,'user-form','Tạo tài khoản');
    if(a==='edit-policy'){
      const p=state.policies.find(x=>x.id===Number(el.dataset.id));state.editPolicy=p;
      return showDialog('SLA · '+priorities[p.priority],`<label>Hạn phản hồi (phút)<input name="response" type="number" min="1" max="43200" value="${p.response_seconds/60}" required></label><label>Hạn giải quyết (phút)<input name="resolution" type="number" min="1" max="43200" value="${p.resolution_seconds/60}" required></label><div class="note">Tạo phiên bản mới cho ticket mới. Ticket đã tiếp nhận giữ nguyên chính sách trước đó.</div>`,'policy-form','Lưu phiên bản mới');
    }
  }catch(e){toast(e.message,true);if(state.ticket)openTicket(state.ticket.id).catch(()=>{});}
});
function updateAssignmentOptions(assignee,category){
  const team=Number($('#assign-team').value);
  $('#assign-agent').innerHTML=options(Object.fromEntries(state.meta.staff.filter(x=>x.role==='AGENT'&&x.team_id===team).map(x=>[x.id,x.name])),String(assignee||''),'Chưa gán Agent');
  $('#assign-category').innerHTML=options(Object.fromEntries(state.meta.categories.filter(x=>x.team_id===team).map(x=>[x.code,x.name])),category);
}
document.addEventListener('change',event=>{
  if(event.target.id==='assign-team')updateAssignmentOptions();
  if(event.target.id==='report-days'){state.days=Number(event.target.value);loadOverview().catch(e=>toast(e.message,true));}
});
document.addEventListener('keydown',event=>{if(event.key==='Enter'&&event.target.matches('tr[data-action]'))event.target.click();});
document.addEventListener('submit',async event=>{
  event.preventDefault();const form=event.target;const data=Object.fromEntries(new FormData(form));const button=form.querySelector('[type=submit]');if(button)button.disabled=true;
  try{
    if(form.id==='login-form'){
      $('#login-error').textContent='';state.user=await api('/auth/login',{method:'POST',body:data});state.meta=await api('/meta');return await navigate(manager()?'overview':'tickets');
    }
    if(form.id==='filter-form'){Object.assign(state,{q:data.q,status:data.status,priority:data.priority,page:1});return await loadTickets();}
    if(form.id==='kb-search'){state.q=data.q;return await loadKb();}
    if(form.id==='new-ticket-form'){
      if(data.customer_id)data.customer_id=Number(data.customer_id);
      const result=await api(state.emailMode?'/channels/email/events':'/tickets',{method:'POST',body:data});$('#modal').close();toast(result.duplicate?'Sự kiện đã tồn tại; mở ticket trước đó':'Đã tiếp nhận yêu cầu');state.view='tickets';shell();return await openTicket(result.id);
    }
    if(form.id==='message-form'){data.version=state.ticket.version;const result=await api(`/tickets/${state.ticket.id}/messages`,{method:'POST',body:data});form.reset();toast(result.delivery_status==='PENDING'?'Đã xếp hàng chờ gửi':'Đã lưu tin nhắn');return await openTicket(state.ticket.id);}
    if(form.id==='faq-form'){await api(`/kb/articles/${state.faqArticle.id}/faq-rules`,{method:'POST',body:{question:data.question,article_version:state.faqArticle.version}});$('#modal').close();toast('Đã duyệt câu hỏi FAQ');return await faqDialog(state.faqArticle.id);}
    if(form.id==='email-settings-form'){await api('/channels/email/simulation',{method:'POST',body:{failures_remaining:Number(data.failures_remaining)}});$('#modal').close();toast('Đã cập nhật kênh mô phỏng');return;}
    if(form.id==='rating-form'){data.score=Number(data.score);await api(`/tickets/${state.ticket.id}/ratings`,{method:'POST',body:data});toast('Cảm ơn bạn đã đánh giá');return await openTicket(state.ticket.id);}
    if(form.id==='assign-form'){data.team_id=Number(data.team_id);data.assignee_id=data.assignee_id?Number(data.assignee_id):null;data.category=data.category||null;data.version=state.ticket.version;await api(`/tickets/${state.ticket.id}/assignment`,{method:'PATCH',body:data});$('#modal').close();return await openTicket(state.ticket.id);}
    if(form.id==='article-form'){
      if(state.editArticle)data.version=state.editArticle.version;
      await api('/kb/articles'+(state.editArticle?'/'+state.editArticle.id:''),{method:state.editArticle?'PUT':'POST',body:data});$('#modal').close();toast('Đã lưu bản nháp');return await loadKb();
    }
    if(form.id==='user-form'){data.team_id=data.team_id?Number(data.team_id):null;await api('/users',{method:'POST',body:data});$('#modal').close();state.meta=await api('/meta');toast('Đã tạo thành viên');return await loadUsers();}
    if(form.id==='policy-form'){await api('/sla/policies',{method:'POST',body:{priority:state.editPolicy.priority,response_seconds:Number(data.response)*60,resolution_seconds:Number(data.resolution)*60}});$('#modal').close();toast('Đã tạo phiên bản SLA mới');return await loadOverview();}
  }catch(e){
    if(form.id==='login-form')$('#login-error').textContent=e.message;
    else if(form.closest('dialog'))$('#dialog-error').textContent=e.message;
    else {toast(e.message,true);if(form.id==='message-form'&&state.ticket)await openTicket(state.ticket.id);}
  }finally{if(button)button.disabled=false;}
});
async function boot(){try{state.user=await api('/auth/me');state.meta=await api('/meta');await navigate(manager()?'overview':'tickets');}catch{renderLogin();}}
boot();
