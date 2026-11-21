# THIẾT KẾ USE CASE — THÀNH VIÊN 5 (UC08)

**Đề tài 07:** Nền tảng Helpdesk đa kênh tích hợp AI  
**Phân hệ:** Báo cáo và Thống kê (Reporting & Analytics)  
**Phụ trách:** Thành viên 5 (TV5)  
**Trạng thái:** Đã chuẩn hóa theo bản hợp nhất toàn hệ thống (MySQL 8.0+, 7 trạng thái Ticket, đồng bộ SLA Pause & View `v_ticket_sla_summary`)

---

## 1. Phạm vi, Actor và các điểm đã chuẩn hóa

- **UC08 — Xem báo cáo ticket, SLA và CSAT:** Cung cấp báo cáo tổng hợp nhất quán trong một kỳ (`days` ngày) cho Supervisor và Admin, gồm 3 khối thống kê bắt buộc (Ticket, SLA, CSAT) và thao tác mở rộng xem chi tiết ticket từ dòng cảnh báo SLA.

### Các điểm đã cập nhật so với bản TV5 cũ để đồng bộ toàn hệ thống
1. **Hệ quản trị CSDL:** Chuyển từ SQLite (Unix epoch `REAL`) sang **MySQL 8.0+ (`DATETIME(6)` UTC, hiển thị `Asia/Ho_Chi_Minh`)**.
2. **Đồng bộ schema SLA & Nhóm:** Bỏ giả định bảng `TICKET_SLA` 1-1 và `USERS.team_id` 1-N; sử dụng View **`v_ticket_sla_summary`**, bảng **`ticket_sla`** (2 dòng/ticket), **`sla_event`** (thay cho `SLA_ALERTS`), **`satisfaction_rating`** (thay cho `RATINGS`) và **`team_member`** (N-N).
3. **Đồng bộ 7 trạng thái & SLA Pause:**
   - **Ticket đang mở:** sửa công thức thành `status NOT IN ('RESOLVED', 'CLOSED', 'CANCELLED')` (loại bỏ ticket spam/hủy).
   - **Thời gian giải quyết trung bình:** dùng thời gian ròng `TIMESTAMPDIFF(SECOND, created_at, resolved_at) - resolution_paused_seconds` để khớp với cơ chế tạm dừng SLA khi `WAITING_CUSTOMER` của UC03 & UC05.
   - **Tuân thủ SLA:** loại bỏ các mốc có `tracking_status = 'CANCELLED'` khỏi mẫu số.

### Actor & Phân rã Use Case
- **Actor chính:** Supervisor (xem phạm vi nhóm quản lý), Admin (xem toàn hệ thống). Agent và Customer không có quyền (`403 Forbidden`).
- **Quan hệ UML:**
  - `UC08` `«include»` `UC08.1: Xem thống kê ticket`, `UC08.2: Xem thống kê SLA`, `UC08.3: Xem thống kê CSAT`.
  - `UC08.4: Mở ticket từ dòng cảnh báo` `«extend»` `UC08` (điều hướng sang chi tiết UC03 sau khi kiểm tra lại quyền đọc ticket).
- **Biểu đồ tham chiếu:** `../Bieu_do/UseCase/Uc08.png`, `../Bieu_do/UC08_Chi_tiet/bieu do hoat dong.png`, `../Bieu_do/SSD/bieudossd.png`, `../Bieu_do/Tong_the/UseCase_Tong_The.png`, `../Bieu_do/Tong_the/ERD_Tong_The.png`.

---

## 2. Đặc tả chi tiết UC08 — Xem báo cáo ticket, SLA và CSAT

| Thuộc tính | Nội dung |
|---|---|
| **Mã / Tên Use Case** | UC08 — Xem báo cáo ticket, SLA và CSAT |
| **Actor chính / phụ** | Chính: Supervisor, Admin. |
| **Mục tiêu** | Theo dõi hiệu suất xử lý ticket, mức độ tuân thủ SLA, danh sách cảnh báo mới nhất và điểm hài lòng khách hàng (CSAT) trên một mốc dữ liệu nhất quán. |
| **Tiền điều kiện** | Người dùng đã đăng nhập với phiên hợp lệ và có vai trò `SUPERVISOR` hoặc `ADMIN` (kiểm tra lại ở tầng backend mỗi lần gọi). |
| **Sự kiện kích hoạt** | Supervisor/Admin mở màn hình Báo cáo hoặc thay đổi bộ lọc kỳ (`days` ∈ [1..365], mặc định 30; chọn nhanh 7 / 30 / 90 ngày). |
| **Luồng chính** | 1. Người dùng mở trang báo cáo hoặc chọn kỳ `days`.<br>2. Hệ thống xác thực phiên (`401`), kiểm tra vai trò `SUPERVISOR`/`ADMIN` (`403`) và kiểm tra `days` là số nguyên trong `[1, 365]` (`422`).<br>3. Hệ thống chốt 1 mốc thời gian đọc nhất quán `asOf` (UTC) và tính `from = asOf - days × 24 giờ`.<br>4. Hệ thống truy vấn trên MySQL 8.0 (lọc phạm vi nhóm nếu là Supervisor):<br>   - Tập ticket tạo trong `[from, asOf]` từ `v_ticket_sla_summary` để tính khối Ticket & thời gian trung bình.<br>   - Các mốc SLA đủ điều kiện từ `ticket_sla` để tính tỷ lệ tuân thủ SLA.<br>   - Các đánh giá đã nộp (`score IS NOT NULL`) gắn với tập ticket trong kỳ từ `satisfaction_rating` để tính CSAT.<br>   - Tối đa **30 cảnh báo gần nhất** từ `sla_event` theo `sla_event.created_at` trong kỳ (độc lập với ngày tạo ticket).<br>5. Hệ thống tổng hợp `ReportSummary` và hiển thị đồng thời 3 khối KPI (chuyển mốc ngày giờ sang `Asia/Ho_Chi_Minh`).<br>6. *(Tùy chọn — UC08.4)* Nếu người dùng bấm vào một ticket từ bảng cảnh báo, hệ thống gọi kiểm tra quyền đọc ticket (`authorizeTicketAccess`) rồi mở màn hình chi tiết UC03. |
| **Luồng thay thế / Ngoại lệ** | - **E1 — Hết phiên / Chưa đăng nhập:** Trả `401`, chuyển hướng đăng nhập.<br>- **E2 — Sai vai trò (Agent/Customer):** Trả `403`, hiển thị màn hình từ chối truy cập.<br>- **E3 — `days` ngoài miền `[1, 365]`:** Trả `422`, giữ nguyên bộ lọc để sửa.<br>- **E4 — Không có dữ liệu trong kỳ:** Vẫn là kết quả **thành công**, hiển thị `0` cho bộ đếm và `—` (`null`) cho KPI trung bình/tỷ lệ/CSAT (không báo lỗi).<br>- **E5 — Lỗi đọc DB / Mạng:** Giữ kỳ và tab hiện tại, hiển thị nút **Thử lại**; không trình bày dữ liệu nửa vời; bỏ qua phản hồi đến muộn của yêu cầu cũ.<br>- **E6 — Chọn ticket không có quyền / không tồn tại (UC08.4):** Trả `403/404`, giữ nguyên ngữ cảnh trang báo cáo, không làm lộ nội dung ticket. |
| **Hậu điều kiện** | Ba khối Ticket, SLA và CSAT được hiển thị nhất quán trên cùng mốc `asOf`. |
| **Quy tắc nghiệp vụ** | - Tỷ lệ tuân thủ SLA tính theo **số mốc** (mỗi ticket tối đa 2 mốc), còn "Đang quá hạn" tính theo **số ticket** (mỗi ticket chỉ đếm 1 lần dù quá hạn cả 2 mốc).<br>- CSAT là **điểm trung bình** trên thang 1–5 kèm số lượt, phân biệt rõ `null` (0 lượt) với điểm số. |

---

## 3. Công thức KPI & Truy vấn chuẩn MySQL 8.0

| Chỉ số | Định nghĩa chuẩn hóa | Khi không có mẫu |
|---|---|---|
| **Tổng ticket** | Số ticket có `created_at` ∈ `[from, asOf]` | `0` |
| **Ticket đang mở** | Số ticket trong kỳ có `status NOT IN ('RESOLVED', 'CLOSED', 'CANCELLED')` | `0` |
| **Phản hồi đầu tiên TB** | `AVG(TIMESTAMPDIFF(SECOND, created_at, first_response_at))` với `first_response_at IS NOT NULL` | `null` (`—`) |
| **Giải quyết ròng TB** | `AVG(TIMESTAMPDIFF(SECOND, created_at, resolved_at) - resolution_paused_seconds)` với `resolved_at IS NOT NULL` | `null` (`—`) |
| **Tuân thủ SLA (%)** | `100 × số mốc (completed_at <= deadline_at) / số mốc đủ điều kiện (tracking_status <> 'CANCELLED' AND (completed_at IS NOT NULL OR deadline_at <= asOf))` | `null` (`—`) |
| **Ticket đang quá hạn** | Số ticket (`status <> 'CANCELLED'`) có ít nhất 1 mốc `RUNNING` và `deadline_at < asOf` (mỗi ticket đếm 1 lần) | `0` |
| **CSAT** | `AVG(score)` và `COUNT(score)` từ `satisfaction_rating` của tập ticket trong kỳ | `null` và `0` lượt |

### Bảng & View MySQL 8.0 sử dụng (trích từ `helpdesk_unified_schema_mysql.sql`)
- `v_ticket_sla_summary`, `ticket`, `ticket_sla`, `sla_event`, `satisfaction_rating`, `category`, `support_team`, `team_member`.
- Chi tiết 3 câu lệnh SQL chuẩn MySQL 8.0 xem tại mục 4.2 của [02_Thiet_Ke_Database_Tong_Hop.md](../02_Thiet_Ke_Database_Tong_Hop.md).

### Tiêu chí kiểm thử trọng tâm
1. Agent/Customer gọi `GET /api/v1/reports/summary` → trả `403 Forbidden`.
2. Ticket trạng thái `CANCELLED` → không được tính vào "Ticket đang mở" và không nằm trong mẫu số "Tuân thủ SLA".
3. Ticket từng tạm dừng 2 giờ ở `WAITING_CUSTOMER` → "Giải quyết ròng TB" trừ đúng 7200 giây.
4. Kỳ không có dữ liệu → trả `200 OK` với bộ đếm `0` và các chỉ số TB/tỷ lệ/CSAT là `null`.
5. Ticket quá hạn cả `FIRST_RESPONSE` lẫn `RESOLUTION` → chỉ đếm `1` trong "Ticket đang quá hạn".
