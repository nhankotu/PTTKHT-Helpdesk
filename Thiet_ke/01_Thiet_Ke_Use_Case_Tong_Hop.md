# TÀI LIỆU THIẾT KẾ USE CASE TỔNG HỢP (HỢP NHẤT & ĐÃ XỬ LÝ XUNG ĐỘT)

**Đề tài 07:** Nền tảng Helpdesk đa kênh tích hợp AI  
**Phiên bản:** 1.0 (Bản hợp nhất từ UC01–UC08 + FR01)  
**Ngày cập nhật:** 21/11/2026  
**Nguồn đầu vào:** Đề tài gốc, Kế hoạch tuần 1, thiết kế UC01–UC02 (TV1), UC03 (TV2), UC04–UC05 (TV3), UC06–UC07 (TV4), UC08 (TV5).

---

## 1. Bảng quyết định xử lý xung đột giữa các phân hệ

Trước khi hợp nhất đặc tả Use Case, các điểm mâu thuẫn giữa tài liệu của các thành viên được chốt thống nhất như sau:

| STT | Nội dung xung đột | Khác biệt trước đây | Quyết định chốt hợp nhất |
|---|---|---|---|
| 1 | **Bộ trạng thái Ticket (`status`)** | Đề tài & UC08: 5 trạng thái; UC04–05: 7 trạng thái; UC03: 9 trạng thái (dùng `PENDING_CUSTOMER`, thêm `PENDING_INTERNAL`, `REOPENED`). | Chốt **7 trạng thái chuẩn**: `NEW`, `ASSIGNED`, `IN_PROGRESS`, `WAITING_CUSTOMER`, `RESOLVED`, `CLOSED`, `CANCELLED`. Bỏ `PENDING_CUSTOMER` (dùng `WAITING_CUSTOMER`); mở lại chuyển `RESOLVED → IN_PROGRESS` và ghi `ticket_event(REOPENED)`; UC08 loại `CANCELLED` khỏi chỉ số "Ticket đang mở". |
| 2 | **Mức độ ưu tiên (`priority`)** | UC03 dùng `MEDIUM`; các UC khác dùng `NORMAL`. | Chốt 4 mức thống nhất: `URGENT`, `HIGH`, `NORMAL`, `LOW` (sửa `MEDIUM → NORMAL` ở UC03). |
| 3 | **Tạm dừng SLA khi `WAITING_CUSTOMER`** | Đề tài & UC08: không tạm dừng; UC03 & UC05: có tạm dừng `RESOLUTION`. | Chốt **CÓ tạm dừng `RESOLUTION` SLA** khi ở trạng thái `WAITING_CUSTOMER` (lưu tại `sla_pause`); `FIRST_RESPONSE` không bao giờ tạm dừng. UC08 tính thời gian giải quyết ròng (trừ `resolution_paused_seconds`). |
| 4 | **Mở lại ticket sau `RESOLVED`** | UC08 giữ hạn cũ; UC05 tạo `sla_pause(RESOLVED_UNTIL_REOPEN)`; UC03 tính lại toàn bộ. | Chốt theo UC05: chỉ tiếp tục `RESOLUTION` bằng thời gian còn lại, tạo bản ghi `sla_pause(RESOLVED_UNTIL_REOPEN)`, giữ nguyên `FIRST_RESPONSE` và giữ lịch sử `breached_at`. |
| 5 | **Thời điểm hoàn thành `FIRST_RESPONSE`** | UC03 ghi nhận ngay khi mở ticket (`ASSIGNED → IN_PROGRESS`); UC05 chỉ tính phản hồi nhân viên; Đề tài tính cả AI. | Chốt: `FIRST_RESPONSE` chỉ hoàn thành khi **phản hồi công khai hữu ích** từ Agent hoặc AI Auto-Reply có `delivery_status IN ('SENT', 'DELIVERED')` và `is_auto_ack = FALSE`. Không tính ghi chú nội bộ, email xác nhận tiếp nhận, hay thao tác mới mở ticket. |
| 6 | **Ranh giới Actor & Hệ thống** | UC03 liệt kê `Helpdesk System` làm Actor, để `Tra cứu KB/RAG` là `«include»`, và còn lỗi copy-paste `AIMS System`, `Khách hàng` lưu ghi chú nội bộ. UC04 nối `Agent` vào `Phân công thủ công`. | Loại `Helpdesk System`, `AI`, `Scheduler`, `Indexer` khỏi danh sách Actor ngoài. Sửa toàn bộ lỗi Actor/copy-paste ở UC03; chuyển `Gợi ý AI RAG` thành tùy chọn (`«extend»` từ UC03 sang UC07). Chỉ `Supervisor` thực hiện `Phân công thủ công`. |
| 7 | **Mâu thuẫn nội bộ UC01–UC02** | File Word ghi 10 bảng (có `channel_account`, `outbox_event`, `priority`, `version`) và 1 conversation = 1 ticket; file SQL lại chỉ có 8 bảng. | Chốt theo bản thiết kế mới nhất (10 bảng có `channel_account` & `outbox_event`), dùng `ticket` chung có `priority` và `version`; quy tắc MVP: 1 conversation đang mở gắn 1 ticket đang mở (khi ticket đã `CLOSED`, tin mới tạo ticket mới liên kết `parent_ticket_id`). |
| 8 | **Thiếu UC06–UC07 & FR01** | Thư mục ban đầu thiếu thiết kế TV4 và phần Auth/RBAC. | Đã bổ sung đầy đủ đặc tả UC06, UC07 và tóm tắt FR01 (Auth & RBAC) vào bản hợp nhất này. |

---

## 2. Danh mục Actor và ranh giới hệ thống thống nhất

| Actor | Loại | Vai trò và quyền hạn chính |
|---|---|---|
| **Khách hàng (Customer)** | Actor chính | Gửi yêu cầu qua web/email/chat, xem ticket của chính mình, bổ sung thông tin, xác nhận giải quyết và gửi đánh giá CSAT. Tuyệt đối không xem được ghi chú nội bộ (`INTERNAL`). |
| **Nhân viên hỗ trợ (Agent)** | Actor chính | Xem hàng đợi được phân quyền, xử lý ticket được phân công, gửi phản hồi công khai, thêm ghi chú nội bộ, dùng gợi ý AI RAG, soạn/gửi duyệt bài viết KB. |
| **Trưởng nhóm (Supervisor)** | Actor chính | Giám sát hàng đợi nhóm, phân công/chuyển giao thủ công, điều chỉnh ưu tiên, xử lý cảnh báo/vi phạm SLA, duyệt/xuất bản/thu hồi bài KB, xem báo cáo nhóm/hệ thống. |
| **Quản trị viên (Admin)** | Actor chính | Quản lý tài khoản, vai trò (`role`), nhóm (`support_team`), kênh (`channel_account`), danh mục (`category`), luật định tuyến (`routing_rule`), chính sách SLA (`sla_policy`) và xem báo cáo toàn hệ thống. |
| **Hệ thống kênh bên ngoài** | Actor hệ thống ngoài | Email Gateway, Webhook Facebook/Zalo đẩy sự kiện yêu cầu vào nền tảng và nhận phản hồi gửi ra. |

> **Ranh giới hệ thống:** `Helpdesk System`, `AI Classifier`, `AI RAG Engine`, `KB Indexer`, `SLA Scheduler`, `Outbox Worker` là thành phần **nội bộ**, không vẽ thành Actor ngoài.

---

## 3. Sơ đồ Use Case tổng thể & Luồng tích hợp xuyên suốt

Ảnh sơ đồ tổng thể: [UseCase_Tong_The.png](./Bieu_do/Tong_the/UseCase_Tong_The.png)  
Ảnh máy trạng thái Ticket hợp nhất: [Ticket_Lifecycle_Unified.png](./Bieu_do/Tong_the/Ticket_Lifecycle_Unified.png)

| Nhóm chức năng | Mã | Tên Use Case | Actor chính | Điểm tích hợp chính |
|---|---|---|---|---|
| **Nền tảng (FR01)** | **UC00** | Đăng nhập, phân quyền & quản trị hệ thống | Tất cả người dùng; Admin | Cung cấp phiên JWT, kiểm tra RBAC cho UC01–UC08 |
| **1. Tiếp nhận đa kênh** | **UC01** | Gửi và tiếp nhận yêu cầu hỗ trợ | Khách hàng | Đẩy yêu cầu kèm `Idempotency-Key` sang UC02 |
| **1. Tiếp nhận đa kênh** | **UC02** | Tiếp nhận, chuẩn hóa và tổng hợp ticket | Hệ thống kênh bên ngoài | Phát sự kiện `TicketCreated` (qua `outbox_event`) cho UC04, UC05, UC07 |
| **2. Quản lý & điều phối** | **UC03** | Xử lý và cập nhật trạng thái ticket | Agent; Supervisor | Tiêu thụ gợi ý UC07; phát sự kiện phản hồi/đổi trạng thái cho UC05; kích hoạt CSAT |
| **2. Quản lý & điều phối** | **UC04** | AI phân loại và định tuyến ticket | Supervisor (thủ công); tự động từ UC02 | Gán `category_id`, `team_id`, `assigned_agent_id`, chuyển `NEW → ASSIGNED` |
| **2. Quản lý & điều phối** | **UC05** | Theo dõi SLA, cảnh báo và chuyển cấp | Supervisor; Agent (nhận cảnh báo) | Khởi tạo từ UC02; cập nhật theo sự kiện UC03; cung cấp dữ liệu cho UC08 |
| **3. Knowledge Base & AI** | **UC06** | Quản lý bài viết Knowledge Base | Agent (soạn); Supervisor (duyệt/thu hồi) | Xuất bản `kb_chunk` phục vụ truy xuất của UC07 |
| **3. Knowledge Base & AI** | **UC07** | AI RAG gợi ý câu trả lời có dẫn nguồn | Agent | Truy xuất KB từ UC06; trả gợi ý có nguồn hoặc Fallback sang UC03 |
| **4. Báo cáo & thống kê** | **UC08** | Xem báo cáo ticket, SLA và CSAT | Supervisor; Admin | Đọc `v_ticket_sla_summary`, `sla_event`, `satisfaction_rating`; mở chi tiết UC03 |

---

## 4. Vòng đời Ticket thống nhất (7 trạng thái chuẩn)

| Trạng thái | Tác động SLA | Ý nghĩa nghiệp vụ |
|---|---|---|
| `NEW` | Chạy `FIRST_RESPONSE` & `RESOLUTION` | Ticket mới tiếp nhận, chưa có Agent phụ trách (hoặc chờ Supervisor phân công thủ công). |
| `ASSIGNED` | Tiếp tục chạy cả 2 mốc | Ticket đã được gán cho Agent/Nhóm (tự động qua UC04 hoặc thủ công bởi Supervisor). |
| `IN_PROGRESS` | Chạy `RESOLUTION`; `FIRST_RESPONSE` dừng **khi phản hồi đầu tiên gửi thành công** | Agent đang xử lý, trao đổi với khách hoặc phối hợp nội bộ (qua ghi chú `INTERNAL`). |
| `WAITING_CUSTOMER` | **Tạm dừng (`PAUSED`) `RESOLUTION`**; không dừng `FIRST_RESPONSE` | Đang chờ khách hàng bổ sung thông tin sau khi Agent đã gửi câu hỏi ra kênh khách. |
| `RESOLVED` | **Hoàn tất (`COMPLETED`) `RESOLUTION`** | Agent đã cung cấp giải pháp cuối cùng (bắt buộc `resolution_code`); hẹn gửi khảo sát CSAT. |
| `CLOSED` | Kết thúc vòng đời | Khách xác nhận hoàn tất hoặc tự động đóng sau 48h không có phản hồi mới. Bất biến; tin mới tạo ticket mới gắn `parent_ticket_id`. |
| `CANCELLED` | **Hủy (`CANCELLED`) cả 2 mốc** (không tính đạt) | Ticket spam/trùng lặp/không hợp lệ; bắt buộc `cancel_reason` ≥ 10 ký tự; không gửi CSAT. |

**Ma trận chuyển trạng thái hợp lệ (State Machine Guard):**
- `NEW → ASSIGNED` (UC04 tự động / Supervisor thủ công)
- `NEW / ASSIGNED → IN_PROGRESS` (Agent nhận xử lý trực tiếp)
- `NEW / ASSIGNED / IN_PROGRESS → CANCELLED` (Agent/Supervisor, bắt buộc `cancel_reason` ≥ 10 ký tự)
- `IN_PROGRESS → WAITING_CUSTOMER` (Agent gửi tin nhắn công khai yêu cầu thêm thông tin → mở `sla_pause`)
- `WAITING_CUSTOMER → IN_PROGRESS` (Khách phản hồi qua webhook/web → đóng `sla_pause`, dời `deadline_at`)
- `WAITING_CUSTOMER → RESOLVED` (Scheduler tự động khi quá 72h không phản hồi, `resolution_code = 'AUTO_CLOSED_NO_RESPONSE'`)
- `IN_PROGRESS → RESOLVED` (Agent chọn `resolution_code` và gửi giải pháp)
- `RESOLVED → IN_PROGRESS` (Khách báo vấn đề chưa xong trước khi `CLOSED` → ghi `ticket_event(REOPENED)`, mở lại `RESOLUTION` SLA kèm `sla_pause(RESOLVED_UNTIL_REOPEN)`)
- `RESOLVED → CLOSED` (Khách xác nhận hoặc quá 48h không phản hồi)
- *Chuyển giao (Transfer/Reassign):* Cập nhật `team_id`/`assigned_agent_id`, giữ nguyên đồng hồ SLA, ghi `assignment_history` và `ticket_event`. Mọi thao tác cập nhật ticket bắt buộc kiểm tra **`ticket.version`** (Optimistic Locking).

---

## 5. Đặc tả chi tiết 08 Use Case (chuẩn hóa cùng biểu mẫu)

### UC00 (FR01) — Đăng nhập, phân quyền và quản trị nền tảng *(tóm tắt)*
- **Actor:** Customer, Agent, Supervisor, Admin.
- **Mục tiêu:** Xác thực người dùng (JWT/Session), kiểm soát quyền truy cập theo vai trò (`role`, `user_role`) và phạm vi dữ liệu (Khách chỉ xem ticket của mình; Agent chỉ xem hàng đợi/ticket thuộc nhóm được cấp quyền; Supervisor quản lý nhóm mình; Admin quản trị danh mục, kênh, luật định tuyến, chính sách SLA).

---

### UC01 — Gửi và tiếp nhận yêu cầu hỗ trợ
| Thuộc tính | Nội dung |
|---|---|
| **Actor chính / phụ** | Chính: Khách hàng. |
| **Mục tiêu** | Khách hàng gửi yêu cầu hỗ trợ qua biểu mẫu Web (hoặc kênh hỗ trợ) và nhận xác nhận đã tiếp nhận kèm mã tra cứu. |
| **Tiền điều kiện** | Kênh tiếp nhận đang hoạt động (`is_active = TRUE`). Hỗ trợ cả khách đã đăng nhập và khách vãng lai (theo cấu hình kênh). |
| **Sự kiện kích hoạt** | Khách hàng điền biểu mẫu yêu cầu và bấm "Gửi yêu cầu". |
| **Luồng chính** | 1. Khách hàng nhập tiêu đề, nội dung, thông tin liên hệ và tệp đính kèm tùy chọn (≤ 25MB/tệp, đúng MIME cho phép).<br>2. Client kiểm tra dữ liệu cơ bản và gửi yêu cầu kèm `Idempotency-Key` duy nhất.<br>3. Hệ thống xác thực đầu vào, kiểm tra tệp, kiểm tra trùng `Idempotency-Key` và ghi nhận yêu cầu bền vững vào `inbound_event` (`RECEIVED`).<br>4. Hệ thống trả `202 Accepted` kèm `requestId` và trạng thái `RECEIVED` (phân biệt rõ *đã nhận yêu cầu* khác với *đã tạo mã ticket*).<br>5. Khách hàng nhận thông báo xác nhận tiếp nhận (`is_auto_ack = TRUE`) và có thể tra cứu mã ticket sau khi UC02 xử lý xong. |
| **Luồng thay thế / Ngoại lệ** | - **E1 — Thiếu dữ liệu / tệp vượt 25MB hoặc sai định dạng:** Trả `400/422`, giữ lại form.<br>- **E2 — Gửi trùng `Idempotency-Key`:** Cùng payload → trả kết quả cũ; khác payload → trả `409 Conflict`.<br>- **E3 — Quá tần suất:** Trả `429 Too Many Requests`.<br>- **E4 — Lỗi gửi email xác nhận:** Không làm mất yêu cầu đã lưu; ghi nhận lỗi gửi để thử lại. |
| **Hậu điều kiện** | Yêu cầu được lưu bền vững trong `inbound_event`, sẵn sàng cho UC02 tạo/ghép ticket. |
| **Quy tắc nghiệp vụ** | Chỉ trả `202` sau khi đã ghi bền vững; email xác nhận tiếp nhận (`is_auto_ack = TRUE`) không tính là `FIRST_RESPONSE` SLA. |

---

### UC02 — Tiếp nhận, chuẩn hóa và tổng hợp ticket
| Thuộc tính | Nội dung |
|---|---|
| **Actor chính / phụ** | Chính: Hệ thống kênh bên ngoài (Email Gateway, Facebook, Zalo) và yêu cầu nội bộ từ UC01. |
| **Mục tiêu** | Xác thực nguồn, chuẩn hóa sự kiện đa kênh về một định dạng chung, chống nhận trùng và tạo mới hoặc nối vào ticket phù hợp. |
| **Tiền điều kiện** | `channel_account` đã cấu hình và đang hoạt động. |
| **Sự kiện kích hoạt** | Webhook từ kênh ngoài hoặc yêu cầu mới từ UC01. |
| **Luồng chính** | 1. Hệ thống xác thực chữ ký webhook / nguồn nội bộ; xác định `channel_account_id` và `external_event_id`.<br>2. Ghi `inbound_event` (đã che PII trong `payload_redacted`); kiểm tra `UNIQUE(channel_account_id, external_event_id)` để chống xử lý trùng.<br>3. Adapter chuẩn hóa sự kiện: xác định `customer_identity`, `conversation` (theo `(channel_account_id, external_thread_id)`), tin nhắn (`external_message_id`) và metadata tệp (`attachment`).<br>4. Trong 1 transaction:<br>   - Nếu hội thoại chưa có ticket đang mở (hoặc ticket cũ đã `CLOSED`): tạo `ticket` mới (`status = 'NEW'`, `priority = 'NORMAL'`, `version = 0`, liên kết `parent_ticket_id` nếu vé cũ đã `CLOSED`), tạo `ticket_message` (`INBOUND`, `PUBLIC`), ghi `outbox_event(TicketCreated)` trong cùng transaction.<br>   - Nếu hội thoại đã có ticket đang mở (`NEW..RESOLVED`): nối `ticket_message` vào ticket đó (nếu ticket đang `WAITING_CUSTOMER` hoặc `RESOLVED` thì phát sự kiện cho UC03/UC05 chuyển về `IN_PROGRESS`).<br>5. Đánh dấu `inbound_event.process_status = 'PROCESSED'`, lưu `ticket_id` và `message_id`.<br>6. Worker đọc `outbox_event` và phát `TicketCreated` tới UC04, UC05, UC07. |
| **Luồng thay thế / Ngoại lệ** | - **E1 — Chữ ký webhook không hợp lệ:** Từ chối `401/403`, không ghi sự kiện nghiệp vụ.<br>- **E2 — Provider retry sự kiện/tin nhắn trùng:** Bắt ràng buộc UNIQUE, trả kết quả cũ, không nhân đôi ticket/tin nhắn.<br>- **E3 — Không tự động gộp khách chưa xác minh:** Không tự gộp 2 `customer` chỉ vì trùng tên/email chưa xác minh (`is_verified = FALSE`).<br>- **E4 — Lỗi phát Outbox:** Worker retry giữ nguyên `eventId`; các consumer (UC04/UC05) phải chống xử lý trùng. |
| **Hậu điều kiện** | Đúng 1 `ticket_message` nguồn được lưu; ticket mới được tạo kèm đúng 1 sự kiện `TicketCreated` trong outbox. |
| **Quy tắc nghiệp vụ** | Ticket phải được lưu bền vững trước khi gọi bất kỳ dịch vụ AI nào; UC02 không tự gán Agent. |

---

### UC03 — Xử lý và cập nhật trạng thái ticket
| Thuộc tính | Nội dung |
|---|---|
| **Actor chính / phụ** | Chính: Agent. Phụ: Supervisor (khi kiểm tra/chuyển giao), Khách hàng (nhận phản hồi/gửi khảo sát). |
| **Mục tiêu** | Agent xem chi tiết hội thoại, phản hồi khách hàng, ghi chú nội bộ, chuyển giao khi cần và cập nhật vòng đời ticket an toàn không xung đột. |
| **Tiền điều kiện** | Agent/Supervisor đã đăng nhập, có quyền truy cập ticket; ticket chưa ở trạng thái `CLOSED` hoặc `CANCELLED`. |
| **Sự kiện kích hoạt** | Agent mở một ticket từ danh sách được phân công hoặc hàng đợi nhóm. |
| **Luồng chính** | 1. Agent mở chi tiết ticket; hệ thống kiểm tra quyền truy cập và tải thông tin khách hàng, lịch sử hội thoại (phân tách rõ `PUBLIC` và `INTERNAL`), đồng hồ SLA hiện tại và `ticket.version`.<br>2. Agent đọc yêu cầu; tùy chọn tra cứu KB hoặc yêu cầu gợi ý AI RAG (mở rộng sang **UC07**).<br>3. Agent soạn phản hồi công khai (`visibility = 'PUBLIC'`, `direction = 'OUTBOUND'`), đính kèm tệp (nếu có), chọn trạng thái đích hợp lệ (VD: `IN_PROGRESS`, `WAITING_CUSTOMER` hoặc `RESOLVED` kèm `resolution_code`) và bấm **Gửi & Cập nhật**.<br>4. Hệ thống kiểm tra quyền, kiểm tra bước chuyển trạng thái theo State Machine Guard và kiểm tra `ticket.version` (Optimistic Locking).<br>5. Hệ thống lưu `ticket_message` (`delivery_status = 'PENDING'`) và đẩy tin qua `ChannelAdapter`.<br>6. Khi gửi kênh thành công (`delivery_status = 'SENT'/'DELIVERED'`): cập nhật trạng thái ticket, tăng `version`, ghi `ticket_event`, và phát sự kiện cập nhật SLA cho **UC05** (hoàn thành `FIRST_RESPONSE` nếu là phản hồi đầu tiên, tạm dừng/hoàn tất `RESOLUTION` tùy trạng thái).<br>7. Nếu chuyển sang `RESOLVED`: tạo bản ghi `satisfaction_rating` (`sent_at = NOW()`) và gửi liên kết khảo sát CSAT cho khách hàng. |
| **Luồng thay thế / Ngoại lệ** | - **A1 — Thêm ghi chú nội bộ (`INTERNAL`):** Agent chọn tab *Ghi chú nội bộ* → hệ thống lưu `ticket_message(visibility = 'INTERNAL', direction = 'INTERNAL')`. Tuyệt đối không đẩy ra kênh khách, không trả về API khách và không tính `FIRST_RESPONSE` SLA.<br>- **A2 — Chuyển giao ticket (Transfer/Reassign):** Agent/Supervisor chọn nhóm/Agent đích (bắt buộc thuộc `team_member`) + lý do → cập nhật phân công, giữ nguyên đồng hồ SLA, ghi `assignment_history` và `ticket_event`.<br>- **A3 — Hủy ticket (`CANCELLED`):** Nhập `cancel_reason` ≥ 10 ký tự → hủy SLA, không gửi CSAT.<br>- **E1 — Lỗi truyền tải kênh (`FAILED`):** Lưu `ticket_message` với `delivery_status = 'FAILED'`, hiển thị nút **Thử lại (Retry)**; **KHÔNG ghi nhận `FIRST_RESPONSE` SLA đã đạt**.<br>- **E2 — Xung đột sửa đồng thời:** `ticket.version` không khớp → trả `409 Conflict`, yêu cầu tải lại trạng thái mới nhất.<br>- **E3 — Chuyển trạng thái sai quy trình:** Từ chối `422 INVALID_STATE_TRANSITION`.<br>- **E4 — Mất quyền truy cập:** Trả `403 Forbidden`. |
| **Hậu điều kiện** | Hội thoại, trạng thái, `version`, `ticket_event` và đồng hồ SLA được cập nhật đồng bộ. |
| **Quy tắc nghiệp vụ** | Không lộ `INTERNAL` ra ngoài; gửi thất bại không tính SLA; mọi thay đổi đều lưu vết kiểm toán. |

---

### UC04 — AI phân loại và định tuyến ticket
| Thuộc tính | Nội dung |
|---|---|
| **Actor chính / phụ** | Chính: Supervisor (phân công thủ công / xử lý ngoại lệ). Tác vụ tự động kích hoạt bởi sự kiện `TicketCreated` từ UC02; Agent nhận thông báo khi được phân công. |
| **Mục tiêu** | Tự động phân loại danh mục bằng AI, chọn nhóm theo `routing_rule` và phân công Agent ít tải nhất; chuyển hàng chờ Supervisor khi AI không chắc chắn hoặc không có Agent khả dụng. |
| **Tiền điều kiện** | Ticket tồn tại ở trạng thái `NEW`; có danh mục và `routing_rule` đang hoạt động. |
| **Sự kiện kích hoạt** | Sự kiện `TicketCreated` từ UC02 (tự động) hoặc Supervisor mở danh sách chờ phân công (thủ công). |
| **Luồng chính (Tự động)** | 1. Nhận `TicketCreated`; kiểm tra chống xử lý trùng.<br>2. Gọi `AI Classifier` với tiêu đề/nội dung công khai → nhận `suggested_category_id`, `confidence`, `model_version`; lưu `classification_result`.<br>3. Đối chiếu `routing_rule` đang hoạt động của danh mục: kiểm tra `confidence >= confidence_threshold` và `auto_assign_enabled = TRUE`.<br>4. Tìm Agent thuộc `team_member` của nhóm đích thỏa `is_active = TRUE` và `is_available = TRUE`.<br>5. Áp dụng chiến lược cân bằng tải (`Strategy`): chọn Agent có ít ticket đang mở (`NEW`, `ASSIGNED`, `IN_PROGRESS`, `WAITING_CUSTOMER`) nhất; nếu hòa thì chọn người lâu nhất chưa được tự động giao; hòa tiếp thì theo `app_user.id` tăng dần.<br>6. Trong transaction: kiểm tra `ticket.version`, cập nhật `category_id`, `team_id`, `assigned_agent_id`, chuyển `status = 'ASSIGNED'`, tăng `version`, lưu `assignment_history(AUTO)` và `notification(PENDING)` cho Agent.<br>7. Worker gửi thông báo cho Agent (lỗi gửi không hủy kết quả phân công). |
| **Luồng thay thế / Ngoại lệ** | - **A1 — Phân công thủ công (Supervisor):** Supervisor xem đề xuất AI, chọn danh mục/nhóm/Agent hợp lệ (thuộc `team_member`) và nhập lý do → kiểm tra `version`, cập nhật phân công, lưu `assignment_history(MANUAL, assigned_by = supervisor_id)` và `notification`.<br>- **E1 — AI lỗi/timeout, điểm tin cậy thấp, thiếu nội dung hoặc không có Agent sẵn sàng:** Giữ ticket ở trạng thái `NEW` (hoặc gán `team_id` nếu đã rõ nhóm), lưu `classification_result(REVIEW_REQUIRED / FAILED)` kèm `failure_reason` và đưa vào hàng chờ Supervisor.<br>- **E2 — Ticket đã được sửa thủ công trước khi worker AI xong:** `version` đã thay đổi → bỏ qua kết quả tự động, không ghi đè. |
| **Hậu điều kiện** | Ticket được `ASSIGNED` kèm lịch sử + thông báo, HOẶC giữ nguyên `NEW` có lý do rõ ràng trong hàng chờ Supervisor. |
| **Quy tắc nghiệp vụ** | AI chỉ đề xuất danh mục; việc chọn nhóm/Agent tuân theo luật nghiệp vụ minh bạch; `assigned_agent_id` bắt buộc thuộc `team_member(team_id)`. |

---

### UC05 — Theo dõi SLA, cảnh báo và chuyển cấp
| Thuộc tính | Nội dung |
|---|---|
| **Actor chính / phụ** | Chính: Supervisor (nhận chuyển cấp, ghi nhận xử lý). Phụ: Agent (xem tiến độ, nhận cảnh báo). Bộ lập lịch nội bộ kiểm tra định kỳ (đề xuất mỗi 1 phút). |
| **Mục tiêu** | Khởi tạo và theo dõi 2 mốc SLA (`FIRST_RESPONSE`, `RESOLUTION`), tạm dừng/tiếp tục đúng quy tắc, phát cảnh báo ở ngưỡng 80% và chuyển cấp khi quá hạn mà không tạo cảnh báo trùng. |
| **Tiền điều kiện** | Chính sách `sla_policy` đang hoạt động cho `priority` của ticket có đủ 2 `sla_policy_target` (`FIRST_RESPONSE` và `RESOLUTION`). |
| **Sự kiện kích hoạt** | `TicketCreated` (khởi tạo), sự kiện phản hồi/đổi trạng thái từ UC03, hoặc chu kỳ kiểm tra định kỳ của Scheduler. |
| **Luồng chính** | 1. **Khởi tạo:** Khi nhận `TicketCreated`, lấy `sla_policy` đang hoạt động theo `ticket.priority`, sao chép snapshot `target_seconds` và `warning_ratio` (mặc định 0.8) vào 2 bản ghi `ticket_sla` (`FIRST_RESPONSE`, `RESOLUTION`) với `started_at = ticket.created_at`, `deadline_at = started_at + target_seconds`.<br>2. **Cập nhật theo UC03:**<br>   - Phản hồi công khai hợp lệ đầu tiên gửi thành công → hoàn tất `FIRST_RESPONSE` (`completed_at`, kiểm tra nếu `completed_at > deadline_at` thì vẫn ghi nhận `breached_at` & `sla_event(BREACH)`).<br>   - Ticket sang `WAITING_CUSTOMER` → tạm dừng `RESOLUTION` (`PAUSED`), tạo `sla_pause(WAITING_CUSTOMER)`. Khi khách trả lời (`IN_PROGRESS`) → đóng `sla_pause`, cộng khoảng pause vào `deadline_at`, chuyển lại `RUNNING`.<br>   - Ticket sang `RESOLVED` → hoàn tất `RESOLUTION`.<br>3. **Kiểm tra định kỳ:** Quét các `ticket_sla` đang `RUNNING`:<br>   - Nếu `elapsed >= target_seconds * warning_ratio` và chưa có `WARNING` → chèn `sla_event(WARNING)` (`UNIQUE(ticket_sla_id, event_type)` chống trùng) và tạo `notification` cho Agent (hoặc Supervisor nếu chưa có Agent).<br>   - Nếu `NOW() > deadline_at` và chưa có `BREACH` → cập nhật `breached_at = deadline_at`, chèn `sla_event(BREACH)` và gửi `notification` chuyển cấp cho Supervisor + Agent.<br>4. **Xử lý chuyển cấp:** Supervisor xem chi tiết SLA, can thiệp nghiệp vụ và ghi nhận hành động vào `escalation_action`. |
| **Luồng thay thế / Ngoại lệ** | - **A1 — Mở lại ticket (`RESOLVED → IN_PROGRESS`):** Chỉ mở lại `RESOLUTION` (`completed_at = NULL`), thêm `sla_pause(RESOLVED_UNTIL_REOPEN)`, dời `deadline_at` đúng khoảng chờ mở lại; **không reset `FIRST_RESPONSE`**, giữ nguyên `breached_at` cũ.<br>- **A2 — Hủy ticket (`CANCELLED`):** Chuyển các mốc chưa hoàn thành sang `CANCELLED`, không tính đạt.<br>- **E1 — Kiểm tra lần đầu đã quá hạn:** Ưu tiên tạo `BREACH`, bỏ qua `WARNING` đã mất ý nghĩa.<br>- **E2 — Worker chạy lặp / ticket đổi trạng thái sát giờ:** Ràng buộc `UNIQUE(ticket_sla_id, event_type)` và khóa bản ghi transaction ngăn tạo cảnh báo trùng. |
| **Hậu điều kiện** | Trạng thái SLA, các khoảng pause, cảnh báo/vi phạm và hành động chuyển cấp được lưu vết chính xác, không trùng lặp. |
| **Quy tắc nghiệp vụ** | Chính sách minh họa 24/7: `URGENT` (15p/4h), `HIGH` (1h/8h), `NORMAL` (4h/24h), `LOW` (8h/48h). Đổi Agent không đổi hạn SLA; đổi `sla_policy` tạo version mới, không ảnh hưởng ticket cũ. |

---

### UC06 — Quản lý bài viết Knowledge Base
| Thuộc tính | Nội dung |
|---|---|
| **Actor chính / phụ** | Chính: Agent (soạn/sửa/gửi duyệt), Supervisor (duyệt/xuất bản/từ chối/thu hồi). Phụ: Admin. |
| **Mục tiêu** | Quản trị vòng đời bài viết KB theo phiên bản, đảm bảo chỉ nội dung đã duyệt (`PUBLISHED`) và đúng phạm vi truy cập mới được lập chỉ mục cho tìm kiếm và AI RAG. |
| **Tiền điều kiện** | Người dùng đã đăng nhập với vai trò `AGENT` hoặc `SUPERVISOR`; danh mục KB tồn tại. |
| **Sự kiện kích hoạt** | Agent tạo bài mới / phiên bản mới, hoặc Supervisor mở hàng đợi duyệt / thu hồi bài. |
| **Luồng chính** | 1. Agent tạo bài `kb_article` (hoặc tạo `version_number` mới) với tiêu đề, danh mục, `access_scope` (`PUBLIC`/`INTERNAL_AGENT`), cờ `allow_auto_reply` và nội dung → lưu `kb_version(DRAFT)`.<br>2. Agent bấm **Gửi duyệt** → chuyển `IN_REVIEW`, thông báo Supervisor.<br>3. Supervisor kiểm tra nội dung và chọn **Phê duyệt & Xuất bản**.<br>4. Trong transaction: chuyển phiên bản mới sang `PUBLISHED` (`reviewed_by`, `published_at`), cập nhật `kb_article.current_published_version_id`; nếu có bản `PUBLISHED` cũ thì chuyển bản cũ sang `ARCHIVED` và đặt `kb_chunk.is_active = FALSE` cho bản cũ.<br>5. Bộ lập chỉ mục cắt đoạn (`kb_chunk`), tạo chỉ mục tìm kiếm/vector và đánh dấu `is_active = TRUE`. |
| **Luồng thay thế / Ngoại lệ** | - **A1 — Từ chối duyệt:** Supervisor nhập `review_note` bắt buộc → chuyển `REJECTED`, thông báo Agent sửa lại.<br>- **A2 — Thu hồi bài viết:** Supervisor chọn **Thu hồi** → chuyển `ARCHIVED`, đặt `current_published_version_id = NULL`, vô hiệu hóa toàn bộ `kb_chunk(is_active = FALSE)` để ngừng truy xuất RAG ngay lập tức.<br>- **E1 — Thiếu nội dung:** Trả `422`.<br>- **E2 — Agent gọi API duyệt/xuất bản/thu hồi:** Từ chối `403 Forbidden`. |
| **Hậu điều kiện** | Mỗi bài viết có tối đa 1 phiên bản `PUBLISHED` (`one_published_version_per_article`); chỉ mục RAG luôn phản ánh đúng bài đang có hiệu lực. |
| **Quy tắc nghiệp vụ** | Không sửa đè phiên bản đã xuất bản; bài bị thu hồi không còn được dùng để tự trả lời hay gợi ý. |

---

### UC07 — AI RAG gợi ý câu trả lời có dẫn nguồn
| Thuộc tính | Nội dung |
|---|---|
| **Actor chính / phụ** | Chính: Agent. Phụ: Hệ thống tự động kích hoạt bất đồng bộ cho ticket mới (nếu bật). |
| **Mục tiêu** | Truy xuất đoạn KB còn hiệu lực, sinh câu trả lời nháp có dẫn nguồn cụ thể cho Agent xem/sửa/gửi, hoặc fallback an toàn sang Agent khi thiếu căn cứ. |
| **Tiền điều kiện** | Ticket tồn tại và chưa `CLOSED`/`CANCELLED`; Agent có quyền xem ticket. |
| **Sự kiện kích hoạt** | Agent bấm **Tạo gợi ý AI** trên màn hình UC03, hoặc hệ thống tự chạy sau UC04. |
| **Luồng chính** | 1. Hệ thống kiểm tra quyền đọc ticket của Agent.<br>2. Tạo ngữ cảnh truy vấn từ tiêu đề và **chỉ các tin nhắn `PUBLIC`** (**tuyệt đối loại bỏ `visibility = 'INTERNAL'`**).<br>3. `KnowledgeRetriever` tìm các `kb_chunk` có `is_active = TRUE` và `kb_version.status = 'PUBLISHED'` theo đúng `access_scope`.<br>4. `RAG Engine` sinh câu trả lời nháp kèm danh sách `kb_chunk_id` trích dẫn (có lớp bảo vệ chống prompt injection).<br>5. `Citation Validator` xác minh mọi trích dẫn đều tồn tại, còn hiệu lực và thuộc tập kết quả truy xuất.<br>6. Lưu `ai_run(SUGGESTED)` và các bản ghi `ai_citation`.<br>7. Hiển thị gợi ý + nguồn cho Agent. Agent chọn Chấp nhận / Chỉnh sửa gửi (sang UC03) / Từ chối → lưu `ai_suggestion_feedback`. |
| **Luồng thay thế / Ngoại lệ** | - **A1 — Tự động trả lời (`AUTO_REPLY`):** Chỉ chạy khi toàn bộ nguồn có `access_scope = 'PUBLIC'`, `allow_auto_reply = TRUE`, độ tin cậy đạt ngưỡng và câu hỏi thuộc phạm vi cho phép → lưu `ai_run(AUTO_SENT)` và gửi khách kèm nguồn.<br>- **E1 — Không tìm thấy nguồn KB / Nguồn mâu thuẫn / Hết hiệu lực / Trích dẫn sai:** Lưu `ai_run(FALLBACK_TO_AGENT)` kèm `fallback_reason`, không bịa câu trả lời, chuyển Agent tự xử lý.<br>- **E2 — Khách yêu cầu nhân viên:** Lưu `CUSTOMER_REQUESTED_HUMAN`, đưa vào hàng đợi Agent.<br>- **E3 — AI lỗi / Timeout:** Lưu `ai_run(FAILED, AI_SERVICE_ERROR)`, không chặn luồng xử lý thủ công của Agent. |
| **Hậu điều kiện** | Mọi lần chạy AI đều lưu vết trong `ai_run` và `ai_citation`; trạng thái ticket độc lập với trạng thái tác vụ AI. |
| **Quy tắc nghiệp vụ** | Không có nguồn `PUBLISHED` hợp lệ thì không sinh câu trả lời; không rò rỉ ghi chú nội bộ vào RAG. |

---

### UC08 — Xem báo cáo ticket, SLA và CSAT
| Thuộc tính | Nội dung |
|---|---|
| **Actor chính / phụ** | Chính: Supervisor, Admin. |
| **Mục tiêu** | Xem báo cáo thống kê nhất quán trong một kỳ (`days` ngày) gồm 3 khối bắt buộc: Ticket (UC08.1), SLA (UC08.2), CSAT (UC08.3), và tùy chọn mở chi tiết ticket từ dòng cảnh báo (UC08.4). |
| **Tiền điều kiện** | Người dùng đã đăng nhập với vai trò `SUPERVISOR` hoặc `ADMIN`. |
| **Sự kiện kích hoạt** | Supervisor/Admin mở trang Báo cáo hoặc đổi kỳ thống kê (`days` ∈ [1..365], mặc định 30). |
| **Luồng chính** | 1. Hệ thống xác thực phiên (`401`), vai trò (`403`) và tham số `days` (`422`); chốt một mốc thời gian đọc nhất quán `asOf` và `from = asOf - days × 24h` (hiển thị theo `Asia/Ho_Chi_Minh`).<br>2. Truy vấn tập ticket tạo trong `[from, asOf]` trên `v_ticket_sla_summary` và `satisfaction_rating` (Supervisor chỉ xem phạm vi nhóm được phân quyền; Admin xem toàn hệ thống).<br>3. Truy vấn tối đa 30 cảnh báo gần nhất từ `sla_event` theo `sla_event.created_at` trong kỳ.<br>4. Tổng hợp 3 khối KPI:<br>   - **Ticket:** Tổng ticket; Đang mở (`status NOT IN ('RESOLVED', 'CLOSED', 'CANCELLED')`); phân bố theo trạng thái và danh mục.<br>   - **SLA:** Thời gian phản hồi đầu tiên trung bình (`AVG(first_response_at - created_at)`); Thời gian giải quyết ròng trung bình (`AVG(resolved_at - created_at - resolution_paused_seconds)`); Tỷ lệ tuân thủ SLA (`100 × số mốc đúng hạn / số mốc đủ điều kiện`, loại trừ mốc `CANCELLED`); Số ticket đang quá hạn (mỗi ticket đếm tối đa 1 lần); danh sách 30 cảnh báo gần nhất.<br>   - **CSAT:** Điểm trung bình `AVG(score)` trên thang 1–5 và `COUNT(*)` lượt đã nộp (`score IS NOT NULL`).<br>5. Hiển thị báo cáo (0 cho bộ đếm, `—` cho KPI không có mẫu).<br>6. *(Tùy chọn — UC08.4)* Nếu người dùng chọn 1 ticket từ bảng cảnh báo, hệ thống kiểm tra lại quyền đọc ticket rồi điều hướng sang chi tiết UC03. |
| **Luồng thay thế / Ngoại lệ** | - **E1 — Chưa đăng nhập / Sai vai trò / Sai tham số:** Trả `401` / `403` / `422`.<br>- **E2 — Không có dữ liệu trong kỳ:** Vẫn là kết quả thành công, hiển thị 0 và `—` (không hiển thị lỗi).<br>- **E3 — Lỗi đọc DB / Mạng:** Giữ nguyên kỳ và tab, hiển thị thông báo lỗi kèm nút **Thử lại**; không ghi đè bằng phản hồi đến muộn của yêu cầu cũ.<br>- **E4 — Mở ticket không có quyền / không tồn tại:** Trả `403/404`, giữ ngữ cảnh trang báo cáo. |
| **Hậu điều kiện** | Ba khối KPI Ticket, SLA, CSAT hiển thị nhất quán trên cùng mốc `asOf`. |
| **Quy tắc nghiệp vụ** | Loại bỏ `CANCELLED` khỏi chỉ số "Ticket đang mở" và mẫu số tuân thủ SLA; phân biệt rõ phản hồi của AI vs Agent khi cần báo cáo chi tiết. |
