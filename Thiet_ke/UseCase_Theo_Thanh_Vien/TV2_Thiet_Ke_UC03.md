# THIẾT KẾ USE CASE — THÀNH VIÊN 2 (UC03)

**Đề tài 07:** Nền tảng Helpdesk đa kênh tích hợp AI  
**Phân hệ:** Quản lý và xử lý Ticket (Ticket Management)  
**Phụ trách:** Thành viên 2 (TV2)  
**Trạng thái:** Đã chuẩn hóa theo bản hợp nhất toàn hệ thống (MySQL 8.0+, 7 trạng thái Ticket, đã xử lý các xung đột & lỗi đặc tả cũ)

---

## 1. Phạm vi, Actor và các điểm đã chuẩn hóa

- **UC03 — Xử lý và cập nhật trạng thái ticket:** Bao phủ toàn bộ nghiệp vụ xử lý hằng ngày của Agent/Supervisor: xem ngữ cảnh hội thoại đa kênh, tham khảo gợi ý AI RAG (UC07), phản hồi khách hàng, ghi chú nội bộ, chuyển giao ticket, xử lý lỗi truyền tải kênh, cập nhật vòng đời ticket an toàn và kích hoạt khảo sát CSAT.

### Các điểm đã cập nhật/sửa lỗi so với bản TV2 cũ
1. **Trạng thái Ticket:** Chuẩn hóa từ 9 trạng thái về **7 trạng thái thống nhất**: đổi `PENDING_CUSTOMER` → `WAITING_CUSTOMER`; bỏ trạng thái riêng `REOPENED` (mở lại chuyển `RESOLVED → IN_PROGRESS` + ghi `ticket_event(REOPENED)`) và `PENDING_INTERNAL` (phối hợp nội bộ dùng ghi chú `visibility = 'INTERNAL'`).
2. **Mức độ ưu tiên:** Đổi `MEDIUM` → `NORMAL` (`URGENT`, `HIGH`, `NORMAL`, `LOW`).
3. **Database (MySQL 8.0+):** Đổi PK từ `UUID` sang `BIGINT AUTO_INCREMENT`; loại bỏ 3 cột SLA nhúng thừa trong `ticket` (đọc từ `ticket_sla` của UC05); bổ sung bắt buộc **`ticket.version`** (Optimistic Locking); đổi `ticket_audit_logs` → `ticket_event` và `ticket_csat_surveys` → `satisfaction_rating`.
4. **Quy tắc `FIRST_RESPONSE` SLA:** Bỏ quy tắc tính ngay khi mới mở ticket; chỉ ghi nhận `FIRST_RESPONSE` khi phản hồi công khai hữu ích có `delivery_status IN ('SENT', 'DELIVERED')`.
5. **Sửa lỗi đặc tả & sơ đồ cũ:** Loại bỏ `Helpdesk System` khỏi Actor ngoài; sửa lỗi copy-paste `AIMS System` và lỗi ghi `Khách hàng` thực hiện lưu ghi chú nội bộ; chuyển `Tra cứu KB & Gợi ý RAG` từ `«include»` thành tùy chọn mở rộng sang **UC07**.

### Actor & Phân rã Use Case
- **Actor chính:** Agent. **Actor phụ:** Supervisor (chuyển giao/kiểm tra), Khách hàng (nhận phản hồi, phản hồi thêm, gửi đánh giá CSAT).
- **Quan hệ UML:**
  - `UC03` `«include»` `UC03.1: Xem chi tiết & lịch sử hội thoại`.
  - `UC03.2: Thêm ghi chú nội bộ (INTERNAL)` `«extend»` `UC03`.
  - `UC03.3: Chuyển giao ticket (Transfer / Reassign)` `«extend»` `UC03`.
  - `UC03.4: Xử lý lỗi truyền tải kênh (Retry)` `«extend»` `UC03`.
  - Liên kết tùy chọn sang **UC07** khi Agent bấm *Tạo gợi ý AI RAG*.
- **Biểu đồ tham chiếu:** `../Bieu_do/UseCase/UC_03.png`, `../Bieu_do/SSD/Sequence Diagram_03.png`, `../Bieu_do/Tong_the/Ticket_Lifecycle_Unified.png`.

---

## 2. Đặc tả chi tiết UC03 — Xử lý và cập nhật trạng thái ticket

| Thuộc tính | Nội dung |
|---|---|
| **Mã / Tên Use Case** | UC03 — Xử lý và cập nhật trạng thái ticket |
| **Actor chính / phụ** | Chính: Agent. Phụ: Supervisor, Khách hàng. |
| **Mục tiêu** | Xử lý yêu cầu hỗ trợ, trao đổi chính xác qua đúng kênh khách hàng, ghi chú nội bộ an toàn, cập nhật trạng thái đúng quy trình không xung đột và kích hoạt CSAT khi giải quyết. |
| **Tiền điều kiện** | Agent/Supervisor đã đăng nhập, có quyền truy cập ticket thuộc nhóm/phân công; ticket chưa ở trạng thái `CLOSED` hoặc `CANCELLED`. |
| **Sự kiện kích hoạt** | Agent chọn mở một ticket từ danh sách *My Tickets* hoặc hàng đợi nhóm. |
| **Luồng chính** | 1. Agent mở chi tiết ticket.<br>2. Hệ thống kiểm tra quyền đọc ticket và hiển thị thông tin khách hàng, lịch sử `ticket_message` (phân tách rõ vùng công khai `PUBLIC` và ghi chú nội bộ `INTERNAL`), tiến độ SLA (`ticket_sla`) và `ticket.version` hiện tại.<br>3. Agent đọc yêu cầu; có thể tùy chọn tra cứu KB hoặc yêu cầu gợi ý AI RAG (gọi **UC07**).<br>4. Agent soạn phản hồi công khai (`visibility = 'PUBLIC'`, `direction = 'OUTBOUND'`), đính kèm tệp nếu cần (≤ 25MB), chọn trạng thái đích hợp lệ (VD: `IN_PROGRESS`, `WAITING_CUSTOMER`, hoặc `RESOLVED` kèm `resolution_code`) và bấm **Gửi & Cập nhật**.<br>5. Hệ thống kiểm tra quyền, kiểm tra tính hợp lệ của bước chuyển trạng thái theo **State Machine Guard**, và kiểm tra **`ticket.version`** (Optimistic Locking).<br>6. Hệ thống lưu `ticket_message` (`delivery_status = 'PENDING'`) cùng `attachment`, sau đó gọi `ChannelAdapter` đẩy tin ra kênh gốc (Web/Email/Chat/Social).<br>7. Khi gửi kênh thành công (`delivery_status = 'SENT'` hoặc `'DELIVERED'`): hệ thống cập nhật trạng thái ticket, tăng `ticket.version = version + 1`, ghi `ticket_event`, và phát sự kiện cập nhật SLA sang **UC05** (hoàn tất `FIRST_RESPONSE` nếu là phản hồi đầu tiên; tạm dừng `RESOLUTION` nếu sang `WAITING_CUSTOMER`; hoàn tất `RESOLUTION` nếu sang `RESOLVED`).<br>8. Nếu trạng thái mới là `RESOLVED`: hệ thống tạo bản ghi `satisfaction_rating` (`sent_at = UTC_TIMESTAMP(6)`) và gửi liên kết khảo sát CSAT cho khách hàng. |
| **Luồng thay thế / Ngoại lệ** | - **A1 — Thêm ghi chú nội bộ (UC03.2):** Agent chọn tab *Ghi chú nội bộ*, nhập nội dung (có thể `@mention` đồng nghiệp/Supervisor). Hệ thống lưu `ticket_message(visibility = 'INTERNAL', direction = 'INTERNAL', sender_type = 'AGENT')`. Tuyệt đối **không đẩy ra kênh khách**, không trả về API khách và không tính `FIRST_RESPONSE` SLA.<br>- **A2 — Chuyển giao ticket (UC03.3):** Agent/Supervisor chọn nhóm/Agent đích (bắt buộc tồn tại trong `team_member`) và nhập lý do. Hệ thống kiểm tra `version`, cập nhật `team_id`/`assigned_agent_id`, giữ nguyên đồng hồ SLA, ghi `assignment_history(MANUAL)` và `ticket_event(ASSIGNMENT_CHANGE)`.<br>- **A3 — Hủy ticket spam/trùng (`CANCELLED`):** Agent/Supervisor nhập `cancel_reason` ≥ 10 ký tự → chuyển `CANCELLED`, hủy 2 mốc SLA, không gửi CSAT.<br>- **A4 — Khách phản hồi sau khi `RESOLVED` (trước khi `CLOSED`):** Hệ thống tự chuyển `RESOLVED → IN_PROGRESS`, ghi `ticket_event(REOPENED)`, giữ nguyên Agent cũ và báo **UC05** mở lại `RESOLUTION` SLA (kèm `sla_pause(RESOLVED_UNTIL_REOPEN)`).<br>- **E1 — Gửi kênh thất bại (UC03.4):** `ChannelAdapter` báo lỗi mạng/API → lưu `ticket_message(delivery_status = 'FAILED')`, hiển thị nút **Thử lại (Retry)**; **KHÔNG đánh dấu `FIRST_RESPONSE` SLA đã đạt**.<br>- **E2 — Xung đột cập nhật đồng thời:** Hai Agent cùng sửa một ticket → `version` không khớp → từ chối `409 Conflict`, yêu cầu tải lại.<br>- **E3 — Chuyển trạng thái không hợp lệ:** Từ chối `422 INVALID_STATE_TRANSITION`.<br>- **E4 — Mất quyền truy cập:** Từ chối `403 Forbidden`. |
| **Hậu điều kiện** | Tin nhắn, tệp đính kèm, trạng thái, `version`, `ticket_event` và đồng hồ SLA được cập nhật đồng bộ; ghi chú nội bộ được cách ly hoàn toàn. |
| **Quy tắc nghiệp vụ** | - Ticket đã `CLOSED` hoặc `CANCELLED` là bất biến (không thể chuyển ngược trạng thái).<br>- Mọi phản hồi công khai và ghi chú nội bộ phải phân biệt rõ trên UI để tránh gửi nhầm nội bộ cho khách. |

---

## 3. Hợp đồng API & Dữ liệu (MySQL 8.0+)

### 3.1. Các API trọng tâm của UC03
- `GET /api/v1/tickets` — Lọc hàng đợi theo trạng thái, danh mục, nhóm, Agent (có kiểm tra quyền).
- `GET /api/v1/tickets/{id}` — Chi tiết ticket, hội thoại (API khách hàng luôn lọc `visibility = 'PUBLIC'`), tiến độ SLA và `version`.
- `POST /api/v1/tickets/{id}/messages` — Gửi phản hồi công khai (`PUBLIC`) hoặc ghi chú nội bộ (`INTERNAL`).
- `PATCH /api/v1/tickets/{id}/status` — Chuyển trạng thái (bắt buộc truyền `version`, `resolution_code` khi `RESOLVED`, `cancel_reason` khi `CANCELLED`).
- `PATCH /api/v1/tickets/{id}/assignment` — Chuyển giao nhóm/Agent (kiểm tra `team_member` và `version`).
- `POST /api/v1/tickets/{id}/ratings` — Khách hàng gửi điểm CSAT (1–5) và nhận xét.

### 3.2. Các bảng MySQL sử dụng (trích từ `helpdesk_unified_schema_mysql.sql`)
- `ticket`, `ticket_message`, `attachment`, `ticket_event`, `satisfaction_rating` (và đọc `ticket_sla`, `assignment_history`).

### 3.3. Tiêu chí kiểm thử trọng tâm
1. Khách hàng gọi `GET /tickets/{id}` → tuyệt đối không thấy bản ghi `visibility = 'INTERNAL'` và không đọc được ticket của khách khác (`403`).
2. Hai Agent cùng cập nhật 1 ticket với cùng `version` → chỉ 1 người thành công, người còn lại nhận `409 Conflict`.
3. Mô phỏng `ChannelAdapter` lỗi → tin lưu `FAILED`, có nút Retry và `FIRST_RESPONSE` SLA chưa được hoàn tất.
4. Chuyển trạng thái sai ma trận (VD: `NEW → CLOSED`) → bị chặn `422 INVALID_STATE_TRANSITION`.
5. Hủy ticket với `cancel_reason` < 10 ký tự → bị từ chối bởi ràng buộc nghiệp vụ và `CHECK` của MySQL.
