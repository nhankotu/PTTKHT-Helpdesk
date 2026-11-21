# THIẾT KẾ USE CASE — THÀNH VIÊN 3 (UC04 & UC05)

**Đề tài 07:** Nền tảng Helpdesk đa kênh tích hợp AI  
**Phân hệ:** Phân loại, Định tuyến & Theo dõi SLA (Routing & SLA)  
**Phụ trách:** Thành viên 3 (TV3)  
**Trạng thái:** Đã chuẩn hóa theo bản hợp nhất toàn hệ thống (MySQL 8.0+, 7 trạng thái Ticket)

---

## 1. Phạm vi, Actor và ranh giới hệ thống

- **UC04 — AI phân loại và định tuyến ticket:** Tự động phân loại danh mục bằng AI khi có ticket mới, ánh xạ sang nhóm theo `routing_rule`, chọn Agent ít tải nhất để phân công tự động, hoặc chuyển hàng chờ Supervisor xử lý thủ công khi AI lỗi / độ tin cậy thấp / không có Agent khả dụng.
- **UC05 — Theo dõi SLA, cảnh báo và chuyển cấp:** Khởi tạo và giám sát 2 mốc SLA độc lập (`FIRST_RESPONSE` và `RESOLUTION`), hỗ trợ tạm dừng/tiếp tục đúng quy tắc, cảnh báo khi đạt 80% thời hạn, ghi nhận vi phạm và chuyển cấp cho Supervisor mà không phát trùng cảnh báo.

### Actor & Ranh giới
- **Supervisor:** Actor chính xử lý phân công thủ công (UC04), nhận thông báo chuyển cấp và ghi nhận hành động xử lý vi phạm (UC05).
- **Agent:** Xem tình trạng SLA của ticket được giao, nhận thông báo phân công (UC04) và nhận cảnh báo sắp quá hạn / vi phạm (UC05). *(Lưu ý: chỉ Supervisor thực hiện `Phân công thủ công`, Agent không thực hiện UC này)*.
- **Thành phần nội bộ (KHÔNG phải Actor):** `AI Classifier`, `SLA Scheduler`, `Notification Worker`.
- **Biểu đồ tham chiếu:** `../Bieu_do/UseCase/UC04.png`, `../Bieu_do/UseCase/UC05_UseCase.png`, `../Bieu_do/SSD/UC04_SSD.png`, `../Bieu_do/SSD/UC05_SSD.png`, `../Bieu_do/Tong_the/ERD_Tong_The.png`.

---

## 2. Đặc tả chi tiết UC04 — AI phân loại và định tuyến ticket

| Thuộc tính | Nội dung |
|---|---|
| **Mã / Tên Use Case** | UC04 — AI phân loại và định tuyến ticket |
| **Actor chính / phụ** | Chính: Supervisor (phân công thủ công / xử lý ngoại lệ). Phụ: Agent (nhận thông báo phân công). Luồng tự động kích hoạt bởi `TicketCreated` từ UC02. |
| **Mục tiêu** | Định tuyến ticket nhanh và minh bạch tới đúng nhóm và Agent khả dụng ít tải nhất; bảo đảm không rơi mất ticket khi AI lỗi hoặc thiếu nhân sự. |
| **Tiền điều kiện** | Ticket tồn tại ở trạng thái `NEW`; hệ thống có danh mục (`category`) và luật định tuyến (`routing_rule`) đang hoạt động. |
| **Sự kiện kích hoạt** | Nhận sự kiện `TicketCreated` từ UC02 (tự động), hoặc Supervisor mở danh sách chờ phân công (thủ công). |
| **Luồng chính (Tự động)** | 1. Nhận `TicketCreated` (kiểm tra idempotent chống xử lý lặp).<br>2. Lấy tiêu đề/nội dung công khai, gọi `AI Classifier` → nhận `suggested_category_id`, `confidence`, `model_version`; lưu `classification_result`.<br>3. Tra cứu `routing_rule` đang hoạt động của danh mục (`is_active = TRUE`): kiểm tra `confidence >= confidence_threshold` và `auto_assign_enabled = TRUE`.<br>4. Tìm danh sách Agent thuộc `team_member` của `team_id` thỏa `app_user.is_active = TRUE` và `app_user.is_available = TRUE`.<br>5. Áp dụng chiến lược chọn Agent (`Strategy`): chọn người có ít ticket đang mở (`NEW`, `ASSIGNED`, `IN_PROGRESS`, `WAITING_CUSTOMER`) nhất; hòa thì chọn người lâu nhất chưa được giao tự động; hòa tiếp theo `app_user.id` tăng dần.<br>6. Trong transaction MySQL: kiểm tra `ticket.version`, cập nhật `category_id`, `team_id`, `assigned_agent_id`, chuyển `status = 'ASSIGNED'`, tăng `version`, lưu `assignment_history(assignment_source = 'AUTO', assigned_by = NULL)`, ghi `ticket_event(ASSIGNMENT_CHANGE)` và tạo `notification(PENDING)` cho Agent.<br>7. Worker gửi thông báo cho Agent (lỗi gửi không hủy phân công, worker thử lại cùng bản ghi). |
| **Luồng thay thế / Ngoại lệ** | - **A1 — Phân công thủ công (Supervisor):** Supervisor thuộc phạm vi quản lý xem nội dung ticket + đề xuất AI, chọn danh mục/nhóm/Agent hợp lệ (thuộc `team_member` và `is_active = TRUE`), nhập lý do → kiểm tra `version`, cập nhật phân công, lưu `assignment_history(MANUAL, assigned_by = supervisor_id)` và `notification`.<br>- **E1 — AI lỗi/timeout, thiếu nội dung, điểm tin cậy < ngưỡng hoặc không có Agent khả dụng:** Giữ ticket ở trạng thái `NEW` (vẫn có thể gán `team_id` nếu đã rõ nhóm), lưu `classification_result(REVIEW_REQUIRED / FAILED)` kèm `failure_reason` và đưa vào hàng chờ Supervisor.<br>- **E2 — Ticket đã được sửa/phân công trước khi AI xong:** `version` đã đổi → bỏ qua kết quả tự động, không ghi đè. |
| **Hậu điều kiện** | Ticket được chuyển sang `ASSIGNED` kèm lịch sử + thông báo, HOẶC giữ `NEW` có lý do rõ ràng trong hàng chờ Supervisor. |
| **Quy tắc nghiệp vụ** | - AI chỉ đề xuất danh mục; chọn nhóm/Agent tuân theo luật nghiệp vụ minh bạch.<br>- Khóa ngoại ghép `FOREIGN KEY(team_id, assigned_agent_id) REFERENCES team_member(team_id, user_id)` bảo đảm không thể gán nhầm Agent ngoài nhóm. |

---

## 3. Đặc tả chi tiết UC05 — Theo dõi SLA, cảnh báo và chuyển cấp

| Thuộc tính | Nội dung |
|---|---|
| **Mã / Tên Use Case** | UC05 — Theo dõi SLA, cảnh báo và chuyển cấp |
| **Actor chính / phụ** | Chính: Supervisor (nhận chuyển cấp, ghi nhận xử lý). Phụ: Agent (xem tiến độ, nhận cảnh báo). Bộ lập lịch nội bộ kiểm tra định kỳ (đề xuất 1 phút/lần). |
| **Mục tiêu** | Theo dõi chính xác thời hạn phản hồi đầu tiên và giải quyết, cảnh báo sớm ở mốc 80%, ghi nhận vi phạm và chuyển cấp không trùng lặp. |
| **Tiền điều kiện** | Có `sla_policy` đang hoạt động cho `ticket.priority` với đủ 2 `sla_policy_target` (`FIRST_RESPONSE` và `RESOLUTION`). |
| **Sự kiện kích hoạt** | `TicketCreated` (khởi tạo), sự kiện phản hồi/đổi trạng thái từ UC03, hoặc chu kỳ quét của `SLA Scheduler`. |
| **Luồng chính** | 1. **Khởi tạo:** Khi nhận `TicketCreated`, lấy `sla_policy` đang bật theo `priority`, sao chép snapshot `target_seconds`, `warning_ratio` (0.8) vào 2 dòng `ticket_sla` (`FIRST_RESPONSE`, `RESOLUTION`) với `started_at = ticket.created_at`, `deadline_at = started_at + target_seconds`.<br>2. **Cập nhật theo UC03:**<br>   - Phản hồi công khai hữu ích đầu tiên gửi thành công (`is_auto_ack = FALSE`, `delivery_status IN ('SENT','DELIVERED')`) → hoàn tất `FIRST_RESPONSE` (`completed_at`). Nếu `completed_at > deadline_at` dù scheduler chưa chạy, vẫn ghi nhận `breached_at` & `sla_event(BREACH)`.<br>   - Ticket chuyển `WAITING_CUSTOMER` → tạm dừng `RESOLUTION` (`PAUSED`), tạo `sla_pause(WAITING_CUSTOMER)`. Khi khách trả lời (`IN_PROGRESS`) → đóng `sla_pause`, cộng thời gian pause vào `deadline_at`, chuyển lại `RUNNING`.<br>   - Ticket chuyển `RESOLVED` → hoàn tất `RESOLUTION`.<br>3. **Quét định kỳ (Scheduler):** Với các `ticket_sla` đang `RUNNING`:<br>   - Tính `elapsed = UTC_TIMESTAMP(6) - started_at - total_paused_seconds`.<br>   - Nếu `elapsed >= target_seconds * warning_ratio` và chưa có `WARNING` → chèn `sla_event(WARNING)` và tạo `notification` cho Agent (hoặc Supervisor nếu chưa có Agent).<br>   - Nếu `UTC_TIMESTAMP(6) > deadline_at` và chưa có `BREACH` → cập nhật `breached_at = deadline_at`, chèn `sla_event(BREACH)` và tạo `notification` chuyển cấp cho Supervisor + Agent.<br>4. **Ghi nhận xử lý chuyển cấp:** Supervisor xem chi tiết vi phạm và lưu hành động can thiệp vào `escalation_action`. |
| **Luồng thay thế / Ngoại lệ** | - **A1 — Mở lại ticket (`RESOLVED → IN_PROGRESS`):** Chỉ mở lại `RESOLUTION` (`completed_at = NULL`), tạo `sla_pause(RESOLVED_UNTIL_REOPEN)`, dời `deadline_at` đúng khoảng chờ mở lại; **không reset `FIRST_RESPONSE`**, giữ nguyên `breached_at` cũ.<br>- **A2 — Hủy ticket (`CANCELLED`):** Chuyển các mốc chưa hoàn thành sang `CANCELLED` (không tính đạt).<br>- **E1 — Lần quét đầu tiên đã quá hạn:** Ưu tiên tạo `BREACH`, không tạo thêm `WARNING` đã quá thời điểm.<br>- **E2 — Scheduler chạy lặp / race condition:** Chống trùng tuyệt đối bằng `UNIQUE(ticket_sla_id, event_type)` trên `sla_event` và khóa dòng (`SELECT ... FOR UPDATE`). |
| **Hậu điều kiện** | Toàn bộ tiến độ SLA, khoảng pause, cảnh báo/vi phạm và hành động chuyển cấp được lưu vết chính xác, phục vụ trực tiếp cho báo cáo UC08. |
| **Quy tắc nghiệp vụ** | - Chính sách minh họa 24/7: `URGENT` (15p / 4h), `HIGH` (1h / 8h), `NORMAL` (4h / 24h), `LOW` (8h / 48h), cảnh báo 80%.<br>- Đổi Agent không kéo dài SLA; sửa chính sách tạo `sla_policy.version` mới, không ảnh hưởng ticket cũ.<br>- Đúng hạn (`completed_at <= deadline_at`) vẫn tính là đạt. |

---

## 4. Dữ liệu MySQL 8.0+ & Tiêu chí kiểm thử

### 4.1. Các bảng MySQL sử dụng (trích từ `helpdesk_unified_schema_mysql.sql`)
- `routing_rule` (có cột sinh `active_category_key` + `UNIQUE` thay Partial Unique Index)
- `classification_result`, `assignment_history`
- `sla_policy` (có `active_priority_key` + `UNIQUE`), `sla_policy_target`, `ticket_sla`
- `sla_pause` (có `open_pause_key` + `UNIQUE`), `sla_event`, `escalation_action`, `notification`
- View `v_ticket_sla_summary` phục vụ UC08.

### 4.2. Tiêu chí kiểm thử trọng tâm
1. AI đạt ngưỡng và có Agent khả dụng → `ASSIGNED`, 1 `assignment_history(AUTO)`, 1 `notification`.
2. AI lỗi / điểm thấp / không có Agent → giữ `NEW` có lý do rõ, không mất ticket.
3. Hai phân công đồng thời → chỉ 1 giao dịch khớp `ticket.version` thành công.
4. Chờ khách (`WAITING_CUSTOMER`) chỉ tạm dừng `RESOLUTION`, không tạm dừng `FIRST_RESPONSE`; resume dời hạn đúng bằng khoảng pause.
5. Scheduler chạy lại nhiều lần → không tạo trùng `WARNING`/`BREACH` nhờ `UNIQUE(ticket_sla_id, event_type)`.
6. Mở lại ticket không reset `FIRST_RESPONSE` và không xóa `breached_at` trước đó.
