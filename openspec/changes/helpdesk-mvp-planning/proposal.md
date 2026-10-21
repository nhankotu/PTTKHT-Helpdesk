# Proposal: Ghi nhận tiến độ và chuẩn bị triển khai Helpdesk đa kênh

## Why

Workspace có tài liệu phân tích, kế hoạch tuần 1 theo bốn nhóm chức năng/tám Use Case UC01–UC08, và một prototype Helpdesk cục bộ được triển khai riêng. Change này theo dõi trạng thái phần mềm và đặc tả kỹ thuật; không xem prototype là bằng chứng nhóm đã hoàn thành sản phẩm bàn giao tuần 1. Kế hoạch phân công TV1–TV5 và đầu ra tuần đầu nằm tại `Ke-hoach-tuan-1-Use-Case-Chu-de-07.md`.

## What Changes

- Ghi nhận các yêu cầu nghiệp vụ MVP về ticket, tiếp nhận kênh, AI/KB và SLA.
- Lưu thiết kế kiến trúc, mô hình dữ liệu, API đề xuất và các quyết định triển khai ban đầu.
- Triển khai bản cục bộ tại `helpdesk/` với FastAPI, SQLite và giao diện tiếng Việt; có xác thực, ticket, email mô phỏng, NLP/KB, SLA và dashboard.
- Bổ sung FAQ khớp chính xác có phê duyệt theo phiên bản, cùng outbox gửi tin nhắn, retry và migration giữ dữ liệu cũ.
- Theo dõi tiến độ bằng checklist OpenSpec: tài liệu đã hoàn thành được đánh dấu xong; xây dựng và kiểm thử phần mềm vẫn để mở.
- Chia đặc tả theo các năng lực nghiệp vụ để có thể triển khai và kiểm tra độc lập.
- Liên kết backlog triển khai với tám Use Case tuần 1, đồng thời giữ riêng trạng thái học phần và trạng thái prototype.

## Capabilities

### New Capabilities
- `channel-ingestion`: Tiếp nhận và chuẩn hóa yêu cầu từ các kênh, bảo đảm sự kiện lặp không tạo dữ liệu trùng.
- `ticket-management`: Quản lý ticket, hội thoại, trạng thái, phân công và quyền truy cập.
- `knowledge-ai`: Phân loại yêu cầu và trả lời dựa trên KB có trích nguồn cùng cơ chế chuyển cho nhân viên.
- `sla-monitoring`: Theo dõi thời hạn SLA, cảnh báo, chuyển cấp và đo lường kết quả.

### Modified Capabilities

Không có; workspace chưa có đặc tả năng lực OpenSpec trước change này.

## Impact

- Tài liệu OpenSpec tại `openspec/changes/helpdesk-mvp-planning/`.
- Tài liệu phân tích và thiết kế hiện có tại `Phan-tich-thiet-ke-Chu-de-07-Helpdesk.md`.
- Ứng dụng, API, schema dữ liệu, frontend, baseline NLP/KB và kiểm thử ở `helpdesk/`; tiến độ thực tế tại `helpdesk/docs/progress.md`.
- Sinh câu trả lời LLM thật, xác nhận nhóm và môi trường công khai vẫn còn mở. FAQ trích nội dung đã duyệt đã có; nội dung sinh tự do không tự gửi.
