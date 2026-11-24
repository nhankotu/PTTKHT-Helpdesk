---
name: helpdesk-domain
description: Nguồn chân lý nghiệp vụ (Source of Truth) cho dự án Omnichannel AI Helpdesk Platform. Luôn đọc skill này trước khi thiết kế bảng, viết Entity, Service, API, AI Pipeline hoặc Test cho bất kỳ phân hệ nào trong 6 phân hệ nghiệp vụ.
---

# Omnichannel AI Helpdesk — Domain & Architecture Rules

> **Nguyên tắc ưu tiên:** Mọi quy tắc trong file này ghi đè lên các skill tổng quát khác. Trước khi tạo Entity, Repository hoặc Flyway migration, **bắt buộc đọc file schema chuẩn** tại `references/helpdesk_unified_schema_mysql.sql` (34 bảng chuẩn hóa 3NF + View `v_ticket_sla_summary`).

## 1. Bản đồ 6 Phân hệ (Modular Monolith)
Mỗi phân hệ là một package độc lập dưới root package (ví dụ `com.helpdesk.modules.<module>`):
1. `identity` **(UC00 / FR01):** Xác thực JWT, RBAC 4 vai trò (`CUSTOMER`, `AGENT`, `SUPERVISOR`, `ADMIN`), kiểm tra quyền theo ticket và nhóm (`support_team`, `team_member`).
2. `intake` **(UC01–UC02):** Tiếp nhận từ Web form + Webhook mô phỏng (Email/Chat/Social), chuẩn hóa dữ liệu, chống trùng bằng `Idempotency-Key` & bảng `inbound_event`, phát sự kiện `TicketCreated` qua `outbox_event`.
3. `ticket` **(UC03):** Vòng đời ticket, State Machine Guard, hội thoại `PUBLIC` vs `INTERNAL`, Retry gửi kênh lỗi, Optimistic Locking (`ticket.version`), khảo sát CSAT.
4. `routing` **(UC04):** Phân loại ticket bằng AI (JSON cấu trúc), khớp luật định tuyến (`routing_rule`), chọn Agent ít tải nhất, fallback sang hàng đợi Supervisor.
5. `sla` **(UC05):** Tính toán 2 mốc `FIRST_RESPONSE` & `RESOLUTION`, tạm dừng/tiếp tục (`sla_pause`), Scheduler quét cảnh báo 80% và chuyển cấp quá hạn chống trùng (`sla_event`).
6. `knowledge` & `reporting` **(UC06–UC08):** Quản lý vòng đời phiên bản KB (`kb_version`, `kb_chunk` — duyệt/xuất bản/thu hồi), AI RAG có kiểm chứng trích dẫn (`ai_citation`) + fallback an toàn, và Dashboard báo cáo đọc từ View `v_ticket_sla_summary`.

## 2. Bắt buộc áp dụng 4 Design Patterns
Không viết code dồn vào một class lớn; phải thể hiện rõ 4 mẫu thiết kế sau:
- **Adapter Pattern (`intake` & outbound channel):** Chuẩn hóa payload từ nhiều kênh (Web, Email, Chat, Social) về mô hình chung `NormalizedInboundMessage` và gửi phản hồi ra từng kênh.
- **Strategy Pattern (`routing`):** Tách thuật toán chọn Agent thành interface `AgentAssignmentStrategy` (mặc định triển khai `LeastLoadedAgentStrategy` — chọn Agent đang mở ít ticket nhất trong nhóm hợp lệ).
- **Repository Pattern (toàn hệ thống):** Tách biệt tầng truy cập dữ liệu qua Spring Data JPA Repository và custom query/projection.
- **Facade Pattern (`ai` / `knowledge`):** Cung cấp một mặt tiền thống nhất `AiHelpdeskFacade` che giấu độ phức tạp của việc gọi LLM, parse JSON phân loại (UC04), truy xuất `kb_chunk`, kiểm chứng `ai_citation` và kích hoạt fallback (UC07).

## 3. Các Ràng buộc Bất biến (Hard Invariants)

### A. Vòng đời Ticket & State Machine Guard (UC03)
7 trạng thái hợp lệ: `NEW` → `ASSIGNED` → `IN_PROGRESS` ↔ `WAITING_CUSTOMER` → `RESOLVED` → `CLOSED` (và `CANCELLED` từ các trạng thái chưa đóng).
- Mọi thay đổi trạng thái **phải đi qua State Machine Guard**; ném lỗi `400/422` nếu chuyển trạng thái bất hợp pháp.
- Khi chuyển sang `WAITING_CUSTOMER` (hoặc trạng thái tạm dừng theo chính sách), tự động ghi nhận `sla_pause`; khi quay lại xử lý thì đóng phiên pause để cộng bù thời gian SLA.

### B. Cách ly tuyệt đối Ghi chú Nội bộ (`INTERNAL`)
- **Cấm tuyệt đối** trả tin nhắn có `visibility = INTERNAL` cho vai trò `CUSTOMER`.
- Việc lọc `visibility = PUBLIC` phải thực hiện **ngay trong câu SQL/JPQL của Repository** và dùng DTO riêng cho Customer (không lọc ở Frontend, không chỉ dựa vào prompt của LLM).
- Pipeline AI RAG (UC07) **chỉ được phép** đọc dữ liệu từ các `kb_chunk` thuộc `kb_version` đang ở trạng thái **PUBLISHED**; tuyệt đối không index hoặc đưa ghi chú `INTERNAL` vào context gửi cho LLM.

### C. Đồng thời, Idempotency & Transactional Outbox
- **Optimistic Locking:** Mọi API cập nhật ticket bắt buộc nhận `version` từ client và kiểm tra khớp với `@Version` (`ticket.version`); trả `409 Conflict` khi xảy ra ghi đè đồng thời.
- **Chống trùng Webhook:** Kiểm tra `Idempotency-Key` / khóa duy nhất trong `inbound_event` (dùng Generated Column + `UNIQUE` theo chuẩn MySQL 8.0) trong cùng transaction tạo ticket; nếu trùng thì trả lại kết quả cũ một cách an toàn (idempotent), không tạo ticket thứ hai.
- **Transactional Outbox:** Ghi bản ghi vào `outbox_event` **trong cùng database transaction** với thao tác nghiệp vụ (ví dụ tạo ticket). Background Worker đọc và xử lý bất đồng bộ bằng `SELECT ... FOR UPDATE SKIP LOCKED` (MySQL 8.0) để tránh xử lý trùng.
- **Chống trùng cảnh báo SLA:** Scheduler quét định kỳ phải kiểm tra/chèn ràng buộc duy nhất vào bảng `sla_event` (ví dụ theo `ticket_id + sla_type + event_type`) để đảm bảo mỗi mốc cảnh báo 80% hoặc quá hạn chỉ bắn đúng 1 lần.

### D. An toàn AI & RAG (UC04 & UC07)
- **UC04 (Phân loại):** Yêu cầu LLM trả về JSON đúng schema; nếu parse lỗi, confidence thấp hoặc không có Agent khả dụng → **Fallback sang hàng đợi Supervisor**.
- **UC07 (RAG có dẫn nguồn):**
  - Mọi câu trả lời AI gửi khách phải đính kèm ít nhất 1 `ai_citation` trỏ tới `kb_chunk` hợp lệ thuộc phiên bản KB đang **PUBLISHED**.
  - Nếu bài KB bị **thu hồi (REVOKED/ARCHIVED)**, các `kb_chunk` của nó lập tức bị loại khỏi kết quả truy xuất RAG.
  - Chống Prompt Injection: Tách biệt System Instruction và User Input, bỏ qua mọi yêu cầu từ khách hàng đòi lộ prompt, bỏ qua luật hoặc truy cập dữ liệu nội bộ.
  - Nếu không tìm thấy chunk đủ liên quan hoặc kiểm chứng trích dẫn thất bại → **Fallback an toàn sang Agent** (không bịa câu trả lời).

## 4. Bộ 8 Ca Kiểm thử Trọng tâm (Bắt buộc có Integration Test xanh)
1. **Chống trùng Webhook:** Gửi 2 request cùng `Idempotency-Key` chỉ tạo đúng 1 ticket và 1 `inbound_event`.
2. **Cách ly ghi chú nội bộ:** Customer gọi API xem chi tiết/hội thoại ticket hoàn toàn không thấy message `INTERNAL`.
3. **Xung đột đồng thời (`@Version`):** 2 Agent cùng cập nhật 1 ticket với cùng `version` → 1 thành công, 1 nhận `409 Conflict`.
4. **Phân quyền RBAC & Ownership:** Customer không xem được ticket của người khác; Agent không thao tác trái phép ngoài nhóm/quyền.
5. **AI Classification & Routing Fallback:** Khi LLM lỗi/timeout hoặc không có Agent rảnh, ticket được đẩy an toàn về hàng đợi Supervisor.
6. **AI RAG Fallback & Chống rò rỉ:** Khi không có nguồn KB khớp (hoặc gặp prompt injection), AI không bịa câu trả lời và kích hoạt fallback sang Agent.
7. **Thu hồi KB (Revocation):** Ngay sau khi thu hồi `kb_version`, truy vấn RAG không còn trích xuất hay dẫn nguồn từ các `kb_chunk` của phiên bản đó.
8. **Chống trùng cảnh báo SLA:** Chạy SLA Scheduler nhiều lần liên tiếp cho cùng 1 ticket vượt ngưỡng 80% / quá hạn chỉ sinh đúng 1 bản ghi `sla_event` cho mỗi mốc.
