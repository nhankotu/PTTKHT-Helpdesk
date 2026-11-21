# Tài liệu Mô tả & Hướng dẫn Kiểm thử REST API — Omnichannel AI Helpdesk

> **Spec chi tiết thiết kế:** [04_Hop_Dong_REST_API.md](../../Thiet_ke/04_Hop_Dong_REST_API.md)  
> **OpenAPI 3.0 Spec (live export):** [openapi.json](./openapi.json)  
> **Swagger UI (khi backend đang chạy):** `http://localhost:8080/swagger-ui.html`  
> **Postman Collection:** [Helpdesk_API.postman_collection.json](./postman/Helpdesk_API.postman_collection.json)  
> **Postman Environment:** [Helpdesk_Local.postman_environment.json](./postman/Helpdesk_Local.postman_environment.json)

---

## 1. Thông tin môi trường Local

- **Base URL:** `http://localhost:8080`
- **Health Check:** `GET http://localhost:8080/actuator/health`
- **MySQL 8.0 (Docker):** `localhost:3307` (`helpdesk` / `helpdesk_user` / `helpdesk_pass`)
- **Webhook Secret mặc định (dev):** `X-Webhook-Signature: dev-webhook-secret`
- **4 Tài khoản mẫu (profile `dev`, mật khẩu chung: `Password123!`):**

| Vai trò | Email | Phạm vi chính |
|---|---|---|
| `ADMIN` | `admin@helpdesk.local` | Toàn hệ thống, cấu hình luật routing, SLA policy, báo cáo toàn cục |
| `SUPERVISOR` | `supervisor@helpdesk.local` | Duyệt/xuất bản/thu hồi KB, phân công thủ công, xử lý escalation, báo cáo nhóm |
| `AGENT` | `agent.an@helpdesk.local` | Xử lý ticket, ghi chú `INTERNAL`, chạy gợi ý AI RAG, gửi phản hồi AI |
| `CUSTOMER` | `customer@helpdesk.local` | Gửi yêu cầu, xem ticket của mình (chỉ tin `PUBLIC`), xác nhận đóng, đánh giá CSAT |

---

## 2. Hướng dẫn Import & Chạy trên Postman

1. Mở **Postman** → bấm **Import** → chọn cả 2 file trong `backend/docs/postman/`:
   - `Helpdesk_API.postman_collection.json`
   - `Helpdesk_Local.postman_environment.json`
2. Ở góc trên bên phải Postman, chọn Environment **Helpdesk Local (localhost:8080)**.
3. Chạy 1 request đăng nhập trong thư mục `1. Identity & Auth` (ví dụ **Login as Supervisor**) — Test Script tự động lưu `{{accessToken}}` và `{{refreshToken}}` vào Environment cho toàn bộ các request còn lại.
4. Khi chạy tạo Ticket / KB / AI RAG, Test Script tự động cập nhật `{{ticketId}}`, `{{ticketVersion}}`, `{{kbArticleId}}`, `{{kbVersionId}}`, `{{aiRunId}}`, `{{kbChunkId}}`.

---

## 3. Danh mục 72 REST Endpoints theo 6 Phân hệ (UC00–UC08)

### Phân hệ 1 — `identity` (UC00 / FR01) — 20 endpoints
| Method | Endpoint | Roles | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Public | Đăng nhập, trả Access & Refresh JWT |
| `POST` | `/api/v1/auth/register` | Public | Đăng ký tài khoản `CUSTOMER` (tự tạo bản ghi `customer`) |
| `POST` | `/api/v1/auth/refresh` | Public | Cấp lại Access Token từ Refresh Token |
| `POST` | `/api/v1/auth/logout` | Authenticated | Đăng xuất |
| `GET` | `/api/v1/auth/me` | Authenticated | Thông tin hồ sơ người dùng hiện tại |
| `PATCH` | `/api/v1/auth/me/availability` | `AGENT`, `SUPERVISOR`, `ADMIN` | Bật/tắt trạng thái sẵn sàng nhận ticket |
| `GET` | `/api/v1/categories` | Public | Danh sách danh mục yêu cầu đang hoạt động |
| `GET` | `/api/v1/teams` | Authenticated | Danh sách nhóm hỗ trợ và thành viên |
| `GET` | `/api/v1/admin/users` | `ADMIN`, `SUPERVISOR` | Danh sách tài khoản (lọc `role`, `teamId`, `isActive`, `isAvailable`) |
| `POST` | `/api/v1/admin/users` | `ADMIN` | Tạo tài khoản nhân viên / người dùng mới |
| `PATCH` | `/api/v1/admin/users/{userId}` | `ADMIN` | Cập nhật thông tin, khóa/mở khóa, đổi vai trò |
| `POST` | `/api/v1/admin/teams` | `ADMIN` | Tạo nhóm hỗ trợ mới |
| `PATCH` | `/api/v1/admin/teams/{teamId}` | `ADMIN` | Cập nhật tên, trưởng nhóm, trạng thái nhóm |
| `PUT` | `/api/v1/admin/teams/{teamId}/members/{userId}` | `ADMIN`, `SUPERVISOR` | Thêm thành viên vào nhóm hỗ trợ |
| `DELETE` | `/api/v1/admin/teams/{teamId}/members/{userId}` | `ADMIN`, `SUPERVISOR` | Xóa thành viên khỏi nhóm hỗ trợ |
| `POST` | `/api/v1/admin/categories` | `ADMIN` | Tạo danh mục yêu cầu mới |
| `PATCH` | `/api/v1/admin/categories/{categoryId}` | `ADMIN` | Cập nhật tên/trạng thái danh mục |
| `GET` | `/api/v1/admin/channels` | `ADMIN`, `SUPERVISOR` | Danh sách kênh tiếp nhận và tài khoản kênh |
| `POST` | `/api/v1/admin/channel-accounts` | `ADMIN` | Cấu hình tài khoản kênh mới |
| `PATCH` | `/api/v1/admin/channel-accounts/{id}` | `ADMIN` | Bật/tắt hoặc cập nhật tài khoản kênh |

### Phân hệ 2 — `intake` & `attachments` (UC01–UC02) — 6 endpoints
| Method | Endpoint | Roles | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/attachments` | Authenticated | Upload tệp đính kèm (`multipart/form-data`, ≤ 25MB) |
| `GET` | `/api/v1/attachments/{attachmentId}/download` | Authenticated | Tải tệp đính kèm (chặn `CUSTOMER` tải file thuộc tin `INTERNAL`) |
| `POST` | `/api/v1/intake/web-requests` | Public / `CUSTOMER` | Tiếp nhận Web Form (bắt buộc header `Idempotency-Key`, trả `202 Accepted`) |
| `GET` | `/api/v1/intake/web-requests/{requestId}` | Authenticated | Tra cứu trạng thái `inbound_event` |
| `POST` | `/api/v1/webhooks/{channelCode}` | Webhook (`X-Webhook-Signature`) | Nhận webhook từ `EMAIL`, `LIVE_CHAT`, `FACEBOOK`, `ZALO` (chống trùng idempotent) |
| `GET` | `/api/v1/admin/inbound-events` | `ADMIN`, `SUPERVISOR` | Giám sát nhật ký sự kiện tiếp nhận |

### Phân hệ 3 — `ticket` (UC03) — 16 endpoints
**Cổng Khách hàng (`/api/v1/customer/tickets` — chỉ `CUSTOMER`, chỉ thấy tin `PUBLIC`):**
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/api/v1/customer/tickets` | Danh sách ticket của chính khách hàng |
| `GET` | `/api/v1/customer/tickets/{ticketId}` | Chi tiết ticket (kiểm tra ownership) |
| `GET` | `/api/v1/customer/tickets/{ticketId}/messages` | Hội thoại công khai (cách ly tuyệt đối `INTERNAL`) |
| `POST` | `/api/v1/customer/tickets/{ticketId}/messages` | Khách hàng gửi phản hồi |
| `POST` | `/api/v1/customer/tickets/{ticketId}/confirm-close` | Xác nhận đóng ticket (`RESOLVED → CLOSED`) |
| `POST` | `/api/v1/customer/tickets/{ticketId}/reopen` | Mở lại ticket đã giải quyết/đóng |
| `POST` | `/api/v1/customer/tickets/{ticketId}/csat` | Gửi đánh giá CSAT (`score: 1..5`) |

**Không gian Nhân viên (`/api/v1/tickets` — `AGENT`, `SUPERVISOR`, `ADMIN`):**
| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/api/v1/tickets` | Danh sách ticket theo bộ lọc & phân trang |
| `GET` | `/api/v1/tickets/{ticketId}` | Chi tiết ticket kèm `version` và `allowedTransitions` |
| `GET` | `/api/v1/tickets/{ticketId}/messages` | Toàn bộ hội thoại (`PUBLIC` + `INTERNAL`) |
| `POST` | `/api/v1/tickets/{ticketId}/messages` | Gửi phản hồi `PUBLIC` hoặc ghi chú `INTERNAL` |
| `POST` | `/api/v1/tickets/{ticketId}/messages/{messageId}/retry` | Thử gửi lại tin nhắn outbound bị lỗi |
| `PATCH` | `/api/v1/tickets/{ticketId}/status` | Đổi trạng thái qua State Machine Guard (bắt buộc `version`) |
| `PATCH` | `/api/v1/tickets/{ticketId}/metadata` | Đổi `priority` (`SUPERVISOR`/`ADMIN`) / `categoryId` (bắt buộc `version`) |
| `POST` | `/api/v1/tickets/{ticketId}/transfer` | Chuyển giao nhóm/Agent (bắt buộc `version`) |
| `GET` | `/api/v1/tickets/{ticketId}/events` | Lịch sử kiểm toán thay đổi ticket |

### Phân hệ 4 — `routing` (UC04) — 7 endpoints
| Method | Endpoint | Roles | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/routing/rules` | `SUPERVISOR`, `ADMIN` | Danh sách luật định tuyến |
| `POST` | `/api/v1/admin/routing/rules` | `ADMIN` | Tạo luật định tuyến theo danh mục → nhóm |
| `PATCH` | `/api/v1/admin/routing/rules/{ruleId}` | `ADMIN` | Cập nhật luật định tuyến |
| `GET` | `/api/v1/routing/review-queue` | `SUPERVISOR`, `ADMIN` | Hàng đợi fallback cần Supervisor xử lý |
| `POST` | `/api/v1/routing/tickets/{ticketId}/assign` | `SUPERVISOR`, `ADMIN` | Phân công thủ công vào nhóm/Agent |
| `POST` | `/api/v1/routing/tickets/{ticketId}/reclassify` | `SUPERVISOR`, `ADMIN` | Kích hoạt AI phân loại lại (Gemini 2.5 Flash / Heuristic) |
| `GET` | `/api/v1/routing/tickets/{ticketId}/classifications` | `SUPERVISOR`, `ADMIN` | Lịch sử phân loại AI của ticket |

### Phân hệ 5 — `sla` (UC05) — 8 endpoints
| Method | Endpoint | Roles | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/sla/policies` | `SUPERVISOR`, `ADMIN` | Danh sách chính sách SLA (2 mốc `FIRST_RESPONSE` & `RESOLUTION`) |
| `POST` | `/api/v1/admin/sla/policies` | `ADMIN` | Tạo phiên bản chính sách SLA mới |
| `GET` | `/api/v1/tickets/{ticketId}/sla` | `AGENT`, `SUPERVISOR`, `ADMIN` | Tiến độ 2 mốc SLA, lịch sử pause & cảnh báo |
| `GET` | `/api/v1/sla/escalations` | `SUPERVISOR`, `ADMIN` | Danh sách sự kiện cảnh báo 80% / vi phạm SLA |
| `POST` | `/api/v1/sla/events/{slaEventId}/escalations` | `SUPERVISOR`, `ADMIN` | Ghi nhận hành động xử lý chuyển cấp |
| `GET` | `/api/v1/notifications` | `AGENT`, `SUPERVISOR`, `ADMIN` | Hộp thông báo của nhân viên |
| `PATCH` | `/api/v1/notifications/{notificationId}/read` | `AGENT`, `SUPERVISOR`, `ADMIN` | Đánh dấu đã đọc 1 thông báo |
| `POST` | `/api/v1/notifications/read-all` | `AGENT`, `SUPERVISOR`, `ADMIN` | Đánh dấu đã đọc tất cả thông báo |

### Phân hệ 6 — `knowledge` & `reporting` (UC06–UC08) — 15 endpoints
**6A. Knowledge Base (UC06 — 11 endpoints):**
| Method | Endpoint | Roles | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/customer/kb/articles` | Public / `CUSTOMER` | Tra cứu bài KB công khai đang `PUBLISHED` |
| `GET` | `/api/v1/kb/articles` | `AGENT`, `SUPERVISOR`, `ADMIN` | Danh sách toàn bộ bài viết KB |
| `GET` | `/api/v1/kb/articles/{articleId}` | `AGENT`, `SUPERVISOR`, `ADMIN` | Chi tiết bài KB kèm toàn bộ phiên bản |
| `POST` | `/api/v1/kb/articles` | `AGENT`, `SUPERVISOR`, `ADMIN` | Tạo bài KB + phiên bản v1 (`DRAFT`) |
| `POST` | `/api/v1/kb/articles/{articleId}/versions` | `AGENT`, `SUPERVISOR`, `ADMIN` | Tạo phiên bản `DRAFT` mới |
| `PUT` | `/api/v1/kb/versions/{versionId}` | `AGENT`, `SUPERVISOR`, `ADMIN` | Sửa phiên bản ở trạng thái `DRAFT` / `REJECTED` |
| `POST` | `/api/v1/kb/versions/{versionId}/submit` | `AGENT`, `SUPERVISOR`, `ADMIN` | Gửi duyệt (`DRAFT/REJECTED → IN_REVIEW`) |
| `POST` | `/api/v1/kb/versions/{versionId}/publish` | `SUPERVISOR`, `ADMIN` | Xuất bản (`IN_REVIEW → PUBLISHED`), tự tách `kb_chunk` |
| `POST` | `/api/v1/kb/versions/{versionId}/reject` | `SUPERVISOR`, `ADMIN` | Từ chối duyệt (`IN_REVIEW → REJECTED`) |
| `POST` | `/api/v1/kb/versions/{versionId}/revoke` | `SUPERVISOR`, `ADMIN` | Thu hồi (`PUBLISHED → ARCHIVED`), vô hiệu hóa `kb_chunk` ngay lập tức |
| `GET` | `/api/v1/kb/chunks/{chunkId}` | `AGENT`, `SUPERVISOR`, `ADMIN` | Xem chi tiết đoạn chunk trích dẫn AI |

**6B. AI RAG (UC07 — 3 endpoints):**
| Method | Endpoint | Roles | Mô tả |
|---|---|---|---|
| `POST` | `/api/v1/tickets/{ticketId}/ai/suggestions` | `AGENT`, `SUPERVISOR` | Chạy AI RAG (Gemini 2.5 Flash) có kiểm chứng `ai_citation` & fallback an toàn |
| `GET` | `/api/v1/tickets/{ticketId}/ai/suggestions` | `AGENT`, `SUPERVISOR`, `ADMIN` | Lịch sử gợi ý AI của ticket |
| `POST` | `/api/v1/ai/runs/{aiRunId}/feedback` | `AGENT`, `SUPERVISOR` | Đánh giá gợi ý AI (`ACCEPTED_AS_IS`, `EDITED_AND_SENT`, `REJECTED`) |

**6C. Báo cáo (UC08 — 1 endpoint):**
| Method | Endpoint | Roles | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/reports/overview?days=30&teamId=` | `SUPERVISOR`, `ADMIN` | Báo cáo hợp nhất 3 khối KPI (`Ticket`, `SLA` từ `v_ticket_sla_summary`, `CSAT`) |

---

## 4. Quy ước Lỗi chuẩn RFC 7807 (`application/problem+json`)

Mọi lỗi đều trả về JSON theo chuẩn `ProblemDetail`:
```json
{
  "type": "urn:helpdesk:error:OPTIMISTIC_LOCK_CONFLICT",
  "title": "Conflict",
  "status": 409,
  "detail": "Ticket đã được cập nhật bởi người khác. Vui lòng tải lại dữ liệu mới nhất.",
  "errorCode": "OPTIMISTIC_LOCK_CONFLICT",
  "timestamp": "2026-11-21T18:45:00Z"
}
```

Các mã lỗi nghiệp vụ quan trọng:
- `401 UNAUTHORIZED` / `INVALID_CREDENTIALS` / `INVALID_WEBHOOK_SIGNATURE`
- `403 FORBIDDEN` / `TICKET_ACCESS_DENIED` / `TEAM_SCOPE_DENIED`
- `409 OPTIMISTIC_LOCK_CONFLICT` / `IDEMPOTENCY_KEY_PAYLOAD_MISMATCH` / `DUPLICATE_RESOURCE`
- `422 INVALID_STATE_TRANSITION` / `INVALID_REPORT_DAYS` / `KB_VERSION_NOT_EDITABLE`
