# Design: Trạng thái và hướng triển khai Helpdesk đa kênh

## Context

Khi bắt đầu change, workspace chỉ chứa tài liệu môn học và bản phân tích/thiết kế Markdown. Sau yêu cầu triển khai tiếp, ứng dụng cục bộ đã được tạo tại `helpdesk/`: FastAPI, SQLite và frontend HTML/CSS/JavaScript. Phân tích gốc ở `Phan-tich-thiet-ke-Chu-de-07-Helpdesk.md`; chi tiết kiến trúc thực tế và các khác biệt so với bản thiết kế ban đầu ở `helpdesk/docs/architecture.md`.

Kế hoạch học phần được cập nhật riêng theo bốn nhóm chức năng và tám Use Case UC01–UC08, với phân công tuần 1 TV1–TV5. Kế hoạch nằm ở `Ke-hoach-tuan-1-Use-Case-Chu-de-07.md`. Các UC là khung phân tích của nhóm; checklist change theo dõi prototype. Luồng đã có trong code không đánh dấu nhóm hoàn tất sơ đồ, đặc tả hay review tuần 1.

## Goals / Non-Goals

**Goals:**

- Làm rõ trạng thái thực tế để nhóm không nhầm tài liệu thiết kế với phần mềm đã chạy.
- Chuẩn hóa yêu cầu MVP và tạo checklist từ thiết lập dự án đến kiểm thử tích hợp.
- Giữ MVP đủ nhỏ cho nhóm 5 người và hỗ trợ mô phỏng kênh để demo.

**Non-Goals:**

- Bản cục bộ chưa tích hợp kênh thật, thông báo email thật hoặc môi trường hosting công khai.
- Không tự gửi câu trả lời AI cho mọi ticket; chỉ tự động trong phạm vi đã có nguồn KB phù hợp.
- Không đưa phân tích cảm xúc, tóm tắt hội thoại dài và tích hợp nhiều mạng xã hội thật vào MVP.

## Decisions

### Chọn stack cục bộ và giữ phiên bản phụ thuộc

Backend FastAPI/Pydantic, SQLite, frontend HTML/CSS/JavaScript cùng origin. `uv.lock` khóa phiên bản; `helpdesk/start.ps1` chạy trên Windows. Đây là quyết định triển khai cục bộ để có sản phẩm demo; việc các thành viên xác nhận chạy trên máy mình vẫn được để mở trong tasks.

### AI triển khai theo hai tầng

Baseline hiện có là phân loại Naive Bayes trên tập tổng hợp nhỏ và TF-IDF truy xuất bài KB. Câu trả lời mặc định trích nguyên nội dung nguồn, không sinh bằng LLM. Adapter Ollama tùy chọn có kiểm tra trích dẫn và timeout; chưa chạy mô hình thật trên máy này. Câu sinh tự do là nháp. Nhánh tự trả lời chỉ dùng nguyên bài KB được duyệt cho một câu hỏi khớp chính xác và phiên bản nguồn cụ thể; không coi nhánh này là nghiệm thu RAG sinh bằng LLM.

### Duyệt FAQ và kiểm tra lại trước khi gửi

Supervisor/Admin duyệt mẫu câu hỏi cùng phiên bản KB. Chỉ ticket mới NORMAL/LOW, chưa có nhân viên can thiệp, không yêu cầu người hoặc giao dịch nhạy cảm mới tự trả lời. Mỗi ticket chỉ có một `faq_delivery`; snapshot phiên bản gốc giữ bất biến để duyệt lại KB mới không làm sống lại câu cũ. Tắt quy tắc, đổi/thu hồi nguồn hoặc thêm hội thoại sẽ ngăn/hủy câu tự động trước khi gửi.

### Outbox và thời điểm ghi nhận SLA

Phản hồi công khai từ staff/FAQ được xếp hàng trong cùng transaction tạo message. Adapter cổng web và email mô phỏng xử lý gửi; chỉ thành công mới tính SLA. Lỗi lưu FAILED, nhân viên có thể retry cùng message_id; không tạo bản sao. Message PENDING/FAILED/CANCELLED không hiển thị với khách. API chặn giải quyết/đóng khi còn phản hồi chưa gửi thành công.

### Dùng ứng dụng nguyên khối chia module

Tổ chức backend theo module nghiệp vụ trong một ứng dụng và cơ sở dữ liệu chung. Điều này giảm công sức vận hành cho bài tập nhóm nhưng vẫn tách được `channels`, `tickets`, `knowledge`, `ai`, `sla` và `reporting`. Tách microservice hiện không phù hợp vì làm tăng triển khai, giao tiếp giữa dịch vụ và xử lý lỗi phân tán.

### Lưu ticket trước rồi xử lý AI bất đồng bộ

Tiếp nhận phải lưu ticket thành công trước khi gọi mô hình. Tác vụ AI có thể chạy qua hàng đợi; lỗi mô hình không làm mất yêu cầu và Agent vẫn xử lý được. Cách gọi AI đồng bộ trực tiếp trong request ít thành phần hơn nhưng khiến việc tiếp nhận phụ thuộc độ trễ và khả dụng của nhà cung cấp AI.

### Chuẩn hóa sự kiện bằng Adapter và chống trùng theo mã ngoài

Mỗi kênh chuyển payload về định dạng chung. Khóa duy nhất `(channel_id, external_event_id)` chống việc nhà cung cấp gửi lại sự kiện tạo nhiều tin nhắn. MVP dùng biểu mẫu web và email mô phỏng; adapter cho mạng xã hội thật là phần mở rộng.

### AI đề xuất; luật nghiệp vụ quyết định định tuyến

AI trả danh mục có cấu trúc; quy tắc ánh xạ danh mục tới nhóm. Chọn Agent bằng tình trạng sẵn sàng và số ticket đang mở, có thể sửa tay. Cách này dễ giải thích và kiểm thử hơn để LLM tự quyết định nhân viên.

### RAG chỉ trả lời tự động khi có nguồn được phép sử dụng

Retriever lọc bài KB đã xuất bản, còn hiệu lực và phù hợp quyền truy cập. Câu trả lời cần trích dẫn bài nguồn. Khi thiếu nguồn, nguồn không nhất quán, khách yêu cầu Agent hoặc dịch vụ AI lỗi, hệ thống chuyển ticket cho người xử lý.

### Theo dõi ticket bằng trạng thái và nhật ký sự kiện

Trạng thái MVP: `NEW`, `IN_PROGRESS`, `WAITING_CUSTOMER`, `RESOLVED`, `CLOSED`. Thay đổi trạng thái, người phụ trách và ưu tiên được ghi vào lịch sử. Cập nhật có phiên bản để phát hiện hai người sửa cùng lúc.

### SLA MVP tính liên tục 24/7

Lưu chính sách đã áp dụng lên từng ticket và hạn phản hồi/hạn giải quyết. Cấu hình giờ hành chính và tạm dừng SLA để sau vì cần thêm lịch làm việc và khoảng tạm dừng. Các thời lượng ví dụ trong tài liệu thiết kế là cấu hình demo, nhóm cần thống nhất trước khi chốt.

## Risks / Trade-offs

- **Sai phân loại làm định tuyến sai** → Cho phép sửa danh mục/nhóm, ghi nhận lần sửa và có hàng đợi phân loại thủ công.
- **AI tạo nội dung không có căn cứ** → Chỉ tự gửi khi có nguồn KB hợp lệ; lưu nguồn và hỗ trợ chuyển Agent.
- **Rò rỉ nội dung nội bộ** → Phân biệt visibility của tin nhắn ở tầng dữ liệu, API và prompt; kiểm thử quyền truy cập.
- **Bộ dữ liệu nhỏ làm chỉ số thiếu tin cậy** → Ghi rõ kích thước và cách tạo tập đánh giá; không báo cáo số liệu khi chưa đo.
- **Tính SLA khác kỳ vọng nghiệp vụ** → Công bố quy tắc 24/7, thời điểm tính phản hồi và quy tắc mở lại ticket trong demo/tài liệu.
- **Các module ghép nối không khớp** → Thống nhất ERD và hợp đồng API trước; tích hợp sớm bằng dữ liệu/API giả lập.

## Migration Plan

1. Tạo cấu trúc dự án, quyết định stack và khởi tạo schema dữ liệu.
2. Xây dựng ticket và hội thoại trước, rồi nối adapter tiếp nhận.
3. Thêm phân loại, KB/RAG và fallback; ticket vẫn hoạt động khi dịch vụ AI lỗi.
4. Thêm SLA, cảnh báo và báo cáo.
5. Seed dữ liệu mẫu, kiểm thử các luồng đầu-cuối và chuẩn bị demo.

Schema hiện ở phiên bản 3. Migration 2 thêm outbox/FAQ/cấu hình lỗi mô phỏng, migration 3 thêm snapshot nguồn trên FAQ đã xếp hàng. Test kiểm tra nâng cấp từ v1 giữ dữ liệu, và FAQ v2 thiếu snapshot được hủy an toàn khi nâng cấp. Các test API dùng DB tạm riêng; không xóa DB người dùng để cập nhật ứng dụng.

## Open Questions

- Nhóm cần xác nhận stack đã chọn và chạy hướng dẫn trên các máy thành viên.
- SLA demo dùng cấu hình trong README; bản hiện tại đóng bằng xác nhận của khách hoặc thao tác nhân viên, chưa tự đóng theo thời gian. Nhóm cần chốt quy tắc nộp chính thức.
- Cần chọn và nghiệm thu mô hình Ollama thực tế hoặc nhà cung cấp khác, cùng bộ đánh giá chất lượng sinh câu trả lời.
- Email hiện là adapter mô phỏng. Tích hợp mailbox sandbox là quyết định mở rộng sau khi nhóm chốt phạm vi.
