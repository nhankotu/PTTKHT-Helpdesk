# HỢP ĐỒNG REST API CHI TIẾT (API CONTRACT — D2)

**Đề tài 07:** Nền tảng Helpdesk đa kênh tích hợp AI  
**Phiên bản hợp đồng:** `v1.0.0` (OpenAPI 3.1 compatible)  
**Ngày chốt:** 21/11/2026  
**Nguồn chân lý đối chiếu:**
- Lược đồ CSDL: [helpdesk_unified_schema_mysql.sql](./helpdesk_unified_schema_mysql.sql) (34 bảng MySQL 8.0+ & View `v_ticket_sla_summary`)
- Đặc tả Use Case hợp nhất: [01_Thiet_Ke_Use_Case_Tong_Hop.md](./01_Thiet_Ke_Use_Case_Tong_Hop.md) (UC00–UC08)
- Tích hợp AI: [03_Huong_Dan_Tich_Hop_AI_OpenAI.md](./03_Huong_Dan_Tich_Hop_AI_OpenAI.md)

---

## 1. Quy ước chung toàn hệ thống (Global Conventions)

### 1.1. Base URL, Versioning & Content-Type
- **Base Path:** `/api/v1`
- **Media Type mặc định:** `application/json; charset=utf-8`
- **Upload tệp đính kèm:** `multipart/form-data` (giới hạn `≤ 25 MB` = `26,214,400 bytes` mỗi tệp, kiểm tra MIME whitelist: `image/png`, `image/jpeg`, `image/webp`, `application/pdf`, `text/plain`, `application/vnd.openxmlformats-officedocument.*`).
- **Response lỗi:** `application/problem+json` theo chuẩn **RFC 7807 (`ProblemDetail`)**.

### 1.2. Định dạng Thời gian & Kiểu dữ liệu
- **Mọi mốc thời gian** trong Request/Response đều dùng chuỗi **UTC ISO-8601** có hậu tố `Z` (tương ứng `java.time.Instant` và MySQL `DATETIME(6)`), ví dụ: `"2026-11-21T16:08:35.123456Z"`.
- Frontend tự chuyển đổi sang múi giờ `Asia/Ho_Chi_Minh` (`UTC+7`) khi hiển thị.
- **Khóa chính:** `BIGINT` → JSON `number` (`integer` / `int64`).
- **Enum:** `UPPER_SNAKE_CASE`, khớp 100% ràng buộc `CHECK` trong MySQL 8.0.

### 1.3. Quy ước Phân trang & Sắp xếp (Pagination & Sorting)
Mọi endpoint trả danh sách đều dùng query params thống nhất:
- `page`: chỉ số trang bắt đầu từ `0` (mặc định `0`).
- `size`: số phần tử mỗi trang (mặc định `20`, tối đa `100`).
- `sort`: định dạng `<field>,<asc|desc>`, có thể lặp nhiều lần (ví dụ: `?page=0&size=20&sort=priority,asc&sort=createdAt,desc`).

**Cấu trúc Response chuẩn `PageResponse<T>`:**
```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 125,
  "totalPages": 7,
  "first": true,
  "last": false
}
```

### 1.4. Xác thực, Phân quyền (Auth & RBAC) & Scope dữ liệu
- **Header xác thực:** `Authorization: Bearer <accessToken>` (JWT chứa claims: `sub` = `userId`, `email`, `roles[]`, `teamIds[]`, `customerId`).
- **4 vai trò chuẩn (`role.code`):** `CUSTOMER`, `AGENT`, `SUPERVISOR`, `ADMIN`.
- **Kiểm soát phạm vi (Data Scope & Ownership):**
  - `CUSTOMER`: Chỉ được gọi nhóm API `/api/v1/customer/**` và `/api/v1/intake/**`. Chỉ xem/thao tác ticket có `ticket.customer_id = currentCustomerId`. **Tuyệt đối không trả dữ liệu `visibility = INTERNAL`** (lọc cứng ở SQL/JPQL và dùng DTO riêng).
  - `AGENT`: Chỉ xem/xử lý ticket được phân công cho mình hoặc thuộc hàng đợi nhóm (`team_member`) của mình. Không được duyệt/xuất bản/thu hồi KB, không xem báo cáo UC08.
  - `SUPERVISOR`: Quản lý hàng đợi, phân công thủ công, xử lý escalation, duyệt/xuất bản/thu hồi KB và xem báo cáo UC08 trong phạm vi các nhóm mình quản lý (`support_team.supervisor_id = currentUserId`).
  - `ADMIN`: Toàn quyền quản trị nền tảng (`app_user`, `support_team`, `category`, `channel_account`, `routing_rule`, `sla_policy`) và xem báo cáo toàn hệ thống.

### 1.5. Headers đặc biệt (Idempotency, Webhook, Tracing)
| Header | Bắt buộc cho | Ý nghĩa |
|---|---|---|
| `Idempotency-Key` | `POST /api/v1/intake/web-requests` | Chuỗi duy nhất (≤ 128 ký tự, khuyến nghị UUIDv4). Trùng key + cùng `payload_hash` → trả lại kết quả cũ (không tạo bản ghi mới). Trùng key + khác `payload_hash` → `409 Conflict`. |
| `X-Webhook-Signature` | `POST /api/v1/webhooks/{channelCode}` | Chữ ký `HMAC-SHA256` của raw payload bằng secret của `channel_account`. Sai chữ ký → `401 Unauthorized`. |
| `X-Webhook-Timestamp` | `POST /api/v1/webhooks/{channelCode}` | Unix epoch seconds chống replay attack (lệch > 300s → `401`). |
| `X-Request-Id` | Tùy chọn (Request & Response) | Mã truy vết request, được phản hồi lại trong header và `ProblemDetail.traceId`. |

### 1.6. Chống ghi đè đồng thời (Optimistic Locking — `ticket.version`)
- Mọi endpoint thay đổi trạng thái/metadata/phân công của `ticket` **bắt buộc** truyền trường `version` (số nguyên khớp `ticket.version` hiện tại) trong Request Body.
- Nếu `version` không khớp với bản ghi hiện tại trong DB, Backend trả ngay `409 Conflict` kèm `errorCode: "OPTIMISTIC_LOCK_CONFLICT"` và `currentVersion` để Frontend hiển thị cảnh báo kèm nút **"Tải lại bản mới nhất"**.

### 1.7. Chuẩn lỗi RFC 7807 (`ProblemDetail`)
Mọi lỗi `4xx/5xx` trả về `Content-Type: application/problem+json`:

```json
{
  "type": "https://helpdesk.local/errors/optimistic-lock-conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Ticket đã được cập nhật bởi một thao tác khác. Vui lòng tải lại dữ liệu mới nhất.",
  "instance": "/api/v1/tickets/1042/status",
  "errorCode": "OPTIMISTIC_LOCK_CONFLICT",
  "timestamp": "2026-11-21T16:10:00.000000Z",
  "traceId": "req-8f2a9c10",
  "currentVersion": 4
}
```

**Bảng mã lỗi chuẩn & mã lỗi nghiệp vụ (`errorCode`):**

| HTTP Status | `errorCode` tiêu biểu | Khi nào xảy ra | Extension fields bổ sung |
|---|---|---|---|
| `400 Bad Request` | `MALFORMED_REQUEST`, `MISSING_HEADER`, `VALIDATION_ERROR` | JSON sai cú pháp, thiếu `Idempotency-Key`, vi phạm `@Valid` cơ bản. | `fieldErrors: [{ field, message, rejectedValue }]` |
| `401 Unauthorized` | `UNAUTHORIZED`, `TOKEN_EXPIRED`, `INVALID_WEBHOOK_SIGNATURE` | Thiếu JWT, JWT hết hạn/không hợp lệ, chữ ký webhook sai. | — |
| `403 Forbidden` | `FORBIDDEN`, `TICKET_ACCESS_DENIED`, `TEAM_SCOPE_DENIED` | Sai vai trò RBAC (VD: Agent gọi duyệt KB, Customer xem ticket người khác). | `requiredRoles` (tùy chọn) |
| `404 Not Found` | `RESOURCE_NOT_FOUND` | Không tìm thấy tài nguyên (hoặc ẩn tài nguyên `INTERNAL` với Customer). | `resourceType`, `resourceId` |
| `409 Conflict` | `OPTIMISTIC_LOCK_CONFLICT`, `IDEMPOTENCY_KEY_PAYLOAD_MISMATCH`, `DUPLICATE_RESOURCE`, `ACTIVE_RULE_CONFLICT` | Lệch `ticket.version`, trùng `Idempotency-Key` khác payload, trùng email/code, vi phạm ràng buộc duy nhất. | `currentVersion` (cho lock conflict) |
| `422 Unprocessable Entity` | `INVALID_STATE_TRANSITION`, `CANCEL_REASON_TOO_SHORT`, `RESOLUTION_CODE_REQUIRED`, `AGENT_NOT_IN_TEAM`, `ATTACHMENT_TOO_LARGE`, `INVALID_REPORT_DAYS`, `KB_VERSION_NOT_EDITABLE` | Dữ liệu đúng cú pháp nhưng vi phạm luật nghiệp vụ / State Machine Guard / tham số ngoài biên (`days` ∉ `[1..365]`). | `fieldErrors` hoặc `allowedTransitions` |
| `429 Too Many Requests` | `RATE_LIMIT_EXCEEDED` | Vượt ngưỡng gửi yêu cầu trên kênh tiếp nhận (UC01-E3). | `retryAfterSeconds` |

**Ví dụ `422 Unprocessable Entity` (vi phạm State Machine Guard):**
```json
{
  "type": "https://helpdesk.local/errors/invalid-state-transition",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Không thể chuyển trạng thái từ CLOSED sang IN_PROGRESS.",
  "instance": "/api/v1/tickets/1042/status",
  "errorCode": "INVALID_STATE_TRANSITION",
  "timestamp": "2026-11-21T16:12:00.000000Z",
  "traceId": "req-3c9b11e2",
  "currentStatus": "CLOSED",
  "targetStatus": "IN_PROGRESS",
  "allowedTransitions": []
}
```

---

## 2. Danh mục Enum chuẩn (Khớp 100% MySQL `CHECK` Constraints)

| Tên Enum | Giá trị hợp lệ |
|---|---|
| `RoleCode` | `CUSTOMER`, `AGENT`, `SUPERVISOR`, `ADMIN` |
| `ChannelCode` | `WEB_FORM`, `EMAIL`, `LIVE_CHAT`, `FACEBOOK`, `ZALO` |
| `TicketStatus` | `NEW`, `ASSIGNED`, `IN_PROGRESS`, `WAITING_CUSTOMER`, `RESOLVED`, `CLOSED`, `CANCELLED` |
| `TicketPriority` | `URGENT`, `HIGH`, `NORMAL`, `LOW` |
| `MessageSenderType` | `CUSTOMER`, `AGENT`, `SUPERVISOR`, `AI_BOT`, `SYSTEM` |
| `MessageDirection` | `INBOUND`, `OUTBOUND`, `INTERNAL` |
| `MessageVisibility` | `PUBLIC`, `INTERNAL` |
| `MessageDeliveryStatus` | `PENDING`, `SENT`, `DELIVERED`, `FAILED` |
| `InboundProcessStatus` | `RECEIVED`, `PROCESSED`, `FAILED` |
| `TicketEventAction` | `CREATED`, `STATUS_CHANGE`, `ASSIGNMENT_CHANGE`, `PRIORITY_CHANGE`, `CATEGORY_CHANGE`, `REOPENED`, `CANCELLED` |
| `ClassificationOutcome` | `APPLIED`, `REVIEW_REQUIRED`, `FAILED`, `SKIPPED` |
| `AssignmentSource` | `AUTO`, `MANUAL` |
| `SlaMetric` | `FIRST_RESPONSE`, `RESOLUTION` |
| `SlaTrackingStatus` | `RUNNING`, `PAUSED`, `COMPLETED`, `CANCELLED` |
| `SlaPauseReason` | `WAITING_CUSTOMER`, `RESOLVED_UNTIL_REOPEN` |
| `SlaEventType` | `WARNING`, `BREACH` |
| `KbAccessScope` | `PUBLIC`, `INTERNAL_AGENT` |
| `KbVersionStatus` | `DRAFT`, `IN_REVIEW`, `PUBLISHED`, `REJECTED`, `ARCHIVED` |
| `AiTaskType` | `RAG_SUGGESTION`, `AUTO_REPLY` |
| `AiRunOutcome` | `SUGGESTED`, `AUTO_SENT`, `FALLBACK_TO_AGENT`, `FAILED` |
| `AiFallbackReason` | `NO_RELEVANT_KB`, `CONFLICTING_SOURCES`, `OUTDATED_OR_REVOKED_KB`, `LOW_CONFIDENCE`, `OUT_OF_AUTO_SCOPE`, `CUSTOMER_REQUESTED_HUMAN`, `CITATION_INVALID`, `AI_SERVICE_ERROR` |
| `AiFeedbackDecision` | `ACCEPTED_AS_IS`, `EDITED_AND_SENT`, `REJECTED`, `ESCALATED` |

---

## 3. Thiết kế Chi tiết Endpoint theo 6 Phân hệ (UC00–UC08)

---

### PHÂN HỆ 1: `identity` — Tài khoản, Phân quyền & Quản trị nền tảng (UC00 / FR01)

#### Bảng tổng hợp Endpoint
| Method | Endpoint | Roles được phép | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Public | Đăng nhập bằng email & mật khẩu, nhận JWT |
| `POST` | `/api/v1/auth/register` | Public | Đăng ký tài khoản Khách hàng (`CUSTOMER`) |
| `POST` | `/api/v1/auth/refresh` | Public (Refresh Token) | Cấp mới `accessToken` |
| `POST` | `/api/v1/auth/logout` | Authenticated | Đăng xuất & vô hiệu hóa refresh token |
| `GET` | `/api/v1/auth/me` | Authenticated | Lấy hồ sơ người dùng hiện tại & scope nhóm |
| `PATCH` | `/api/v1/auth/me/availability` | `AGENT`, `SUPERVISOR` | Bật/tắt `is_available` nhận phân công tự động (UC04) |
| `GET` | `/api/v1/admin/users` | `ADMIN`, `SUPERVISOR` (nhóm mình) | Danh sách tài khoản (lọc `role`, `teamId`, `isActive`, `isAvailable`) |
| `POST` | `/api/v1/admin/users` | `ADMIN` | Tạo tài khoản nhân viên (`AGENT`/`SUPERVISOR`/`ADMIN`) |
| `PATCH` | `/api/v1/admin/users/{userId}` | `ADMIN` | Cập nhật thông tin, khóa/mở khóa, đổi vai trò |
| `GET` | `/api/v1/teams` | `AGENT`, `SUPERVISOR`, `ADMIN` | Danh sách nhóm hỗ trợ (`support_team`) |
| `POST` | `/api/v1/admin/teams` | `ADMIN` | Tạo nhóm hỗ trợ mới |
| `PATCH` | `/api/v1/admin/teams/{teamId}` | `ADMIN` | Cập nhật tên, trưởng nhóm (`supervisorId`), trạng thái |
| `PUT` | `/api/v1/admin/teams/{teamId}/members/{userId}` | `ADMIN`, `SUPERVISOR` (nhóm mình) | Thêm Agent vào `team_member` |
| `DELETE` | `/api/v1/admin/teams/{teamId}/members/{userId}` | `ADMIN`, `SUPERVISOR` (nhóm mình) | Xóa Agent khỏi `team_member` |
| `GET` | `/api/v1/categories` | Public / Authenticated | Danh sách danh mục (`?activeOnly=true`) |
| `POST` | `/api/v1/admin/categories` | `ADMIN` | Tạo danh mục mới |
| `PATCH` | `/api/v1/admin/categories/{categoryId}` | `ADMIN` | Cập nhật danh mục |
| `GET` | `/api/v1/admin/channels` | `ADMIN`, `SUPERVISOR` | Danh sách kênh (`channel`) và tài khoản kênh (`channel_account`) |
| `POST` | `/api/v1/admin/channel-accounts` | `ADMIN` | Cấu hình tài khoản kênh mới |
| `PATCH` | `/api/v1/admin/channel-accounts/{id}` | `ADMIN` | Bật/tắt hoặc cập nhật cấu hình kênh |

#### DTO trọng tâm Phân hệ 1

**`POST /api/v1/auth/login`**
- Request:
```json
{
  "email": "agent.an@helpdesk.local",
  "password": "StrongPassword123!"
}
```
- Response `200 OK`:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "user": {
    "id": 12,
    "fullName": "Nguyễn Văn An",
    "email": "agent.an@helpdesk.local",
    "phone": "0901234567",
    "roles": ["AGENT"],
    "teamIds": [2],
    "customerId": null,
    "isActive": true,
    "isAvailable": true
  }
}
```

**`PATCH /api/v1/auth/me/availability`**
- Request: `{ "isAvailable": true }`
- Response `200 OK`: `{ "userId": 12, "isAvailable": true, "updatedAt": "2026-11-21T16:00:00Z" }`

---

### PHÂN HỆ 2: `intake` — Tiếp nhận Đa kênh & Tổng hợp Ticket (UC01–UC02)

#### Bảng tổng hợp Endpoint
| Method | Endpoint | Roles / Auth | Headers bắt buộc | Mô tả |
|---|---|---|---|---|
| `POST` | `/api/v1/attachments` | Authenticated / Guest | — | Upload 1 tệp đính kèm (`multipart/form-data`, ≤ 25MB) |
| `GET` | `/api/v1/attachments/{attachmentId}/download` | Authenticated (có quyền đọc ticket) | — | Tải tệp đính kèm (chặn `CUSTOMER` tải file từ tin `INTERNAL`) |
| `POST` | `/api/v1/intake/web-requests` | `CUSTOMER` hoặc Public (Guest) | `Idempotency-Key` | **UC01:** Gửi yêu cầu hỗ trợ từ Web Form → lưu `inbound_event` & tạo/ghép ticket |
| `GET` | `/api/v1/intake/web-requests/{requestId}` | `CUSTOMER` (chủ sở hữu) / Tracking token | — | Tra cứu trạng thái `inbound_event` (`RECEIVED`/`PROCESSED`/`FAILED`) & `ticketCode` |
| `POST` | `/api/v1/webhooks/{channelCode}` | External Channel (`EMAIL`, `LIVE_CHAT`, `FACEBOOK`, `ZALO`) | `X-Webhook-Signature`, `X-Webhook-Timestamp` | **UC02:** Nhận sự kiện webhook từ kênh ngoài, chuẩn hóa qua `ChannelAdapter` & chống trùng |
| `GET` | `/api/v1/admin/inbound-events` | `ADMIN`, `SUPERVISOR` | — | Giám sát nhật ký `inbound_event` (`?processStatus=FAILED&page=0&size=20`) |

#### DTO trọng tâm Phân hệ 2

**`POST /api/v1/intake/web-requests`** (Header: `Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000`)
- Request:
```json
{
  "subject": "Đã chuyển khoản nhưng tài khoản chưa kích hoạt",
  "description": "Tôi thanh toán gói Pro lúc 14:00 mã giao dịch FT261121999...",
  "categoryId": 1,
  "contact": {
    "fullName": "Trần Minh Quân",
    "email": "quan.tran@example.com",
    "phone": "0912345678"
  },
  "attachmentIds": [501, 502]
}
```
*(Ghi chú: Nếu người dùng đã đăng nhập với role `CUSTOMER`, trường `contact` là tùy chọn và sẽ tự lấy từ hồ sơ `customer` hiện tại.)*

- Response `202 Accepted` (tạo mới hoặc trả lại kết quả cũ nếu trùng `Idempotency-Key` & cùng `payload_hash`):
```json
{
  "requestId": 9001,
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "processStatus": "PROCESSED",
  "ticketId": 1042,
  "ticketCode": "HD-2026-001042",
  "duplicate": false,
  "receivedAt": "2026-11-21T16:05:10.120000Z"
}
```
- Lỗi đặc thù:
  - `409 Conflict` (`IDEMPOTENCY_KEY_PAYLOAD_MISMATCH`) nếu cùng `Idempotency-Key` nhưng khác nội dung payload.
  - `422 Unprocessable Entity` (`ATTACHMENT_TOO_LARGE` / `UNSUPPORTED_MEDIA_TYPE`).
  - `429 Too Many Requests` (`RATE_LIMIT_EXCEEDED`).

**`POST /api/v1/webhooks/{channelCode}`** (VD: `/api/v1/webhooks/EMAIL` hoặc `/api/v1/webhooks/FACEBOOK`)
- Request (payload chuẩn hóa/mô phỏng từ kênh ngoài):
```json
{
  "channelAccountCode": "FB_PAGE_MAIN",
  "externalEventId": "evt_fb_9988776655",
  "externalThreadId": "thr_fb_123456",
  "externalMessageId": "msg_fb_778899",
  "sender": {
    "externalUserId": "fb_user_445566",
    "displayName": "Lê Thu Hà",
    "email": null,
    "phone": null
  },
  "subject": "Hỏi về chính sách đổi trả đơn hàng #8821",
  "body": "Shop ơi cho mình hỏi đơn #8821 muốn đổi size thì làm sao ạ?",
  "occurredAt": "2026-11-21T16:06:00.000000Z",
  "attachments": [
    {
      "fileName": "bill.png",
      "mimeType": "image/png",
      "sizeBytes": 245120,
      "remoteUrl": "https://cdn.example.com/bill.png"
    }
  ]
}
```
- Response `202 Accepted` (hoặc `200 OK` nếu `duplicate: true`):
```json
{
  "inboundEventId": 9002,
  "externalEventId": "evt_fb_9988776655",
  "processStatus": "PROCESSED",
  "duplicate": false,
  "action": "TICKET_CREATED",
  "ticketId": 1043,
  "ticketCode": "HD-2026-001043",
  "messageId": 5510
}
```
*(Giá trị `action`: `TICKET_CREATED` | `MESSAGE_APPENDED` | `DUPLICATE_IGNORED`)*

---

### PHÂN HỆ 3: `ticket` — Vòng đời Ticket, Hội thoại & CSAT (UC03)

> **Quy tắc bất biến về cách ly dữ liệu (`INTERNAL` Isolation):**
> - Khách hàng (`CUSTOMER`) **chỉ dùng** nhóm endpoint `/api/v1/customer/tickets/**` với DTO riêng (`CustomerTicketDetailDto`, `CustomerMessageDto`).
> - Repository của Customer Portal **bắt buộc** lọc cứng `WHERE m.visibility = 'PUBLIC'` trong SQL/JPQL.
> - Nếu `CUSTOMER` gọi bất kỳ endpoint nào dưới `/api/v1/tickets/**`, Spring Security chặn ngay `403 Forbidden`.

#### 3A. Nhóm Endpoint Cổng Khách hàng (`CUSTOMER` only — Ownership Check)

| Method | Endpoint | Roles | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/customer/tickets` | `CUSTOMER` | Danh sách ticket của chính khách hàng (`?status=&page=0&size=20`) |
| `GET` | `/api/v1/customer/tickets/{ticketId}` | `CUSTOMER` | Chi tiết ticket (`CustomerTicketDetailDto` — **không có trường nội bộ**) |
| `GET` | `/api/v1/customer/tickets/{ticketId}/messages` | `CUSTOMER` | Danh sách tin nhắn **`PUBLIC` duy nhất** kèm đính kèm & trích dẫn AI |
| `POST` | `/api/v1/customer/tickets/{ticketId}/messages` | `CUSTOMER` | Khách bổ sung thông tin (nếu `WAITING_CUSTOMER` → tự chuyển `IN_PROGRESS` & đóng `sla_pause`) |
| `POST` | `/api/v1/customer/tickets/{ticketId}/confirm-close` | `CUSTOMER` | Khách xác nhận hoàn tất (`RESOLVED → CLOSED`, yêu cầu `version`) |
| `POST` | `/api/v1/customer/tickets/{ticketId}/reopen` | `CUSTOMER` | Khách yêu cầu mở lại (`RESOLVED → IN_PROGRESS`, yêu cầu `version` & `reason`) |
| `POST` | `/api/v1/customer/tickets/{ticketId}/csat` | `CUSTOMER` | Nộp khảo sát CSAT (`score` 1..5, `comment`) cho ticket `RESOLVED`/`CLOSED` |

**DTO `CustomerTicketDetailDto`** (`GET /api/v1/customer/tickets/{ticketId}`):
```json
{
  "id": 1042,
  "ticketCode": "HD-2026-001042",
  "subject": "Đã chuyển khoản nhưng tài khoản chưa kích hoạt",
  "description": "Tôi thanh toán gói Pro lúc 14:00...",
  "status": "RESOLVED",
  "channel": { "code": "WEB_FORM", "name": "Web Form" },
  "category": { "id": 1, "code": "PAYMENT", "name": "Thanh toán" },
  "assignedAgentName": "Nguyễn Văn An",
  "resolutionCode": "ACCOUNT_ACTIVATED",
  "resolvedAt": "2026-11-21T16:45:00Z",
  "closedAt": null,
  "createdAt": "2026-11-21T16:05:10Z",
  "updatedAt": "2026-11-21T16:45:00Z",
  "version": 3,
  "csat": {
    "eligible": true,
    "submitted": false,
    "score": null,
    "comment": null,
    "submittedAt": null
  }
}
```
*(Chú ý: KHÔNG có `internalNotes`, KHÔNG có `teamId`, KHÔNG có `classificationResult`, KHÔNG có thông tin SLA nội bộ/cảnh báo chuyển cấp.)*

**DTO `CustomerMessageDto`** (`GET /api/v1/customer/tickets/{ticketId}/messages`):
```json
{
  "id": 5520,
  "senderType": "AGENT",
  "senderDisplayName": "Nguyễn Văn An",
  "direction": "OUTBOUND",
  "isAutoAck": false,
  "body": "Chào bạn, tài khoản gói Pro của bạn đã được kích hoạt thành công.",
  "occurredAt": "2026-11-21T16:45:00Z",
  "attachments": [
    {
      "id": 510,
      "fileName": "xac_nhan.pdf",
      "mimeType": "application/pdf",
      "sizeBytes": 102400
    }
  ],
  "citations": []
}
```
*(Chú ý: Không bao giờ có tin `visibility = INTERNAL`, thậm chí không cần trả trường `visibility` vì 100% là `PUBLIC`.)*

**`POST /api/v1/customer/tickets/{ticketId}/csat`**
- Request: `{ "score": 5, "comment": "Hỗ trợ rất nhanh và nhiệt tình!" }` (`score`: integer `1..5` bắt buộc).
- Response `200 OK`: `{ "ticketId": 1042, "score": 5, "comment": "Hỗ trợ rất nhanh và nhiệt tình!", "submittedAt": "2026-11-21T17:00:00Z" }`

---

#### 3B. Nhóm Endpoint Không gian Nhân viên (`AGENT`, `SUPERVISOR`, `ADMIN`)

| Method | Endpoint | Roles | Yêu cầu `version`? | Mô tả |
|---|---|---|---|---|
| `GET` | `/api/v1/tickets` | `AGENT`, `SUPERVISOR`, `ADMIN` | Không | Danh sách ticket theo phạm vi quyền (`?status=&priority=&categoryId=&teamId=&assignedAgentId=&unassignedOnly=&slaState=&q=&page=0&size=20&sort=createdAt,desc`) |
| `GET` | `/api/v1/tickets/{ticketId}` | `AGENT`, `SUPERVISOR`, `ADMIN` | Không | Chi tiết đầy đủ cho nhân viên (`StaffTicketDetailDto`) |
| `GET` | `/api/v1/tickets/{ticketId}/messages` | `AGENT`, `SUPERVISOR`, `ADMIN` | Không | Lịch sử hội thoại đầy đủ (`?visibility=ALL\|PUBLIC\|INTERNAL`) |
| `POST` | `/api/v1/tickets/{ticketId}/messages` | `AGENT`, `SUPERVISOR` | **Có** (nếu kèm `targetStatus`) | Gửi phản hồi `PUBLIC` hoặc ghi chú `INTERNAL`; hỗ trợ đổi trạng thái nguyên tử ("Gửi & Cập nhật") |
| `POST` | `/api/v1/tickets/{ticketId}/messages/{messageId}/retry` | `AGENT`, `SUPERVISOR` | Không | Thử gửi lại tin nhắn `PUBLIC` có `deliveryStatus = FAILED` (UC03-E1) |
| `PATCH` | `/api/v1/tickets/{ticketId}/status` | `AGENT`, `SUPERVISOR` | **Có (Bắt buộc)** | Chuyển trạng thái qua State Machine Guard (kèm `resolutionCode` / `cancelReason`) |
| `PATCH` | `/api/v1/tickets/{ticketId}/metadata` | `AGENT`, `SUPERVISOR`, `ADMIN` | **Có (Bắt buộc)** | Cập nhật `priority` (`SUPERVISOR`/`ADMIN`) hoặc `categoryId` |
| `POST` | `/api/v1/tickets/{ticketId}/transfer` | `AGENT`, `SUPERVISOR`, `ADMIN` | **Có (Bắt buộc)** | Chuyển giao ticket sang nhóm/Agent khác (`team_member`), giữ nguyên SLA |
| `GET` | `/api/v1/tickets/{ticketId}/events` | `AGENT`, `SUPERVISOR`, `ADMIN` | Không | Lịch sử kiểm toán (`ticket_event` + `assignment_history`) |

**DTO `StaffTicketDetailDto`** (`GET /api/v1/tickets/{ticketId}`):
```json
{
  "id": 1042,
  "ticketCode": "HD-2026-001042",
  "conversationId": 3001,
  "parentTicketId": null,
  "subject": "Đã chuyển khoản nhưng tài khoản chưa kích hoạt",
  "description": "Tôi thanh toán gói Pro lúc 14:00...",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "resolutionCode": null,
  "cancelReason": null,
  "channel": { "id": 1, "code": "WEB_FORM", "name": "Web Form" },
  "category": { "id": 1, "code": "PAYMENT", "name": "Thanh toán" },
  "team": { "id": 2, "code": "BILLING", "name": "Hỗ trợ Thanh toán" },
  "assignedAgent": { "id": 12, "fullName": "Nguyễn Văn An", "email": "agent.an@helpdesk.local" },
  "customer": {
    "id": 88,
    "fullName": "Trần Minh Quân",
    "email": "quan.tran@example.com",
    "phone": "0912345678",
    "isVerified": true
  },
  "sla": {
    "firstResponse": {
      "metric": "FIRST_RESPONSE",
      "trackingStatus": "COMPLETED",
      "deadlineAt": "2026-11-21T17:05:10Z",
      "completedAt": "2026-11-21T16:15:00Z",
      "breachedAt": null,
      "warningTriggered": false
    },
    "resolution": {
      "metric": "RESOLUTION",
      "trackingStatus": "RUNNING",
      "deadlineAt": "2026-11-22T00:05:10Z",
      "completedAt": null,
      "breachedAt": null,
      "warningTriggered": false,
      "pausedSeconds": 0
    }
  },
  "latestClassification": {
    "id": 401,
    "suggestedCategoryCode": "PAYMENT",
    "confidence": 0.9100,
    "outcome": "APPLIED",
    "modelVersion": "gpt-4o-mini"
  },
  "allowedTransitions": ["WAITING_CUSTOMER", "RESOLVED", "CANCELLED"],
  "resolvedAt": null,
  "closedAt": null,
  "createdAt": "2026-11-21T16:05:10Z",
  "updatedAt": "2026-11-21T16:15:00Z",
  "version": 2
}
```

**`POST /api/v1/tickets/{ticketId}/messages`** (Gửi phản hồi `PUBLIC` hoặc Ghi chú `INTERNAL`)
- Request (Trả lời công khai + chuyển `WAITING_CUSTOMER`):
```json
{
  "visibility": "PUBLIC",
  "body": "Bạn vui lòng gửi giúp mình ảnh chụp biên lai giao dịch nhé.",
  "attachmentIds": [],
  "targetStatus": "WAITING_CUSTOMER",
  "resolutionCode": null,
  "version": 2
}
```
- Request (Ghi chú nội bộ `INTERNAL` — không đổi trạng thái, không đẩy kênh ngoài):
```json
{
  "visibility": "INTERNAL",
  "body": "Đã kiểm tra cổng thanh toán, thấy lệnh treo ở ngân hàng, đang chờ đối soát.",
  "attachmentIds": [512]
}
```
- Response `201 Created`:
```json
{
  "message": {
    "id": 5525,
    "ticketId": 1042,
    "senderUserId": 12,
    "senderDisplayName": "Nguyễn Văn An",
    "senderType": "AGENT",
    "direction": "OUTBOUND",
    "visibility": "PUBLIC",
    "isAutoAck": false,
    "body": "Bạn vui lòng gửi giúp mình ảnh chụp biên lai giao dịch nhé.",
    "deliveryStatus": "SENT",
    "occurredAt": "2026-11-21T16:20:00Z",
    "attachments": []
  },
  "ticketStatus": "WAITING_CUSTOMER",
  "ticketVersion": 3
}
```
*(Ghi chú UC03-E1: Nếu `ChannelAdapter` gửi ra kênh ngoài thất bại, hệ thống vẫn trả `201 Created` với `message.deliveryStatus = "FAILED"`, giữ nguyên trạng thái cũ và **KHÔNG** đánh dấu hoàn thành `FIRST_RESPONSE` SLA.)*

**`PATCH /api/v1/tickets/{ticketId}/status`**
- Request:
```json
{
  "version": 3,
  "targetStatus": "CANCELLED",
  "resolutionCode": null,
  "cancelReason": "Ticket trùng lặp với mã HD-2026-001040 đã xử lý xong."
}
```
- Ràng buộc kiểm tra:
  - `targetStatus = RESOLVED` → bắt buộc `resolutionCode` (nếu thiếu → `422 RESOLUTION_CODE_REQUIRED`).
  - `targetStatus = CANCELLED` → bắt buộc `cancelReason` có `TRIM(length) >= 10` (nếu thiếu → `422 CANCEL_REASON_TOO_SHORT`).
  - Sai ma trận chuyển trạng thái → `422 INVALID_STATE_TRANSITION`.
  - Lệch `version` → `409 OPTIMISTIC_LOCK_CONFLICT`.

**`POST /api/v1/tickets/{ticketId}/transfer`**
- Request:
```json
{
  "version": 2,
  "teamId": 3,
  "assignedAgentId": 19,
  "reason": "Chuyển sang nhóm Kỹ thuật kiểm tra log hệ thống."
}
```
- Ràng buộc: `assignedAgentId` (nếu khác `null`) **bắt buộc** thuộc `team_member(teamId)` (khớp `fk_ticket_team_member` trong MySQL; vi phạm → `422 AGENT_NOT_IN_TEAM`).

---

### PHÂN HỆ 4: `routing` — AI Phân loại & Định tuyến (UC04)

#### Bảng tổng hợp Endpoint
| Method | Endpoint | Roles | Yêu cầu `version`? | Mô tả |
|---|---|---|---|---|
| `GET` | `/api/v1/routing/rules` | `SUPERVISOR`, `ADMIN` | Không | Danh sách `routing_rule` |
| `POST` | `/api/v1/admin/routing/rules` | `ADMIN` | Không | Tạo `routing_rule` (mỗi `category` tối đa 1 luật `isActive = true`) |
| `PATCH` | `/api/v1/admin/routing/rules/{ruleId}` | `ADMIN` | Không | Cập nhật `teamId`, `confidenceThreshold`, `autoAssignEnabled`, `isActive` |
| `GET` | `/api/v1/routing/review-queue` | `SUPERVISOR`, `ADMIN` | Không | Hàng đợi Fallback (`status = NEW`, AI `REVIEW_REQUIRED`/`FAILED` hoặc không có Agent rảnh) |
| `POST` | `/api/v1/routing/tickets/{ticketId}/assign` | `SUPERVISOR`, `ADMIN` | **Có (Bắt buộc)** | Phân công thủ công (`NEW → ASSIGNED`), ghi `assignment_history(MANUAL)` & `notification` |
| `POST` | `/api/v1/routing/tickets/{ticketId}/reclassify` | `SUPERVISOR`, `ADMIN` | Không | Chạy lại AI phân loại cho ticket |
| `GET` | `/api/v1/routing/tickets/{ticketId}/classifications` | `AGENT`, `SUPERVISOR`, `ADMIN` | Không | Xem lịch sử `classification_result` của ticket |

#### DTO trọng tâm Phân hệ 4

**`POST /api/v1/admin/routing/rules`**
- Request:
```json
{
  "categoryId": 1,
  "teamId": 2,
  "confidenceThreshold": 0.7500,
  "autoAssignEnabled": true,
  "isActive": true
}
```
- Response `201 Created` (hoặc `409 ACTIVE_RULE_CONFLICT` nếu `categoryId` đã có luật `isActive = true` khác).

**`POST /api/v1/routing/tickets/{ticketId}/assign`**
- Request:
```json
{
  "version": 0,
  "categoryId": 1,
  "teamId": 2,
  "agentId": 12,
  "reason": "AI confidence thấp (0.58), trưởng nhóm phân công thủ công cho An."
}
```
- Response `200 OK`:
```json
{
  "ticketId": 1045,
  "status": "ASSIGNED",
  "categoryId": 1,
  "teamId": 2,
  "assignedAgentId": 12,
  "assignmentSource": "MANUAL",
  "version": 1,
  "assignedAt": "2026-11-21T16:30:00Z"
}
```

---

### PHÂN HỆ 5: `sla` — Theo dõi SLA, Cảnh báo, Chuyển cấp & Thông báo (UC05)

#### Bảng tổng hợp Endpoint
| Method | Endpoint | Roles | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/sla/policies` | `SUPERVISOR`, `ADMIN` | Danh sách `sla_policy` (kèm 2 `targets`: `FIRST_RESPONSE`, `RESOLUTION`) |
| `POST` | `/api/v1/admin/sla/policies` | `ADMIN` | Tạo phiên bản mới `sla_policy` theo `priority` (tự tăng `version`, deactivate bản cũ) |
| `GET` | `/api/v1/tickets/{ticketId}/sla` | `AGENT`, `SUPERVISOR`, `ADMIN` | Chi tiết 2 mốc `ticket_sla`, `pauses[]`, `events[]` (`WARNING`/`BREACH`), `escalationActions[]` |
| `GET` | `/api/v1/sla/escalations` | `SUPERVISOR`, `ADMIN` | Danh sách `sla_event` cần xử lý chuyển cấp (`?eventType=BREACH&handled=false&teamId=`) |
| `POST` | `/api/v1/sla/events/{slaEventId}/escalations` | `SUPERVISOR`, `ADMIN` | Ghi nhận hành động xử lý chuyển cấp (`escalation_action`) |
| `GET` | `/api/v1/notifications` | `AGENT`, `SUPERVISOR`, `ADMIN` | Danh sách thông báo của người dùng hiện tại (`?unreadOnly=true&page=0&size=20`) |
| `PATCH` | `/api/v1/notifications/{notificationId}/read` | `AGENT`, `SUPERVISOR`, `ADMIN` | Đánh dấu 1 thông báo đã đọc (`read_at`) |
| `POST` | `/api/v1/notifications/read-all` | `AGENT`, `SUPERVISOR`, `ADMIN` | Đánh dấu toàn bộ thông báo của user hiện tại đã đọc |

#### DTO trọng tâm Phân hệ 5

**`POST /api/v1/admin/sla/policies`**
- Request:
```json
{
  "name": "Chính sách SLA 24/7 - Khẩn cấp v2",
  "priority": "URGENT",
  "targets": [
    { "metric": "FIRST_RESPONSE", "targetSeconds": 900, "warningRatio": 0.8000 },
    { "metric": "RESOLUTION", "targetSeconds": 14400, "warningRatio": 0.8000 }
  ]
}
```
- Response `201 Created`: Trả về `SlaPolicyDto` với `version` mới tạo và `isActive: true`.

**`POST /api/v1/sla/events/{slaEventId}/escalations`**
- Request:
```json
{
  "action": "REASSIGNED_TO_SENIOR_AGENT",
  "note": "Đã chuyển cho Agent cấp cao và ưu tiên xử lý trong 15 phút."
}
```
- Response `201 Created`:
```json
{
  "id": 301,
  "slaEventId": 880,
  "handledBy": { "id": 5, "fullName": "Lê Quốc Bảo" },
  "action": "REASSIGNED_TO_SENIOR_AGENT",
  "note": "Đã chuyển cho Agent cấp cao và ưu tiên xử lý trong 15 phút.",
  "handledAt": "2026-11-21T16:40:00Z"
}
```

---

### PHÂN HỆ 6: `knowledge` & `reporting` — Knowledge Base, AI RAG & Báo cáo (UC06–UC08)

#### 6A. Knowledge Base (UC06)

| Method | Endpoint | Roles | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/customer/kb/articles` | Public / `CUSTOMER` | Tra cứu bài KB công khai (`access_scope = PUBLIC` & đang `PUBLISHED`) |
| `GET` | `/api/v1/kb/articles` | `AGENT`, `SUPERVISOR`, `ADMIN` | Danh sách bài viết KB (`?categoryId=&accessScope=&status=&q=&page=0&size=20`) |
| `GET` | `/api/v1/kb/articles/{articleId}` | `AGENT`, `SUPERVISOR`, `ADMIN` | Chi tiết `kb_article` kèm toàn bộ lịch sử `versions[]` |
| `POST` | `/api/v1/kb/articles` | `AGENT`, `SUPERVISOR`, `ADMIN` | Tạo `kb_article` + `kb_version` v1 (`DRAFT`) |
| `POST` | `/api/v1/kb/articles/{articleId}/versions` | `AGENT`, `SUPERVISOR`, `ADMIN` | Tạo `kb_version` mới (`DRAFT`, không ghi đè bản `PUBLISHED`) |
| `PUT` | `/api/v1/kb/versions/{versionId}` | `AGENT` (tác giả), `SUPERVISOR`, `ADMIN` | Sửa nội dung phiên bản khi `status IN ('DRAFT', 'REJECTED')` |
| `POST` | `/api/v1/kb/versions/{versionId}/submit` | `AGENT`, `SUPERVISOR`, `ADMIN` | Gửi duyệt (`DRAFT`/`REJECTED` → `IN_REVIEW`) |
| `POST` | `/api/v1/kb/versions/{versionId}/publish` | `SUPERVISOR`, `ADMIN` | Phê duyệt & Xuất bản (`IN_REVIEW → PUBLISHED`), tự archive bản cũ & tạo `kb_chunk(is_active=true)` |
| `POST` | `/api/v1/kb/versions/{versionId}/reject` | `SUPERVISOR`, `ADMIN` | Từ chối duyệt (`IN_REVIEW → REJECTED`, bắt buộc `reviewNote`) |
| `POST` | `/api/v1/kb/versions/{versionId}/revoke` | `SUPERVISOR`, `ADMIN` | Thu hồi bài viết (`PUBLISHED → ARCHIVED`, đặt `kb_chunk.is_active = false` ngay lập tức) |
| `GET` | `/api/v1/kb/chunks/{chunkId}` | `AGENT`, `SUPERVISOR`, `ADMIN` | Xem chi tiết đoạn `kb_chunk` phục vụ preview trích dẫn AI |

**`POST /api/v1/kb/articles`**
- Request:
```json
{
  "articleCode": "KB-PAY-001",
  "categoryId": 1,
  "accessScope": "PUBLIC",
  "allowAutoReply": true,
  "title": "Hướng dẫn xử lý thanh toán chuyển khoản chưa kích hoạt",
  "summary": "Các bước kiểm tra mã giao dịch và thời gian kích hoạt tự động.",
  "content": "## 1. Kiểm tra mã giao dịch\n..."
}
```
- Response `201 Created`: Trả `KbArticleDetailDto` kèm `versionId`, `versionNumber: 1`, `status: "DRAFT"`.

**`POST /api/v1/kb/versions/{versionId}/reject`**
- Request: `{ "reviewNote": "Thiếu hướng dẫn trường hợp thanh toán vào cuối tuần." }` (bắt buộc không rỗng, nếu thiếu → `422`).

**`POST /api/v1/kb/versions/{versionId}/revoke`**
- Request: `{ "reason": "Chính sách hoàn tiền đã thay đổi từ 22/11/2026." }`
- Response `200 OK`: `{ "versionId": 71, "status": "ARCHIVED", "deactivatedChunks": 6, "archivedAt": "2026-11-21T16:50:00Z" }`

---

#### 6B. AI RAG Gợi ý có dẫn nguồn (UC07)

| Method | Endpoint | Roles | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/tickets/{ticketId}/ai/suggestions` | `AGENT`, `SUPERVISOR` | Chạy AI RAG sinh gợi ý nháp có dẫn nguồn (chỉ đọc tin `PUBLIC` + `kb_chunk` đang `PUBLISHED` & `is_active = true`) |
| `GET` | `/api/v1/tickets/{ticketId}/ai/suggestions` | `AGENT`, `SUPERVISOR`, `ADMIN` | Lấy danh sách các lần chạy `ai_run` kèm `citations[]` và `feedback` của ticket |
| `POST` | `/api/v1/ai/runs/{aiRunId}/feedback` | `AGENT`, `SUPERVISOR` | Ghi nhận quyết định của Agent vào `ai_suggestion_feedback` |

**`POST /api/v1/tickets/{ticketId}/ai/suggestions`**
- Request (tùy chọn):
```json
{
  "customQuery": null,
  "topK": 5
}
```
- Response `201 Created` (Trường hợp có nguồn hợp lệ — `SUGGESTED`):
```json
{
  "id": 7001,
  "ticketId": 1042,
  "taskType": "RAG_SUGGESTION",
  "modelName": "gpt-4o-mini",
  "promptVersion": "rag-v1.0",
  "outcome": "SUGGESTED",
  "answerText": "Thông thường giao dịch chuyển khoản sẽ tự động kích hoạt sau 5–15 phút. Bạn vui lòng kiểm tra lại đúng nội dung chuyển khoản và gửi ảnh biên lai nếu quá 15 phút.",
  "confidence": 0.8900,
  "fallbackReason": null,
  "latencyMs": 1420,
  "createdAt": "2026-11-21T16:18:00Z",
  "citations": [
    {
      "id": 8101,
      "kbChunkId": 102,
      "kbVersionId": 70,
      "articleId": 25,
      "articleCode": "KB-PAY-001",
      "articleTitle": "Hướng dẫn xử lý thanh toán chuyển khoản chưa kích hoạt",
      "heading": "1. Thời gian kích hoạt tự động",
      "excerpt": "Giao dịch chuyển khoản hợp lệ sẽ được hệ thống đối soát và kích hoạt trong vòng 5–15 phút...",
      "relevanceScore": 0.9320,
      "citationOrder": 1
    }
  ],
  "feedback": null
}
```
- Response `201 Created` (Trường hợp Fallback an toàn — `FALLBACK_TO_AGENT`, không bịa câu trả lời):
```json
{
  "id": 7002,
  "ticketId": 1046,
  "taskType": "RAG_SUGGESTION",
  "modelName": "gpt-4o-mini",
  "promptVersion": "rag-v1.0",
  "outcome": "FALLBACK_TO_AGENT",
  "answerText": null,
  "confidence": 0.2100,
  "fallbackReason": "NO_RELEVANT_KB",
  "latencyMs": 310,
  "createdAt": "2026-11-21T16:19:00Z",
  "citations": [],
  "feedback": null
}
```

**`POST /api/v1/ai/runs/{aiRunId}/feedback`**
- Request:
```json
{
  "decision": "EDITED_AND_SENT",
  "note": "Bổ sung thêm mã đơn hàng của khách trước khi gửi."
}
```
- Response `201 Created` (mỗi `aiRunId` chỉ nhận 1 feedback theo `uq_ai_feedback_run`; gửi lần 2 trả `409 DUPLICATE_RESOURCE`).

---

#### 6C. Báo cáo Ticket, SLA & CSAT (UC08)

| Method | Endpoint | Roles | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/reports/overview` | `SUPERVISOR` (nhóm mình), `ADMIN` (toàn hệ thống) | Báo cáo hợp nhất 3 khối KPI (`Ticket`, `SLA`, `CSAT`) trên cùng mốc `asOf` |

**Query Parameters:**
- `days`: integer từ `1` đến `365`, mặc định `30`. Ngoài biên → `422 INVALID_REPORT_DAYS`.
- `teamId`: tùy chọn (`BIGINT`). Với `ADMIN`, bỏ trống = toàn hệ thống. Với `SUPERVISOR`, chỉ được truyền `teamId` thuộc nhóm mình quản lý (truyền nhóm khác → `403 TEAM_SCOPE_DENIED`).

**Response `200 OK`** (`GET /api/v1/reports/overview?days=30`):
```json
{
  "period": {
    "days": 30,
    "from": "2026-10-22T16:00:00.000000Z",
    "asOf": "2026-11-21T16:00:00.000000Z",
    "displayTimezone": "Asia/Ho_Chi_Minh"
  },
  "scope": {
    "teamId": null,
    "teamName": "Toàn hệ thống"
  },
  "ticketMetrics": {
    "totalTickets": 240,
    "openTickets": 38,
    "byStatus": [
      { "status": "NEW", "count": 6 },
      { "status": "ASSIGNED", "count": 10 },
      { "status": "IN_PROGRESS", "count": 14 },
      { "status": "WAITING_CUSTOMER", "count": 8 },
      { "status": "RESOLVED", "count": 42 },
      { "status": "CLOSED", "count": 150 },
      { "status": "CANCELLED", "count": 10 }
    ],
    "byCategory": [
      { "categoryId": 1, "categoryCode": "PAYMENT", "categoryName": "Thanh toán", "count": 95 },
      { "categoryId": 2, "categoryCode": "TECHNICAL", "categoryName": "Kỹ thuật", "count": 80 }
    ]
  },
  "slaMetrics": {
    "avgFirstResponseSeconds": 845.5,
    "avgNetResolutionSeconds": 12420.0,
    "complianceRatePercent": 92.5,
    "eligibleSlaCount": 400,
    "metSlaCount": 370,
    "overdueTicketCount": 4,
    "recentAlerts": [
      {
        "slaEventId": 880,
        "ticketId": 1042,
        "ticketCode": "HD-2026-001042",
        "subject": "Đã chuyển khoản nhưng tài khoản chưa kích hoạt",
        "priority": "HIGH",
        "metric": "RESOLUTION",
        "eventType": "WARNING",
        "deadlineAt": "2026-11-22T00:05:10Z",
        "createdAt": "2026-11-21T22:30:00Z",
        "handled": false
      }
    ]
  },
  "csatMetrics": {
    "averageScore": 4.62,
    "submittedCount": 118,
    "distribution": [
      { "score": 5, "count": 85 },
      { "score": 4, "count": 23 },
      { "score": 3, "count": 7 },
      { "score": 2, "count": 2 },
      { "score": 1, "count": 1 }
    ]
  }
}
```
*(Ghi chú UC08-E2: Khi kỳ báo cáo không có dữ liệu, các bộ đếm trả về `0`, các giá trị trung bình / tỷ lệ như `avgFirstResponseSeconds`, `avgNetResolutionSeconds`, `complianceRatePercent`, `averageScore` trả về `null` để Frontend hiển thị `—`.)*

---

## 4. Ma trận đối chiếu 8 Ca Kiểm thử Trọng tâm với Hợp đồng API

| STT | Ca kiểm thử bắt buộc | Endpoint kiểm chứng chính | Kỳ vọng HTTP & Hành vi |
|---|---|---|---|
| 1 | **Chống trùng Webhook / Intake** | `POST /api/v1/intake/web-requests`, `POST /api/v1/webhooks/{channelCode}` | 2 request cùng `Idempotency-Key` / `externalEventId` → chỉ tạo 1 `ticket`, request 2 trả `duplicate: true`; khác payload → `409`. |
| 2 | **Cách ly ghi chú nội bộ (`INTERNAL`)** | `GET /api/v1/customer/tickets/{id}/messages` vs `POST /api/v1/tickets/{id}/messages` | Customer tuyệt đối không thấy tin `INTERNAL`; gọi endpoint nhân viên nhận `403`. |
| 3 | **Xung đột đồng thời (`@Version`)** | `PATCH /api/v1/tickets/{id}/status`, `POST /api/v1/tickets/{id}/transfer` | 2 Agent gửi cùng `version` → 1 nhận `200`, 1 nhận `409 OPTIMISTIC_LOCK_CONFLICT` kèm `currentVersion`. |
| 4 | **Phân quyền RBAC & Ownership** | `GET /api/v1/customer/tickets/{otherId}`, `POST /api/v1/kb/versions/{id}/publish`, `GET /api/v1/reports/overview` | Customer xem ticket người khác → `403`; Agent gọi publish KB / xem báo cáo → `403`. |
| 5 | **AI Classification & Routing Fallback** | `GET /api/v1/routing/review-queue`, `POST /api/v1/routing/tickets/{id}/assign` | Khi LLM lỗi/confidence thấp/hết Agent → ticket ở `NEW`, xuất hiện trong `review-queue` để Supervisor gán thủ công. |
| 6 | **AI RAG Fallback & Chống rò rỉ** | `POST /api/v1/tickets/{id}/ai/suggestions` | Không có KB khớp / prompt injection → `outcome = FALLBACK_TO_AGENT`, `answerText = null`, `citations = []`. |
| 7 | **Thu hồi KB (Revocation)** | `POST /api/v1/kb/versions/{id}/revoke` → `POST /api/v1/tickets/{id}/ai/suggestions` | Sau khi revoke, `deactivatedChunks > 0` và mọi truy vấn RAG sau đó không còn dẫn nguồn từ bản bị thu hồi. |
| 8 | **Chống trùng cảnh báo SLA** | `GET /api/v1/tickets/{id}/sla`, `GET /api/v1/sla/escalations` | Scheduler chạy lặp nhiều lần cho ticket vượt 80% / quá hạn → chỉ sinh đúng 1 `sla_event(WARNING)` và 1 `sla_event(BREACH)`. |
