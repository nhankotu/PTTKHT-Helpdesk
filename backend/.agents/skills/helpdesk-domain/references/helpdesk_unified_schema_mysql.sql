-- ============================================================================
-- NỀN TẢNG HELPDESK ĐA KÊNH TÍCH HỢP AI (ĐỀ TÀI 07)
-- LƯỢC ĐỒ CƠ SỞ DỮ LIỆU HỢP NHẤT — MYSQL 8.0+ (UC01 ĐẾN UC08 + FR01)
-- ============================================================================
-- DBMS       : MySQL 8.0+ (InnoDB, utf8mb4_unicode_ci)
-- Quy ước    :
--   - Tên bảng  : snake_case, số ít
--   - Khóa chính: BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY
--   - Thời gian : DATETIME(6) lưu UTC (tránh giới hạn năm 2038 của TIMESTAMP),
--                 hiển thị theo múi giờ Asia/Ho_Chi_Minh ở tầng ứng dụng
--   - Ràng buộc duy nhất có điều kiện: dùng Generated Column (STORED) + UNIQUE
--   - Chống ghi đè đồng thời: ticket.version (Optimistic Locking)
--   - Tổng số bảng: 34 bảng (6 phân hệ) + 1 View báo cáo cho UC08
-- ============================================================================

SET NAMES utf8mb4;
SET TIME_ZONE = '+00:00';
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- PHÂN HỆ 1: TÀI KHOẢN, PHÂN QUYỀN & NHÓM HỖ TRỢ (FR01 — 5 bảng)
-- ============================================================================

CREATE TABLE app_user (
    id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name      VARCHAR(150) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    phone          VARCHAR(30) NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    is_available   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_app_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(30) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    UNIQUE KEY uq_role_code (code),
    CONSTRAINT chk_role_code CHECK (code IN ('CUSTOMER', 'AGENT', 'SUPERVISOR', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_role (
    user_id     BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE support_team (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(30) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    supervisor_id BIGINT NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_support_team_code (code),
    UNIQUE KEY uq_support_team_name (name),
    CONSTRAINT fk_support_team_supervisor FOREIGN KEY (supervisor_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE team_member (
    team_id     BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    joined_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (team_id, user_id),
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES support_team(id),
    CONSTRAINT fk_team_member_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- PHÂN HỆ 2: TIẾP NHẬN ĐA KÊNH & ĐỊNH DANH KHÁCH HÀNG (UC01–UC02 — 6 bảng)
-- ============================================================================

CREATE TABLE channel (
    id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(30) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY uq_channel_code (code),
    CONSTRAINT chk_channel_code CHECK (code IN ('WEB_FORM', 'EMAIL', 'LIVE_CHAT', 'FACEBOOK', 'ZALO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE channel_account (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    channel_id      BIGINT NOT NULL,
    account_code    VARCHAR(100) NOT NULL,
    display_name    VARCHAR(150) NOT NULL,
    config_metadata JSON NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_channel_account_code (account_code),
    CONSTRAINT fk_channel_account_channel FOREIGN KEY (channel_id) REFERENCES channel(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE customer (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NULL,
    full_name   VARCHAR(150) NULL,
    email       VARCHAR(255) NULL,
    phone       VARCHAR(30) NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_customer_user (user_id),
    CONSTRAINT fk_customer_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE customer_identity (
    id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id        BIGINT NULL,
    channel_account_id BIGINT NOT NULL,
    external_user_id   VARCHAR(255) NOT NULL,
    display_name       VARCHAR(150) NULL,
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_customer_identity_ext (channel_account_id, external_user_id),
    CONSTRAINT fk_customer_identity_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_customer_identity_account FOREIGN KEY (channel_account_id) REFERENCES channel_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE conversation (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    channel_account_id   BIGINT NOT NULL,
    customer_identity_id BIGINT NULL,
    external_thread_id   VARCHAR(255) NOT NULL,
    started_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_conversation_thread (channel_account_id, external_thread_id),
    CONSTRAINT fk_conversation_account FOREIGN KEY (channel_account_id) REFERENCES channel_account(id),
    CONSTRAINT fk_conversation_identity FOREIGN KEY (customer_identity_id) REFERENCES customer_identity(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE category (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(40) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY uq_category_code (code),
    UNIQUE KEY uq_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- PHÂN HỆ 3: TICKET, HỘI THOẠI, ĐÍNH KÈM & SỰ KIỆN (UC02–UC03 — 6 bảng)
-- ============================================================================

CREATE TABLE ticket (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_code       VARCHAR(30) NOT NULL,
    conversation_id   BIGINT NOT NULL,
    customer_id       BIGINT NULL,
    parent_ticket_id  BIGINT NULL,
    channel_id        BIGINT NOT NULL,
    category_id       BIGINT NULL,
    team_id           BIGINT NULL,
    assigned_agent_id BIGINT NULL,
    subject           VARCHAR(300) NOT NULL,
    description       TEXT NULL,
    status            VARCHAR(25) NOT NULL DEFAULT 'NEW',
    priority          VARCHAR(15) NOT NULL DEFAULT 'NORMAL',
    resolution_code   VARCHAR(50) NULL,
    cancel_reason     TEXT NULL,
    resolved_at       DATETIME(6) NULL,
    closed_at         DATETIME(6) NULL,
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version           INT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_ticket_code (ticket_code),
    CONSTRAINT fk_ticket_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id),
    CONSTRAINT fk_ticket_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_ticket_parent FOREIGN KEY (parent_ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_ticket_channel FOREIGN KEY (channel_id) REFERENCES channel(id),
    CONSTRAINT fk_ticket_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT fk_ticket_team FOREIGN KEY (team_id) REFERENCES support_team(id),
    CONSTRAINT fk_ticket_agent FOREIGN KEY (assigned_agent_id) REFERENCES app_user(id),
    CONSTRAINT fk_ticket_team_member FOREIGN KEY (team_id, assigned_agent_id)
        REFERENCES team_member(team_id, user_id),
    CONSTRAINT chk_ticket_status CHECK (status IN (
        'NEW', 'ASSIGNED', 'IN_PROGRESS',
        'WAITING_CUSTOMER', 'RESOLVED', 'CLOSED', 'CANCELLED'
    )),
    CONSTRAINT chk_ticket_priority CHECK (priority IN ('URGENT', 'HIGH', 'NORMAL', 'LOW')),
    CONSTRAINT chk_ticket_version CHECK (version >= 0),
    CONSTRAINT chk_ticket_agent_team CHECK (assigned_agent_id IS NULL OR team_id IS NOT NULL),
    CONSTRAINT chk_ticket_cancel_reason CHECK (
        status <> 'CANCELLED' OR (cancel_reason IS NOT NULL AND CHAR_LENGTH(TRIM(cancel_reason)) >= 10)
    ),
    CONSTRAINT chk_ticket_resolution_code CHECK (status <> 'RESOLVED' OR resolution_code IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_message (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id     BIGINT NOT NULL,
    ticket_id           BIGINT NULL,
    sender_user_id      BIGINT NULL,
    sender_type         VARCHAR(20) NOT NULL,
    direction           VARCHAR(10) NOT NULL,
    visibility          VARCHAR(15) NOT NULL DEFAULT 'PUBLIC',
    is_auto_ack         BOOLEAN NOT NULL DEFAULT FALSE,
    external_message_id VARCHAR(255) NULL,
    body                TEXT NOT NULL,
    delivery_status     VARCHAR(20) NOT NULL DEFAULT 'SENT',
    occurred_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_message_conv_ext (conversation_id, external_message_id),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id),
    CONSTRAINT fk_message_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_user_id) REFERENCES app_user(id),
    CONSTRAINT chk_message_sender_type CHECK (sender_type IN ('CUSTOMER', 'AGENT', 'SUPERVISOR', 'AI_BOT', 'SYSTEM')),
    CONSTRAINT chk_message_direction CHECK (direction IN ('INBOUND', 'OUTBOUND', 'INTERNAL')),
    CONSTRAINT chk_message_visibility CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    CONSTRAINT chk_message_delivery CHECK (delivery_status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED')),
    CONSTRAINT chk_message_visibility_rule CHECK (
        (visibility = 'INTERNAL' AND direction = 'INTERNAL' AND sender_type IN ('AGENT', 'SUPERVISOR', 'SYSTEM'))
        OR (visibility = 'PUBLIC' AND direction IN ('INBOUND', 'OUTBOUND'))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attachment (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id  BIGINT NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    mime_type   VARCHAR(150) NOT NULL,
    size_bytes  BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_attachment_message FOREIGN KEY (message_id) REFERENCES ticket_message(id),
    CONSTRAINT chk_attachment_size CHECK (size_bytes >= 0 AND size_bytes <= 26214400)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inbound_event (
    id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    channel_account_id BIGINT NOT NULL,
    external_event_id  VARCHAR(255) NOT NULL,
    idempotency_key    VARCHAR(128) NULL,
    payload_hash       VARCHAR(64) NULL,
    payload_redacted   JSON NULL,
    process_status     VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    ticket_id          BIGINT NULL,
    message_id         BIGINT NULL,
    error_code         VARCHAR(80) NULL,
    received_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at       DATETIME(6) NULL,
    UNIQUE KEY uq_inbound_event_ext (channel_account_id, external_event_id),
    CONSTRAINT fk_inbound_event_account FOREIGN KEY (channel_account_id) REFERENCES channel_account(id),
    CONSTRAINT fk_inbound_event_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_inbound_event_message FOREIGN KEY (message_id) REFERENCES ticket_message(id),
    CONSTRAINT chk_inbound_event_status CHECK (process_status IN ('RECEIVED', 'PROCESSED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE outbox_event (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    aggregate_type  VARCHAR(40) NOT NULL,
    aggregate_id    BIGINT NOT NULL,
    event_type      VARCHAR(60) NOT NULL,
    payload         JSON NOT NULL,
    publish_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at    DATETIME(6) NULL,
    CONSTRAINT chk_outbox_status CHECK (publish_status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_outbox_retry CHECK (retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_event (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT NOT NULL,
    actor_id    BIGINT NULL,
    action_type VARCHAR(30) NOT NULL,
    field_name  VARCHAR(50) NOT NULL,
    old_value   TEXT NULL,
    new_value   TEXT NOT NULL,
    reason      TEXT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ticket_event_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_ticket_event_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    CONSTRAINT chk_ticket_event_action CHECK (action_type IN (
        'CREATED', 'STATUS_CHANGE', 'ASSIGNMENT_CHANGE',
        'PRIORITY_CHANGE', 'CATEGORY_CHANGE', 'REOPENED', 'CANCELLED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- PHÂN HỆ 4: AI PHÂN LOẠI & ĐỊNH TUYẾN (UC04 — 3 bảng)
-- ============================================================================

CREATE TABLE routing_rule (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_id          BIGINT NOT NULL,
    team_id              BIGINT NOT NULL,
    confidence_threshold DECIMAL(5,4) NOT NULL,
    auto_assign_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    -- Generated column đảm bảo mỗi category chỉ có tối đa 1 luật is_active = TRUE
    active_category_key  BIGINT GENERATED ALWAYS AS (IF(is_active, category_id, NULL)) STORED,
    UNIQUE KEY uq_one_active_route (active_category_key),
    CONSTRAINT fk_routing_rule_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT fk_routing_rule_team FOREIGN KEY (team_id) REFERENCES support_team(id),
    CONSTRAINT chk_routing_threshold CHECK (confidence_threshold BETWEEN 0 AND 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE classification_result (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id             BIGINT NOT NULL,
    suggested_category_id BIGINT NULL,
    confidence            DECIMAL(5,4) NULL,
    model_version         VARCHAR(100) NULL,
    outcome               VARCHAR(25) NOT NULL,
    failure_reason        TEXT NULL,
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_classification_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_classification_category FOREIGN KEY (suggested_category_id) REFERENCES category(id),
    CONSTRAINT chk_classification_conf CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    CONSTRAINT chk_classification_outcome CHECK (outcome IN ('APPLIED', 'REVIEW_REQUIRED', 'FAILED', 'SKIPPED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assignment_history (
    id                BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id         BIGINT NOT NULL,
    category_id       BIGINT NULL,
    team_id           BIGINT NOT NULL,
    agent_id          BIGINT NOT NULL,
    assigned_by       BIGINT NULL,
    assignment_source VARCHAR(10) NOT NULL,
    reason            TEXT NULL,
    assigned_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_assignment_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_assignment_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT fk_assignment_team FOREIGN KEY (team_id) REFERENCES support_team(id),
    CONSTRAINT fk_assignment_agent FOREIGN KEY (agent_id) REFERENCES app_user(id),
    CONSTRAINT fk_assignment_by FOREIGN KEY (assigned_by) REFERENCES app_user(id),
    CONSTRAINT chk_assignment_source CHECK (assignment_source IN ('AUTO', 'MANUAL')),
    CONSTRAINT chk_assignment_by_rule CHECK (
        (assignment_source = 'AUTO' AND assigned_by IS NULL)
        OR (assignment_source = 'MANUAL' AND assigned_by IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- PHÂN HỆ 5: SLA, CẢNH BÁO, CHUYỂN CẤP & THÔNG BÁO (UC05 — 7 bảng)
-- ============================================================================

CREATE TABLE sla_policy (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    priority            VARCHAR(15) NOT NULL,
    version             INT NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- Generated column đảm bảo mỗi priority chỉ có tối đa 1 chính sách is_active = TRUE
    active_priority_key VARCHAR(15) GENERATED ALWAYS AS (IF(is_active, priority, NULL)) STORED,
    UNIQUE KEY uq_sla_policy_priority_ver (priority, version),
    UNIQUE KEY uq_one_active_sla_policy (active_priority_key),
    CONSTRAINT chk_sla_policy_priority CHECK (priority IN ('URGENT', 'HIGH', 'NORMAL', 'LOW')),
    CONSTRAINT chk_sla_policy_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sla_policy_target (
    id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    policy_id      BIGINT NOT NULL,
    metric         VARCHAR(20) NOT NULL,
    target_seconds INT NOT NULL,
    warning_ratio  DECIMAL(5,4) NOT NULL DEFAULT 0.8000,
    UNIQUE KEY uq_sla_target_policy_metric (policy_id, metric),
    CONSTRAINT fk_sla_target_policy FOREIGN KEY (policy_id) REFERENCES sla_policy(id),
    CONSTRAINT chk_sla_target_metric CHECK (metric IN ('FIRST_RESPONSE', 'RESOLUTION')),
    CONSTRAINT chk_sla_target_seconds CHECK (target_seconds > 0),
    CONSTRAINT chk_sla_target_ratio CHECK (warning_ratio > 0 AND warning_ratio < 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_sla (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id        BIGINT NOT NULL,
    policy_target_id BIGINT NOT NULL,
    metric           VARCHAR(20) NOT NULL,
    target_seconds   INT NOT NULL,
    warning_ratio    DECIMAL(5,4) NOT NULL,
    started_at       DATETIME(6) NOT NULL,
    deadline_at      DATETIME(6) NOT NULL,
    completed_at     DATETIME(6) NULL,
    tracking_status  VARCHAR(15) NOT NULL DEFAULT 'RUNNING',
    breached_at      DATETIME(6) NULL,
    UNIQUE KEY uq_ticket_sla_metric (ticket_id, metric),
    CONSTRAINT fk_ticket_sla_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_ticket_sla_target FOREIGN KEY (policy_target_id) REFERENCES sla_policy_target(id),
    CONSTRAINT chk_ticket_sla_metric CHECK (metric IN ('FIRST_RESPONSE', 'RESOLUTION')),
    CONSTRAINT chk_ticket_sla_seconds CHECK (target_seconds > 0),
    CONSTRAINT chk_ticket_sla_ratio CHECK (warning_ratio > 0 AND warning_ratio < 1),
    CONSTRAINT chk_ticket_sla_status CHECK (tracking_status IN ('RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_ticket_sla_deadline CHECK (deadline_at >= started_at),
    CONSTRAINT chk_ticket_sla_completed_time CHECK (completed_at IS NULL OR completed_at >= started_at),
    CONSTRAINT chk_ticket_sla_completed_flag CHECK (
        (tracking_status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (tracking_status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT chk_ticket_sla_pause_only_res CHECK (metric = 'RESOLUTION' OR tracking_status <> 'PAUSED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sla_pause (
    id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_sla_id  BIGINT NOT NULL,
    paused_at      DATETIME(6) NOT NULL,
    resumed_at     DATETIME(6) NULL,
    reason         VARCHAR(50) NOT NULL,
    -- Generated column đảm bảo mỗi ticket_sla chỉ có tối đa 1 khoảng pause đang mở
    open_pause_key BIGINT GENERATED ALWAYS AS (IF(resumed_at IS NULL, ticket_sla_id, NULL)) STORED,
    UNIQUE KEY uq_one_open_pause (open_pause_key),
    CONSTRAINT fk_sla_pause_ticket_sla FOREIGN KEY (ticket_sla_id) REFERENCES ticket_sla(id),
    CONSTRAINT chk_sla_pause_reason CHECK (reason IN ('WAITING_CUSTOMER', 'RESOLVED_UNTIL_REOPEN')),
    CONSTRAINT chk_sla_pause_time CHECK (resumed_at IS NULL OR resumed_at >= paused_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sla_event (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_sla_id BIGINT NOT NULL,
    event_type    VARCHAR(10) NOT NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_sla_event_type (ticket_sla_id, event_type),
    CONSTRAINT fk_sla_event_ticket_sla FOREIGN KEY (ticket_sla_id) REFERENCES ticket_sla(id),
    CONSTRAINT chk_sla_event_type CHECK (event_type IN ('WARNING', 'BREACH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE escalation_action (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sla_event_id BIGINT NOT NULL,
    handled_by   BIGINT NOT NULL,
    action       VARCHAR(100) NOT NULL,
    note         TEXT NULL,
    handled_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_escalation_event FOREIGN KEY (sla_event_id) REFERENCES sla_event(id),
    CONSTRAINT fk_escalation_user FOREIGN KEY (handled_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_id          BIGINT NOT NULL,
    ticket_id             BIGINT NOT NULL,
    sla_event_id          BIGINT NULL,
    assignment_history_id BIGINT NULL,
    delivery_status       VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at               DATETIME(6) NULL,
    read_at               DATETIME(6) NULL,
    UNIQUE KEY uq_notif_sla_recipient (sla_event_id, recipient_id),
    UNIQUE KEY uq_notif_assign_recipient (assignment_history_id, recipient_id),
    CONSTRAINT fk_notif_recipient FOREIGN KEY (recipient_id) REFERENCES app_user(id),
    CONSTRAINT fk_notif_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_notif_sla_event FOREIGN KEY (sla_event_id) REFERENCES sla_event(id),
    CONSTRAINT fk_notif_assignment FOREIGN KEY (assignment_history_id) REFERENCES assignment_history(id),
    CONSTRAINT chk_notif_delivery CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT chk_notif_exactly_one_source CHECK (
        (sla_event_id IS NOT NULL AND assignment_history_id IS NULL)
        OR (sla_event_id IS NULL AND assignment_history_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- PHÂN HỆ 6: KNOWLEDGE BASE, AI RAG & KHẢO SÁT CSAT (UC06–UC08 — 7 bảng)
-- ============================================================================

CREATE TABLE kb_article (
    id                           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    article_code                 VARCHAR(30) NOT NULL,
    category_id                  BIGINT NULL,
    access_scope                 VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    allow_auto_reply             BOOLEAN NOT NULL DEFAULT FALSE,
    current_published_version_id BIGINT NULL,
    created_by                   BIGINT NOT NULL,
    created_at                   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_kb_article_code (article_code),
    CONSTRAINT fk_kb_article_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT fk_kb_article_creator FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT chk_kb_article_scope CHECK (access_scope IN ('PUBLIC', 'INTERNAL_AGENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE kb_version (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    article_id            BIGINT NOT NULL,
    version_number        INT NOT NULL,
    title                 VARCHAR(250) NOT NULL,
    summary               TEXT NULL,
    content               LONGTEXT NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by            BIGINT NOT NULL,
    reviewed_by           BIGINT NULL,
    review_note           TEXT NULL,
    published_at          DATETIME(6) NULL,
    archived_at           DATETIME(6) NULL,
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    -- Generated column đảm bảo mỗi bài viết chỉ có tối đa 1 phiên bản PUBLISHED
    published_article_key BIGINT GENERATED ALWAYS AS (IF(status = 'PUBLISHED', article_id, NULL)) STORED,
    UNIQUE KEY uq_kb_version_number (article_id, version_number),
    UNIQUE KEY uq_one_published_version (published_article_key),
    CONSTRAINT fk_kb_version_article FOREIGN KEY (article_id) REFERENCES kb_article(id),
    CONSTRAINT fk_kb_version_creator FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT fk_kb_version_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user(id),
    CONSTRAINT chk_kb_version_num CHECK (version_number > 0),
    CONSTRAINT chk_kb_version_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED')),
    CONSTRAINT chk_kb_version_published CHECK (status <> 'PUBLISHED' OR (reviewed_by IS NOT NULL AND published_at IS NOT NULL)),
    CONSTRAINT chk_kb_version_archived CHECK (status <> 'ARCHIVED' OR archived_at IS NOT NULL),
    CONSTRAINT chk_kb_version_rejected CHECK (status <> 'REJECTED' OR (reviewed_by IS NOT NULL AND review_note IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE kb_article
    ADD CONSTRAINT fk_kb_article_current_published_version
    FOREIGN KEY (current_published_version_id) REFERENCES kb_version(id);

CREATE TABLE kb_chunk (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version_id      BIGINT NOT NULL,
    chunk_index     INT NOT NULL,
    heading         VARCHAR(250) NULL,
    content         TEXT NOT NULL,
    token_count     INT NOT NULL,
    embedding_ref   VARCHAR(255) NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    indexed_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_kb_chunk_order (version_id, chunk_index),
    CONSTRAINT fk_kb_chunk_version FOREIGN KEY (version_id) REFERENCES kb_version(id),
    CONSTRAINT chk_kb_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT chk_kb_chunk_tokens CHECK (token_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_run (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id        BIGINT NOT NULL,
    requested_by     BIGINT NULL,
    task_type        VARCHAR(25) NOT NULL DEFAULT 'RAG_SUGGESTION',
    model_name       VARCHAR(100) NOT NULL,
    prompt_version   VARCHAR(50) NOT NULL,
    query_text       TEXT NOT NULL,
    answer_text      TEXT NULL,
    confidence       DECIMAL(5,4) NULL,
    outcome          VARCHAR(25) NOT NULL,
    fallback_reason  VARCHAR(50) NULL,
    latency_ms       INT NULL,
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_run_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_ai_run_requester FOREIGN KEY (requested_by) REFERENCES app_user(id),
    CONSTRAINT chk_ai_run_task CHECK (task_type IN ('RAG_SUGGESTION', 'AUTO_REPLY')),
    CONSTRAINT chk_ai_run_conf CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    CONSTRAINT chk_ai_run_outcome CHECK (outcome IN ('SUGGESTED', 'AUTO_SENT', 'FALLBACK_TO_AGENT', 'FAILED')),
    CONSTRAINT chk_ai_run_fallback CHECK (
        fallback_reason IS NULL OR fallback_reason IN (
            'NO_RELEVANT_KB', 'CONFLICTING_SOURCES', 'OUTDATED_OR_REVOKED_KB',
            'LOW_CONFIDENCE', 'OUT_OF_AUTO_SCOPE', 'CUSTOMER_REQUESTED_HUMAN',
            'CITATION_INVALID', 'AI_SERVICE_ERROR'
        )
    ),
    CONSTRAINT chk_ai_run_latency CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT chk_ai_run_outcome_fields CHECK (
        (outcome IN ('SUGGESTED', 'AUTO_SENT') AND answer_text IS NOT NULL)
        OR (outcome IN ('FALLBACK_TO_AGENT', 'FAILED') AND fallback_reason IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_citation (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ai_run_id        BIGINT NOT NULL,
    kb_chunk_id      BIGINT NOT NULL,
    kb_version_id    BIGINT NOT NULL,
    relevance_score  DECIMAL(5,4) NOT NULL,
    citation_order   INT NOT NULL,
    UNIQUE KEY uq_ai_citation_chunk (ai_run_id, kb_chunk_id),
    UNIQUE KEY uq_ai_citation_order (ai_run_id, citation_order),
    CONSTRAINT fk_ai_citation_run FOREIGN KEY (ai_run_id) REFERENCES ai_run(id),
    CONSTRAINT fk_ai_citation_chunk FOREIGN KEY (kb_chunk_id) REFERENCES kb_chunk(id),
    CONSTRAINT fk_ai_citation_version FOREIGN KEY (kb_version_id) REFERENCES kb_version(id),
    CONSTRAINT chk_ai_citation_score CHECK (relevance_score BETWEEN 0 AND 1),
    CONSTRAINT chk_ai_citation_order CHECK (citation_order > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_suggestion_feedback (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ai_run_id   BIGINT NOT NULL,
    agent_id    BIGINT NOT NULL,
    decision    VARCHAR(25) NOT NULL,
    note        TEXT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_ai_feedback_run (ai_run_id),
    CONSTRAINT fk_ai_feedback_run FOREIGN KEY (ai_run_id) REFERENCES ai_run(id),
    CONSTRAINT fk_ai_feedback_agent FOREIGN KEY (agent_id) REFERENCES app_user(id),
    CONSTRAINT chk_ai_feedback_decision CHECK (
        decision IN ('ACCEPTED_AS_IS', 'EDITED_AND_SENT', 'REJECTED', 'ESCALATED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE satisfaction_rating (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id    BIGINT NOT NULL,
    customer_id  BIGINT NULL,
    score        TINYINT NULL,
    comment      TEXT NULL,
    sent_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    submitted_at DATETIME(6) NULL,
    UNIQUE KEY uq_satisfaction_ticket (ticket_id),
    CONSTRAINT fk_satisfaction_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_satisfaction_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT chk_satisfaction_score CHECK (score IS NULL OR score BETWEEN 1 AND 5),
    CONSTRAINT chk_satisfaction_sync CHECK (
        (score IS NULL AND submitted_at IS NULL)
        OR (score IS NOT NULL AND submitted_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- VIEW HỖ TRỢ BÁO CÁO UC08 (Tương thích chuẩn MySQL 8.0)
-- ============================================================================

CREATE OR REPLACE VIEW v_ticket_sla_summary AS
SELECT
    t.id                                                              AS ticket_id,
    t.ticket_code,
    t.status,
    t.priority,
    t.category_id,
    t.team_id,
    t.assigned_agent_id,
    t.created_at,
    fr.deadline_at                                                    AS first_response_due_at,
    fr.completed_at                                                   AS first_response_at,
    fr.breached_at                                                    AS first_response_breached_at,
    fr.tracking_status                                                AS first_response_status,
    rs.deadline_at                                                    AS resolution_due_at,
    rs.completed_at                                                   AS resolved_at,
    rs.breached_at                                                    AS resolution_breached_at,
    rs.tracking_status                                                AS resolution_status,
    COALESCE((
        SELECT SUM(TIMESTAMPDIFF(SECOND, p.paused_at, COALESCE(p.resumed_at, UTC_TIMESTAMP(6))))
        FROM sla_pause p
        WHERE p.ticket_sla_id = rs.id
    ), 0)                                                             AS resolution_paused_seconds
FROM ticket t
LEFT JOIN ticket_sla fr ON fr.ticket_id = t.id AND fr.metric = 'FIRST_RESPONSE'
LEFT JOIN ticket_sla rs ON rs.ticket_id = t.id AND rs.metric = 'RESOLUTION';

-- ============================================================================
-- CHỈ MỤC TRUY VẤN (INDEXES)
-- ============================================================================

CREATE INDEX ix_customer_identity_customer       ON customer_identity (customer_id);
CREATE INDEX ix_ticket_conversation_created      ON ticket (conversation_id, created_at);
CREATE INDEX ix_ticket_agent_status              ON ticket (assigned_agent_id, status);
CREATE INDEX ix_ticket_created_status            ON ticket (created_at, status);
CREATE INDEX ix_ticket_message_ticket_created    ON ticket_message (ticket_id, created_at);
CREATE INDEX ix_ticket_message_conv_occurred     ON ticket_message (conversation_id, occurred_at);
CREATE INDEX ix_inbound_event_status_received    ON inbound_event (process_status, received_at);
CREATE INDEX ix_outbox_status_created            ON outbox_event (publish_status, created_at);
CREATE INDEX ix_ticket_event_ticket_created      ON ticket_event (ticket_id, created_at);
CREATE INDEX ix_classification_ticket_created    ON classification_result (ticket_id, created_at);
CREATE INDEX ix_assignment_ticket_time           ON assignment_history (ticket_id, assigned_at);
CREATE INDEX ix_sla_status_deadline              ON ticket_sla (tracking_status, deadline_at);
CREATE INDEX ix_sla_event_created                ON sla_event (created_at);
CREATE INDEX ix_notification_delivery_created    ON notification (delivery_status, created_at);
CREATE INDEX ix_kb_article_category_scope        ON kb_article (category_id, access_scope);
CREATE INDEX ix_kb_version_article_status        ON kb_version (article_id, status);
CREATE INDEX ix_kb_chunk_active_version          ON kb_chunk (is_active, version_id);
CREATE INDEX ix_ai_run_ticket_created            ON ai_run (ticket_id, created_at);
CREATE INDEX ix_ai_citation_run                  ON ai_citation (ai_run_id, citation_order);
CREATE INDEX ix_satisfaction_submitted           ON satisfaction_rating (submitted_at);

SET FOREIGN_KEY_CHECKS = 1;
