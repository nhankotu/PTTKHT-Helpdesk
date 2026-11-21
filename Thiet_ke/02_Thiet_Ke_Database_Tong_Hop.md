# TÀI LIỆU THIẾT KẾ CƠ SỞ DỮ LIỆU TỔNG HỢP (UNIFIED DATABASE DESIGN)

**Đề tài 07:** Nền tảng Helpdesk đa kênh tích hợp AI  
**Phiên bản:** 1.1 — **MySQL 8.0+ (InnoDB)** (Hợp nhất toàn bộ 6 phân hệ — UC01 đến UC08 + FR01)  
**Ngày cập nhật:** 21/11/2026  
**DDL nguồn chính thức:** [helpdesk_unified_schema_mysql.sql](./helpdesk_unified_schema_mysql.sql)  
**Sơ đồ tổng quan ERD:** [ERD_Tong_The.png](./Bieu_do/Tong_the/ERD_Tong_The.png)

---

## 1. Quy ước kỹ thuật thống nhất (xử lý xung đột DB)

| Hạng mục | Quy ước chốt toàn hệ thống (MySQL 8.0+) | Xung đột đã được xử lý |
|---|---|---|
| **Hệ quản trị CSDL** | **MySQL 8.0+ (InnoDB, `utf8mb4_unicode_ci`)** | Thống nhất toàn bộ các phân hệ (thay cho PostgreSQL ở UC01–07 và SQLite ở UC08). |
| **Kiểu khóa chính (PK)** | `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY` | Thống nhất với kiểu `BIGINT` tự tăng ở UC01–02, UC04–07; thay cho `UUID` ở UC03. Các mã hiển thị dùng cột `UNIQUE` riêng (`ticket_code`, `article_code`). |
| **Kiểu thời gian** | `DATETIME(6)` lưu UTC (hiển thị `Asia/Ho_Chi_Minh`) | Tránh giới hạn năm 2038 của `TIMESTAMP` trong MySQL; thay cho `TIMESTAMPTZ` (PostgreSQL), `TIMESTAMP` (UC03) và Unix epoch `REAL` (UC08). |
| **Kiểu JSON & Số thực** | `JSON`, `DECIMAL(5,4)`, `TINYINT` (điểm 1–5), `LONGTEXT` (nội dung KB) | Chuyển đổi chuẩn từ `JSONB`, `NUMERIC(5,4)`, `SMALLINT` của PostgreSQL sang MySQL 8.0. |
| **Ràng buộc duy nhất có điều kiện** | **Generated Column (`STORED`) + `UNIQUE KEY`** | MySQL 8.0 không hỗ trợ `CREATE UNIQUE INDEX ... WHERE ...`; dùng cột sinh tự động trả `NULL` khi không thỏa điều kiện (VD: `active_category_key`, `active_priority_key`, `open_pause_key`, `published_article_key`). |
| **Quy ước đặt tên** | `snake_case`, tên bảng **số ít**, PK là `id`, FK là `<entity>_id` | Xóa bỏ tình trạng lẫn lộn `ticket`/`tickets`, `users`/`app_user`, `id`/`ticket_id`. |
| **Chống sửa đồng thời** | `ticket.version INT NOT NULL DEFAULT 0` | Bổ sung vào thiết kế UC03 và UC01–02 SQL vốn bị thiếu trước đây. |
| **Phân quyền & Nhóm** | Chuẩn hóa 3NF với `role`, `user_role`, `support_team`, `team_member` | Thay cho cột `role` đơn (UC04–05) và `USERS.team_id` 1-N (UC08) để hỗ trợ đa vai trò và 1 nhân viên thuộc nhiều nhóm. |
| **Cấu trúc SLA** | Tách chuẩn hóa 1 ticket – 2 dòng `ticket_sla` (`FIRST_RESPONSE`, `RESOLUTION`) + `sla_pause` + `sla_event` + View `v_ticket_sla_summary` | Loại bỏ 3 cột SLA nhúng thừa trong `tickets` của UC03; cung cấp View `v_ticket_sla_summary` để UC08 truy vấn báo cáo trực tiếp mà không sai lệch. |

---

## 2. Bảng ánh xạ từ thiết kế cũ sang Lược đồ hợp nhất (34 bảng + 1 View)

| Phân hệ | Bảng hợp nhất (34 bảng) | Nguồn cũ đã được hợp nhất / thay thế |
|---|---|---|
| **1. Tài khoản & RBAC (5 bảng)** | `app_user`, `role`, `user_role`, `support_team`, `team_member` | Thay thế `users`/`support_groups` (UC03), `app_user` (UC04–05), `USERS`/`TEAMS` (UC08). |
| **2. Tiếp nhận đa kênh (6 bảng)** | `channel`, `channel_account`, `customer`, `customer_identity`, `conversation`, `category` | Hợp nhất từ UC01–02 (10 bảng), `category` (UC04–05), `CATEGORIES` (UC08). |
| **3. Ticket & Hội thoại (6 bảng)** | `ticket`, `ticket_message`, `attachment`, `inbound_event`, `outbox_event`, `ticket_event` | Hợp nhất `ticket`/`tickets`, `ticket_message`/`ticket_messages`, `attachment`/`ticket_attachments`, `ticket_audit_logs` → `ticket_event`. |
| **4. Phân loại & Định tuyến (3 bảng)** | `routing_rule`, `classification_result`, `assignment_history` | Giữ nguyên thiết kế chuẩn hóa của UC04 (TV3). |
| **5. SLA & Thông báo (7 bảng)** | `sla_policy`, `sla_policy_target`, `ticket_sla`, `sla_pause`, `sla_event`, `escalation_action`, `notification` | Giữ khung chuẩn hóa của UC05 (TV3); thay thế `SLA_POLICIES`/`SLA_ALERTS` (UC08) và 3 cột SLA trong `tickets` (UC03). |
| **6. KB, AI RAG & CSAT (7 bảng)** | `kb_article`, `kb_version`, `kb_chunk`, `ai_run`, `ai_citation`, `ai_suggestion_feedback`, `satisfaction_rating` | Hợp nhất UC06–UC07 (TV4) + gộp `ticket_csat_surveys` (UC03) và `RATINGS` (UC08) thành `satisfaction_rating`. |
| **Báo cáo (1 View)** | `v_ticket_sla_summary` | Cầu nối dữ liệu chuẩn giữa `ticket_sla` (UC05) và truy vấn báo cáo của UC08 (TV5). |

---

## 3. Đặc tả chi tiết các nhóm bảng cốt lõi

### 3.1. Phân hệ Tài khoản, Vai trò & Nhóm hỗ trợ (FR01)
| Bảng | Khóa & Trường chính | Vai trò & Ràng buộc |
|---|---|---|
| `app_user` | `id` PK, `full_name`, `email` UQ, `password_hash`, `phone`, `is_active`, `is_available` | Tài khoản nội bộ và khách hàng đã đăng ký; `is_available` phục vụ định tuyến tự động UC04. |
| `role` | `id` PK, `code` UQ (`CUSTOMER`, `AGENT`, `SUPERVISOR`, `ADMIN`), `name` | Danh mục vai trò chuẩn hóa. |
| `user_role` | `(user_id, role_id)` PK, FK | Quan hệ N-N giữa người dùng và vai trò (không lưu chuỗi danh sách vai trò). |
| `support_team` | `id` PK, `code` UQ, `name` UQ, `supervisor_id` FK → `app_user` | Nhóm hỗ trợ và trưởng nhóm phụ trách. |
| `team_member` | `(team_id, user_id)` PK, FK | Thành viên nhóm; làm đích khóa ngoại ghép cho `ticket(team_id, assigned_agent_id)`. |

### 3.2. Phân hệ Tiếp nhận đa kênh (UC01–UC02)
| Bảng | Khóa & Trường chính | Vai trò & Ràng buộc |
|---|---|---|
| `channel` | `id` PK, `code` UQ (`WEB_FORM`, `EMAIL`, `LIVE_CHAT`, `FACEBOOK`, `ZALO`), `name`, `is_active` | Danh mục loại kênh hỗ trợ. |
| `channel_account` | `id` PK, `channel_id` FK, `account_code` UQ, `display_name`, `config_metadata` JSON | Tài khoản kênh cụ thể (VD: hộp thư `support@`, Zalo OA). Không lưu token/secret dạng plain text. |
| `customer` | `id` PK, `user_id` UQ FK (nullable), `full_name`, `email`, `phone`, `is_verified` | Hồ sơ khách hàng, hỗ trợ khách vãng lai chưa có tài khoản `app_user`. |
| `customer_identity` | `id` PK, `customer_id` FK, `channel_account_id` FK, `external_user_id` | Định danh khách trên từng kênh; `UNIQUE(channel_account_id, external_user_id)`. |
| `conversation` | `id` PK, `channel_account_id` FK, `customer_identity_id` FK, `external_thread_id` | Luồng hội thoại nguồn; `UNIQUE(channel_account_id, external_thread_id)`. |
| `category` | `id` PK, `code` UQ, `name` UQ, `is_active` | Danh mục phân loại dùng chung cho Ticket, Routing và KB. |

### 3.3. Phân hệ Ticket, Hội thoại & Kiểm toán (UC02–UC03)
| Bảng | Khóa & Trường chính | Vai trò & Ràng buộc |
|---|---|---|
| `ticket` | `id` PK, `ticket_code` UQ, `conversation_id` FK, `customer_id` FK, `parent_ticket_id` FK, `channel_id` FK, `category_id` FK, `team_id` FK, `assigned_agent_id` FK, `subject`, `description`, `status` (7 giá trị), `priority` (4 giá trị), `resolution_code`, `cancel_reason`, `resolved_at`, `closed_at`, `version` | Thực thể trung tâm. **Ràng buộc nổi bật:** `FOREIGN KEY(team_id, assigned_agent_id) REFERENCES team_member(team_id, user_id)` bảo đảm Agent luôn thuộc nhóm xử lý; `CANCELLED` bắt buộc có `cancel_reason` ≥ 10 ký tự; `RESOLVED` bắt buộc có `resolution_code`. |
| `ticket_message` | `id` PK, `conversation_id` FK, `ticket_id` FK, `sender_user_id` FK, `sender_type`, `direction`, `visibility` (`PUBLIC`/`INTERNAL`), `is_auto_ack`, `external_message_id`, `body`, `delivery_status`, `occurred_at` | Lưu hội thoại khách và ghi chú nội bộ. `CHECK` bảo đảm `INTERNAL` chỉ do `AGENT`/`SUPERVISOR`/`SYSTEM` tạo. `UNIQUE(conversation_id, external_message_id)` chống trùng tin nhắn. |
| `attachment` | `id` PK, `message_id` FK, `file_name`, `mime_type`, `size_bytes` (≤ 25MB), `storage_key` | Metadata tệp đính kèm (file vật lý lưu Object Storage S3/MinIO). |
| `inbound_event` | `id` PK, `channel_account_id` FK, `external_event_id`, `idempotency_key`, `payload_hash`, `payload_redacted`, `process_status`, `ticket_id` FK, `message_id` FK | Chống nhận trùng webhook/web form bằng `UNIQUE(channel_account_id, external_event_id)`. |
| `outbox_event` | `id` PK, `aggregate_type`, `aggregate_id`, `event_type`, `payload` JSON, `publish_status`, `retry_count` | Mẫu Transactional Outbox phát `TicketCreated` nguyên tử cùng transaction tạo ticket. |
| `ticket_event` | `id` PK, `ticket_id` FK, `actor_id` FK, `action_type`, `field_name`, `old_value`, `new_value`, `reason` | Nhật ký kiểm toán (Audit Log) cho mọi biến đổi vòng đời ticket. |

### 3.4. Phân hệ AI Phân loại & Định tuyến (UC04)
| Bảng | Khóa & Trường chính | Vai trò & Ràng buộc |
|---|---|---|
| `routing_rule` | `id` PK, `category_id` FK, `team_id` FK, `confidence_threshold`, `auto_assign_enabled`, `is_active`, `active_category_key` | Ánh xạ danh mục → nhóm; cột sinh `active_category_key` + `UNIQUE` bảo đảm mỗi danh mục chỉ có 1 luật đang bật. |
| `classification_result` | `id` PK, `ticket_id` FK, `suggested_category_id` FK, `confidence`, `model_version`, `outcome`, `failure_reason` | Lưu mọi lần AI phân loại (kể cả khi thất bại/điểm thấp). |
| `assignment_history` | `id` PK, `ticket_id` FK, `category_id` FK, `team_id` FK, `agent_id` FK, `assigned_by` FK, `assignment_source` (`AUTO`/`MANUAL`), `reason` | Bảo toàn lịch sử phân công kể cả khi Agent rời nhóm (không FK vào `team_member`). |

### 3.5. Phân hệ SLA, Cảnh báo & Thông báo (UC05)
| Bảng | Khóa & Trường chính | Vai trò & Ràng buộc |
|---|---|---|
| `sla_policy` | `id` PK, `name`, `priority`, `version`, `is_active` | Phiên bản chính sách theo ưu tiên; `one_active_sla_policy` đảm bảo mỗi mức ưu tiên chỉ có 1 phiên bản đang bật. |
| `sla_policy_target` | `id` PK, `policy_id` FK, `metric` (`FIRST_RESPONSE`/`RESOLUTION`), `target_seconds`, `warning_ratio` | Thời hạn mục tiêu cho từng mốc; `UNIQUE(policy_id, metric)`. |
| `ticket_sla` | `id` PK, `ticket_id` FK, `policy_target_id` FK, `metric`, `target_seconds`, `warning_ratio`, `started_at`, `deadline_at`, `completed_at`, `tracking_status`, `breached_at` | Snapshot chính sách cho từng ticket (2 dòng/ticket). `CHECK` cấm tạm dừng `FIRST_RESPONSE`. |
| `sla_pause` | `id` PK, `ticket_sla_id` FK, `paused_at`, `resumed_at`, `reason` (`WAITING_CUSTOMER`/`RESOLVED_UNTIL_REOPEN`) | Lịch sử khoảng tạm dừng; `one_open_pause` đảm bảo không có 2 pause đang mở chồng lên nhau. |
| `sla_event` | `id` PK, `ticket_sla_id` FK, `event_type` (`WARNING`/`BREACH`) | Chống phát cảnh báo trùng bằng `UNIQUE(ticket_sla_id, event_type)`. |
| `escalation_action` | `id` PK, `sla_event_id` FK, `handled_by` FK, `action`, `note`, `handled_at` | Ghi nhận nhiều hành động can thiệp của Supervisor cho 1 sự kiện vi phạm. |
| `notification` | `id` PK, `recipient_id` FK, `ticket_id` FK, `sla_event_id` FK, `assignment_history_id` FK, `delivery_status` | Dùng chung cho thông báo phân công (UC04) và cảnh báo/chuyển cấp (UC05); `CHECK` bảo đảm chỉ đúng 1 trong 2 khóa ngoại nguồn khác `NULL`. |

### 3.6. Phân hệ Knowledge Base, AI RAG & CSAT (UC06–UC08)
| Bảng | Khóa & Trường chính | Vai trò & Ràng buộc |
|---|---|---|
| `kb_article` | `id` PK, `article_code` UQ, `category_id` FK, `access_scope`, `allow_auto_reply`, `current_published_version_id` FK, `created_by` FK | Định danh bài viết KB xuyên suốt các phiên bản. |
| `kb_version` | `id` PK, `article_id` FK, `version_number`, `title`, `content`, `status`, `created_by`, `reviewed_by`, `review_note`, `published_at`, `archived_at` | Vòng đời phiên bản; `one_published_version_per_article` đảm bảo tối đa 1 bản `PUBLISHED`/bài. |
| `kb_chunk` | `id` PK, `version_id` FK, `chunk_index`, `heading`, `content`, `token_count`, `embedding_ref`, `is_active` | Đoạn nội dung đã lập chỉ mục cho RAG; đặt `is_active = FALSE` ngay khi thu hồi bài. |
| `ai_run` | `id` PK, `ticket_id` FK, `requested_by` FK, `task_type`, `model_name`, `prompt_version`, `query_text`, `answer_text`, `confidence`, `outcome`, `fallback_reason`, `latency_ms` | Nhật ký chạy AI RAG, tách biệt với trạng thái ticket. |
| `ai_citation` | `id` PK, `ai_run_id` FK, `kb_chunk_id` FK, `kb_version_id` FK, `relevance_score`, `citation_order` | Nguồn trích dẫn có thứ tự cho từng câu trả lời AI. |
| `ai_suggestion_feedback` | `id` PK, `ai_run_id` UQ FK, `agent_id` FK, `decision`, `note` | Phản hồi của Agent về gợi ý AI phục vụ đánh giá chất lượng. |
| `satisfaction_rating` | `id` PK, `ticket_id` UQ FK, `customer_id` FK, `score` (1–5), `comment`, `sent_at`, `submitted_at` | Khảo sát CSAT (1-1 với ticket đã giải quyết); `CHECK` bảo đảm `score` và `submitted_at` luôn đồng bộ NULL/NOT NULL. |

---

## 4. Giải trình chuẩn hóa (3NF) và Chiến lược truy vấn cho UC08

### 4.1. Kiểm tra chuẩn hóa 3NF
- **1NF:** Mọi thuộc tính đều nguyên tử; không lưu danh sách ID trong chuỗi (vai trò tách ra `user_role`, thành viên tách ra `team_member`, trích dẫn tách ra `ai_citation`, khoảng tạm dừng tách ra `sla_pause`).
- **2NF:** Các bảng khóa ghép (`user_role`, `team_member`) không có thuộc tính phụ thuộc bộ phận.
- **3NF & Phi chuẩn hóa có kiểm soát (Snapshot):** Toàn bộ danh mục, chính sách, phiên bản được tách riêng. Các trường snapshot như `ticket_sla.target_seconds`, `ticket_sla.warning_ratio` và `ai_citation.kb_version_id` được sao chép có chủ đích để **bảo toàn tính lịch sử kiểm toán** khi chính sách SLA hoặc bài KB thay đổi về sau.

### 4.2. Truy vấn chuẩn cho Báo cáo UC08 trên Schema MySQL 8.0
Nhờ View `v_ticket_sla_summary`, UC08 tính chính xác toàn bộ KPI trên **MySQL 8.0** mà không bị nhân dòng khi JOIN:

```sql
-- 1. Tổng hợp KPI Ticket & Thời gian trung bình (kỳ [:from_ts, :as_of], UTC)
SELECT
    COUNT(*)                                                                             AS total_tickets,
    SUM(CASE WHEN status NOT IN ('RESOLVED', 'CLOSED', 'CANCELLED') THEN 1 ELSE 0 END)   AS open_tickets,
    AVG(CASE
        WHEN first_response_at IS NOT NULL
        THEN TIMESTAMPDIFF(SECOND, created_at, first_response_at)
    END)                                                                                 AS avg_first_response_sec,
    AVG(CASE
        WHEN resolved_at IS NOT NULL
        THEN TIMESTAMPDIFF(SECOND, created_at, resolved_at) - resolution_paused_seconds
    END)                                                                                 AS avg_net_resolution_sec,
    SUM(CASE
        WHEN status <> 'CANCELLED'
         AND (
             (first_response_status = 'RUNNING' AND first_response_due_at < :as_of)
             OR (resolution_status = 'RUNNING' AND resolution_due_at < :as_of)
         )
        THEN 1 ELSE 0
    END)                                                                                 AS overdue_tickets
FROM v_ticket_sla_summary
WHERE created_at >= :from_ts AND created_at <= :as_of;

-- 2. Tỷ lệ tuân thủ SLA theo mốc (loại bỏ mốc CANCELLED)
SELECT
    ROUND(
        100.0 * SUM(CASE
            WHEN ts.completed_at IS NOT NULL AND ts.completed_at <= ts.deadline_at THEN 1 ELSE 0
        END) / NULLIF(COUNT(*), 0),
        2
    ) AS sla_compliance_pct
FROM ticket_sla ts
JOIN ticket t ON t.id = ts.ticket_id
WHERE t.created_at >= :from_ts AND t.created_at <= :as_of
  AND ts.tracking_status <> 'CANCELLED'
  AND (ts.completed_at IS NOT NULL OR ts.deadline_at <= :as_of);

-- 3. Điểm CSAT trung bình (theo tập ticket tạo trong kỳ)
SELECT
    ROUND(AVG(sr.score), 2) AS csat_avg,
    COUNT(sr.score)         AS rating_count
FROM satisfaction_rating sr
JOIN ticket t ON t.id = sr.ticket_id
WHERE t.created_at >= :from_ts AND t.created_at <= :as_of
  AND sr.score IS NOT NULL;
```
