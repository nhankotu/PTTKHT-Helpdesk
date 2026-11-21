# THIẾT KẾ USE CASE — THÀNH VIÊN 1 (UC01 & UC02)

**Đề tài 07:** Nền tảng Helpdesk đa kênh tích hợp AI  
**Phân hệ:** Tiếp nhận và tổng hợp yêu cầu đa kênh (Omnichannel Intake)  
**Phụ trách:** Thành viên 1 (TV1)  
**Trạng thái:** Đã chuẩn hóa theo bản hợp nhất toàn hệ thống (MySQL 8.0+, 7 trạng thái Ticket)

---

## 1. Phạm vi, Actor và ranh giới hệ thống

- **UC01 — Gửi và tiếp nhận yêu cầu hỗ trợ:** Khách hàng gửi yêu cầu qua biểu mẫu Web (hoặc kênh hỗ trợ) kèm tệp đính kèm tùy chọn và nhận xác nhận tiếp nhận bền vững (`requestId`, `RECEIVED`).
- **UC02 — Tiếp nhận, chuẩn hóa và tổng hợp ticket:** Hệ thống tiếp nhận sự kiện từ kênh bên ngoài (Email Gateway, Facebook, Zalo) và yêu cầu nội bộ từ UC01; xác thực nguồn, chuẩn hóa dữ liệu, chống trùng, tạo mới hoặc nối vào ticket phù hợp và phát sự kiện `TicketCreated` cho UC04, UC05, UC07.

### Actor & Ranh giới
- **Khách hàng (Customer):** Actor chính của UC01.
- **Hệ thống kênh bên ngoài (Email Gateway / Facebook / Zalo):** Actor hệ thống ngoài của UC02.
- **Thành phần nội bộ (KHÔNG phải Actor):** `Channel Adapter`, `Outbox Worker`, `Queue`, `Database`.
- **Biểu đồ tham chiếu:**
  - Use Case: `../Bieu_do/UseCase/UC_01.png`, `../Bieu_do/UseCase/UC_02.png` *(lưu ý bổ sung kênh Web Form khi vẽ lại bản cuối)*
  - SSD: `../Bieu_do/SSD/UC01_SSD.png`, `../Bieu_do/SSD/UC02_SSD.png`
  - Tổng thể: `../Bieu_do/Tong_the/UseCase_Tong_The.png`, `../Bieu_do/Tong_the/ERD_Tong_The.png`

---

## 2. Đặc tả chi tiết UC01 — Gửi và tiếp nhận yêu cầu hỗ trợ

| Thuộc tính | Nội dung |
|---|---|
| **Mã / Tên Use Case** | UC01 — Gửi và tiếp nhận yêu cầu hỗ trợ |
| **Actor chính / phụ** | Chính: Khách hàng (Customer). |
| **Mục tiêu** | Khách hàng gửi yêu cầu hỗ trợ thành công và nhận mã tiếp nhận tra cứu; đảm bảo không mất yêu cầu và không tạo trùng khi gửi lại. |
| **Tiền điều kiện** | Kênh tiếp nhận (`channel`, `channel_account`) đang ở trạng thái `is_active = TRUE`. Hỗ trợ cả khách đã đăng nhập (`app_user`) và khách vãng lai (guest). |
| **Sự kiện kích hoạt** | Khách hàng điền biểu mẫu hỗ trợ và bấm "Gửi yêu cầu". |
| **Luồng chính** | 1. Khách hàng nhập tiêu đề (`subject`), nội dung (`body`), thông tin liên hệ (email/điện thoại nếu chưa đăng nhập) và tệp đính kèm tùy chọn (≤ 25MB/tệp, đúng định dạng MIME cho phép).<br>2. Client kiểm tra dữ liệu hợp lệ cơ bản và gửi yêu cầu `POST /api/v1/support/requests` kèm header `Idempotency-Key` duy nhất.<br>3. Hệ thống xác thực đầu vào, kiểm tra tệp đính kèm, kiểm tra trùng `Idempotency-Key` và lưu bền vững sự kiện vào bảng `inbound_event` (`process_status = 'RECEIVED'`, đã che PII trong `payload_redacted`).<br>4. Hệ thống trả `202 Accepted` kèm `requestId` và trạng thái `RECEIVED`.<br>5. Hệ thống gửi thông báo xác nhận tiếp nhận (`ticket_message.is_auto_ack = TRUE`) và cho phép khách tra cứu mã `ticket_code` qua `GET /api/v1/support/requests/{requestId}` sau khi UC02 xử lý xong. |
| **Luồng thay thế / Ngoại lệ** | - **E1 — Thiếu nội dung / sai định dạng / tệp > 25MB:** Trả `400/422`, giữ nguyên dữ liệu biểu mẫu.<br>- **E2 — Trùng `Idempotency-Key`:** Cùng `payload_hash` → trả lại kết quả `202` cũ; khác `payload_hash` → trả `409 Conflict`.<br>- **E3 — Quá tần suất gửi:** Trả `429 Too Many Requests`.<br>- **E4 — Lỗi gửi email/tin xác nhận:** Không hủy yêu cầu đã lưu; đánh dấu lỗi gửi để worker thử lại. |
| **Hậu điều kiện** | Yêu cầu đã được ghi bền vững vào `inbound_event`; trạng thái "Đã nhận yêu cầu" (`RECEIVED`) được phân biệt rõ với "Đã tạo ticket". |
| **Quy tắc nghiệp vụ** | - Chỉ trả `202` sau khi đã lưu bền vững vào DB.<br>- Email/tin xác nhận tiếp nhận (`is_auto_ack = TRUE`) **không được tính là `FIRST_RESPONSE` SLA**. |

---

## 3. Đặc tả chi tiết UC02 — Tiếp nhận, chuẩn hóa và tổng hợp ticket

| Thuộc tính | Nội dung |
|---|---|
| **Mã / Tên Use Case** | UC02 — Tiếp nhận, chuẩn hóa và tổng hợp ticket |
| **Actor chính / phụ** | Chính: Hệ thống kênh bên ngoài (Email Gateway, Facebook, Zalo) và yêu cầu đã tiếp nhận từ UC01. |
| **Mục tiêu** | Chuẩn hóa sự kiện từ mọi kênh về định dạng chung, chống nhận trùng khi provider retry, tạo ticket mới hoặc nối vào ticket đang mở và phát `TicketCreated`. |
| **Tiền điều kiện** | `channel_account` đã cấu hình và đang hoạt động; có cơ chế xác thực chữ ký webhook hoặc nguồn nội bộ. |
| **Sự kiện kích hoạt** | Kênh ngoài gọi webhook `POST /api/v1/channels/{id}/events` hoặc hàng đợi tiếp nhận sự kiện mới từ UC01. |
| **Luồng chính** | 1. Hệ thống xác thực chữ ký webhook / nguồn nội bộ; xác định `channel_account_id` và `external_event_id`.<br>2. Chèn bản ghi vào `inbound_event`; dựa vào ràng buộc `UNIQUE(channel_account_id, external_event_id)` để chống xử lý trùng.<br>3. `Channel Adapter` chuẩn hóa payload: tách thông tin người gửi (`customer_identity`), luồng trao đổi (`conversation` theo `UNIQUE(channel_account_id, external_thread_id)`), mã tin nhắn (`external_message_id`) và metadata tệp (`attachment`).<br>4. Trong **1 transaction MySQL (InnoDB)**:<br>   - **Trường hợp 1 (Hội thoại chưa có ticket đang mở, hoặc ticket cũ đã `CLOSED`):** Tạo `ticket` mới (`ticket_code` duy nhất, `status = 'NEW'`, `priority = 'NORMAL'`, `version = 0`; nếu ticket cũ đã `CLOSED` thì gán `parent_ticket_id`), tạo `ticket_message` (`direction = 'INBOUND'`, `visibility = 'PUBLIC'`, `sender_type = 'CUSTOMER'`), ghi `ticket_event(CREATED)` và chèn `outbox_event(event_type = 'TicketCreated')` trong cùng transaction.<br>   - **Trường hợp 2 (Hội thoại đã có ticket đang mở `NEW..RESOLVED`):** Nối `ticket_message` vào ticket đó (chống trùng bằng `UNIQUE(conversation_id, external_message_id)`). Nếu ticket đang ở `WAITING_CUSTOMER` hoặc `RESOLVED`, phát sự kiện cập nhật để UC03/UC05 chuyển về `IN_PROGRESS`.<br>5. Cập nhật `inbound_event.process_status = 'PROCESSED'`, gắn `ticket_id`, `message_id`, `processed_at`.<br>6. `Outbox Worker` đọc `outbox_event(PENDING)` và phát `TicketCreated` tới UC04, UC05, UC07. |
| **Luồng thay thế / Ngoại lệ** | - **E1 — Chữ ký webhook sai:** Từ chối `401/403`, không ghi `inbound_event`.<br>- **E2 — Provider retry event/message cũ:** Bắt lỗi `UNIQUE` → trả kết quả cũ, không tạo ticket/tin nhắn thứ hai.<br>- **E3 — Khách hàng chưa xác minh (`is_verified = FALSE`):** Không tự động gộp 2 `customer` chỉ vì trùng tên hoặc email chưa xác minh.<br>- **E4 — Lỗi DB trước commit:** Rollback toàn bộ, trả `5xx` để provider retry an toàn.<br>- **E5 — Outbox phát thất bại:** Tăng `retry_count`, thử lại với cùng `outbox_event.id` (consumer UC04/UC05 phải idempotent). |
| **Hậu điều kiện** | Đúng 1 `ticket_message` nguồn được lưu; ticket mới tạo có đúng 1 sự kiện `TicketCreated` trong `outbox_event`. |
| **Quy tắc nghiệp vụ** | - Ticket luôn được lưu bền vững trước khi gọi AI (UC04/UC07).<br>- UC02 **không tự gán Agent** và chỉ khởi tạo trạng thái `NEW`.<br>- Che toàn bộ PII trong `payload_redacted`; không lưu secret/token plain text trong `channel_account.config_metadata`. |

---

## 4. Hợp đồng tích hợp & Dữ liệu (MySQL 8.0+)

### 4.1. Hợp đồng sự kiện `TicketCreated` (UC02 → UC04, UC05, UC07)
```json
{
  "eventId": 1024,
  "eventType": "TicketCreated",
  "schemaVersion": "1.0",
  "ticketId": 501,
  "ticketCode": "TCK-2026-00501",
  "channelCode": "EMAIL",
  "conversationId": 310,
  "status": "NEW",
  "priority": "NORMAL",
  "subject": "Không đăng nhập được sau khi thanh toán",
  "description": "Tôi đã thanh toán nhưng tài khoản chưa kích hoạt.",
  "createdAt": "2026-11-21T15:00:00.000000Z"
}
```

### 4.2. Các bảng MySQL sử dụng (trích từ `helpdesk_unified_schema_mysql.sql`)
- `channel`, `channel_account`, `customer`, `customer_identity`, `conversation`
- `ticket` (chú ý đầy đủ `priority`, `version`, `parent_ticket_id`)
- `ticket_message`, `attachment`, `inbound_event`, `outbox_event`, `ticket_event`

### 4.3. Tiêu chí kiểm thử trọng tâm
1. Gửi yêu cầu hợp lệ → tạo đúng 1 `ticket(NEW)`, 1 `ticket_message(INBOUND, PUBLIC)`, 1 `outbox_event(TicketCreated)`.
2. Gửi trùng `Idempotency-Key` cùng payload → không tạo trùng; khác payload → `409 Conflict`.
3. Webhook gửi lặp cùng `(channel_account_id, external_event_id)` → chỉ tạo 1 tin nhắn/ticket.
4. Webhook sai chữ ký → `401/403`, không ghi DB.
5. Khách nhắn tiếp sau khi ticket đã `CLOSED` → tạo ticket mới (`NEW`) có `parent_ticket_id` trỏ về ticket cũ.
