-- ============================================================================
-- V2: DỮ LIỆU DANH MỤC NỀN (MASTER DATA) CHO HỆ THỐNG HELPDESK
-- ============================================================================

-- 1. Vai trò chuẩn (4 roles — FR01)
INSERT INTO role (code, name) VALUES
    ('CUSTOMER',   'Khách hàng'),
    ('AGENT',      'Nhân viên hỗ trợ'),
    ('SUPERVISOR', 'Trưởng nhóm hỗ trợ'),
    ('ADMIN',      'Quản trị viên hệ thống');

-- 2. Kênh tiếp nhận đa kênh (5 channels — UC01-UC02)
INSERT INTO channel (code, name, is_active) VALUES
    ('WEB_FORM',  'Cổng Web Form',     TRUE),
    ('EMAIL',     'Email Hỗ trợ',      TRUE),
    ('LIVE_CHAT', 'Trò chuyện trực tuyến', TRUE),
    ('FACEBOOK',  'Facebook Messenger', TRUE),
    ('ZALO',      'Zalo OA',           TRUE);

-- 3. Tài khoản kênh mặc định
INSERT INTO channel_account (channel_id, account_code, display_name, is_active)
SELECT id, 'WEB_PORTAL_DEFAULT', 'Cổng hỗ trợ khách hàng mặc định', TRUE FROM channel WHERE code = 'WEB_FORM';

INSERT INTO channel_account (channel_id, account_code, display_name, is_active)
SELECT id, 'SUPPORT_EMAIL_MAIN', 'support@helpdesk.local', TRUE FROM channel WHERE code = 'EMAIL';

INSERT INTO channel_account (channel_id, account_code, display_name, is_active)
SELECT id, 'WEB_LIVE_CHAT', 'Live Chat Trang chủ', TRUE FROM channel WHERE code = 'LIVE_CHAT';

INSERT INTO channel_account (channel_id, account_code, display_name, is_active)
SELECT id, 'FB_PAGE_MAIN', 'Fanpage Hỗ trợ chính', TRUE FROM channel WHERE code = 'FACEBOOK';

INSERT INTO channel_account (channel_id, account_code, display_name, is_active)
SELECT id, 'ZALO_OA_MAIN', 'Zalo OA Hỗ trợ chính', TRUE FROM channel WHERE code = 'ZALO';

-- 4. Danh mục yêu cầu cơ bản (UC04)
INSERT INTO category (code, name, is_active) VALUES
    ('PAYMENT',   'Thanh toán & Hóa đơn', TRUE),
    ('ACCOUNT',   'Tài khoản & Đăng nhập', TRUE),
    ('TECHNICAL', 'Lỗi kỹ thuật',         TRUE),
    ('ORDER',     'Đơn hàng & Giao nhận', TRUE),
    ('OTHER',     'Yêu cầu khác',         TRUE);

-- 5. Chính sách SLA mặc định 24/7 (4 mức ưu tiên — UC05)
INSERT INTO sla_policy (name, priority, version, is_active) VALUES
    ('Chính sách SLA 24/7 - Khẩn cấp',   'URGENT', 1, TRUE),
    ('Chính sách SLA 24/7 - Cao',        'HIGH',   1, TRUE),
    ('Chính sách SLA 24/7 - Bình thường','NORMAL', 1, TRUE),
    ('Chính sách SLA 24/7 - Thấp',       'LOW',    1, TRUE);

-- URGENT: 15 phút phản hồi đầu / 4 giờ giải quyết
INSERT INTO sla_policy_target (policy_id, metric, target_seconds, warning_ratio)
SELECT id, 'FIRST_RESPONSE', 900,   0.8000 FROM sla_policy WHERE priority = 'URGENT' AND version = 1;
INSERT INTO sla_policy_target (policy_id, metric, target_seconds, warning_ratio)
SELECT id, 'RESOLUTION',     14400, 0.8000 FROM sla_policy WHERE priority = 'URGENT' AND version = 1;

-- HIGH: 1 giờ phản hồi đầu / 8 giờ giải quyết
INSERT INTO sla_policy_target (policy_id, metric, target_seconds, warning_ratio)
SELECT id, 'FIRST_RESPONSE', 3600,  0.8000 FROM sla_policy WHERE priority = 'HIGH' AND version = 1;
INSERT INTO sla_policy_target (policy_id, metric, target_seconds, warning_ratio)
SELECT id, 'RESOLUTION',     28800, 0.8000 FROM sla_policy WHERE priority = 'HIGH' AND version = 1;

-- NORMAL: 4 giờ phản hồi đầu / 24 giờ giải quyết
INSERT INTO sla_policy_target (policy_id, metric, target_seconds, warning_ratio)
SELECT id, 'FIRST_RESPONSE', 14400, 0.8000 FROM sla_policy WHERE priority = 'NORMAL' AND version = 1;
INSERT INTO sla_policy_target (policy_id, metric, target_seconds, warning_ratio)
SELECT id, 'RESOLUTION',     86400, 0.8000 FROM sla_policy WHERE priority = 'NORMAL' AND version = 1;

-- LOW: 8 giờ phản hồi đầu / 48 giờ giải quyết
INSERT INTO sla_policy_target (policy_id, metric, target_seconds, warning_ratio)
SELECT id, 'FIRST_RESPONSE', 28800,  0.8000 FROM sla_policy WHERE priority = 'LOW' AND version = 1;
INSERT INTO sla_policy_target (policy_id, metric, target_seconds, warning_ratio)
SELECT id, 'RESOLUTION',     172800, 0.8000 FROM sla_policy WHERE priority = 'LOW' AND version = 1;
