# Tasks

## 1. Phân tích và thiết kế - đã hoàn thành

- [x] 1.1 Đọc đề tài 7 và yêu cầu chung; xác nhận phạm vi MVP trong `Phan-tich-thiet-ke-Chu-de-07-Helpdesk.md`
- [x] 1.2 Lập thiết kế nghiệp vụ, trạng thái ticket, kiến trúc, dữ liệu, AI/RAG và API; kiểm tra nội dung trong tài liệu thiết kế
- [x] 1.3 Đề xuất cách chia module cho nhóm 5 người và lộ trình D1-D6; ghi trong tài liệu thiết kế
- [x] 1.4 Khởi tạo cấu trúc OpenSpec và tạo các đặc tả hành vi cho MVP; xác nhận bằng `openspec status --change helpdesk-mvp-planning`
- [x] 1.5 Cập nhật kế hoạch tuần 1 theo đúng bốn nhóm chức năng và tám Use Case UC01–UC08; xác minh Actor, phạm vi và tiêu chí nghiệm thu thống nhất trong tài liệu
- [x] 1.6 Phân công tuần 1 cho năm thành viên, gắn đầu ra cá nhân và lịch bảy ngày; phân biệt phân công tuần đầu với phương án chia module triển khai về sau
- [x] 1.7 Hoàn thiện hồ sơ TV5–UC08: đặc tả/công thức KPI; UML chi tiết, lớp, trình tự, giao tiếp, hoạt động, chuyển màn hình và interface module; render 9 sơ đồ PNG/SVG, 6 mockup GUI; đối chiếu bộ 6 ticket mẫu và ghi rõ phần thiết kế đề xuất/cần nhóm duyệt

## 2. Chốt yêu cầu và tạo dự án

- [ ] 2.1 Thống nhất stack, cấu trúc repository và quy ước code; xác minh dự án mới chạy được trên máy của cả nhóm
- [ ] 2.2 Chốt vai trò, danh mục ticket, SLA demo và quy tắc đóng/mở lại; cập nhật đặc tả và xác minh các thành viên duyệt cùng một phiên bản
- [ ] 2.3 Tạo backlog có user story, tiêu chí chấp nhận, người phụ trách và deadline; xác minh mọi yêu cầu Must có ít nhất một thẻ
- [ ] 2.4 Hoàn thiện ERD, wireframe, class/sequence/package diagram và hợp đồng API; xác minh các module dùng thống nhất trường dữ liệu và mã lỗi

## 3. Xây dựng nền tảng ticket và kênh

- [x] 3.1 Tạo schema tài khoản, nhóm, vai trò, ticket, message và event log; xác minh migration chạy trên cơ sở dữ liệu rỗng
- [x] 3.2 Xây đăng nhập, phân quyền và bảo vệ ticket theo chủ sở hữu/nhóm; xác minh khách không đọc được ticket hoặc ghi chú nội bộ của người khác
- [x] 3.3 Xây tạo ticket, hội thoại, tìm kiếm và chuyển trạng thái; xác minh luồng khách gửi yêu cầu đến Agent giải quyết
- [x] 3.4 Xây adapter web và email mô phỏng cùng xử lý idempotent; xác minh gửi lại cùng `external_event_id` không tạo dữ liệu trùng
- [x] 3.5 Xây luật định tuyến và phân công Agent; xác minh danh mục được ánh xạ đúng nhóm và có hàng đợi khi không có Agent khả dụng
- [x] 3.6 Xây outbox, adapter gửi mô phỏng, thử lại và migration giữ dữ liệu; kiểm tra tin lỗi không tới khách, không tính SLA và retry không nhân bản message

## 4. Xây KB và tích hợp AI

- [x] 4.1 Xây quản lý bài KB, phiên bản, trạng thái xuất bản và phân quyền; xác minh bài nháp/đã thu hồi không xuất hiện trong kết quả truy xuất
- [x] 4.2 Xây phân loại ticket và lưu kết quả AI; xác minh đầu ra sai định dạng/lỗi mô hình chuyển sang xử lý thủ công
- [ ] 4.3 Xây truy xuất KB, sinh câu trả lời có trích dẫn và fallback; xác minh câu hỏi không có nguồn được chuyển Agent
- [ ] 4.4 Tạo tập đánh giá có nhãn và bộ câu hỏi KB; xác minh báo cáo macro-F1, chất lượng nguồn, fallback và độ trễ được tính từ dữ liệu thử nghiệm
- [x] 4.5 Xây FAQ khớp chính xác có phê duyệt theo phiên bản; kiểm tra không gửi khi yêu cầu người, ưu tiên cao, nguồn đổi/thu hồi hoặc quy tắc bị tắt

Baseline cục bộ của 4.3/4.4 đã có TF-IDF, trích xuất KB, nguồn, fallback, tập tổng hợp và script đánh giá. FAQ theo phê duyệt đã hoàn thành ở 4.5. Vẫn để mở 4.3/4.4 vì mô hình sinh LLM thật và đánh giá chất lượng sinh chưa được nghiệm thu; kiểm tra adapter Ollama hiện dùng mock.

## 5. Xây SLA, báo cáo và trải nghiệm

- [x] 5.1 Xây chính sách SLA có phiên bản và lưu hạn lên ticket; xác minh ticket giữ chính sách đã áp dụng khi cấu hình mới được tạo
- [x] 5.2 Xây bộ lập lịch cảnh báo và chuyển cấp; xác minh tác vụ chạy lại không gửi cảnh báo trùng
- [x] 5.3 Xây dashboard và khảo sát hài lòng; xác minh số liệu phản hồi, SLA và đánh giá khớp với dữ liệu ticket mẫu
- [x] 5.4 Hoàn thiện giao diện khách hàng, Agent, Supervisor và Admin; xác minh các luồng chính thao tác được ở màn hình hẹp và rộng

## 6. Tích hợp, kiểm thử và bàn giao

- [x] 6.1 Chạy kiểm thử tích hợp cho tiếp nhận → định tuyến → phản hồi/RAG → giải quyết; xác minh lịch sử trạng thái và hội thoại đầy đủ
- [x] 6.2 Chạy kiểm thử lỗi AI, kênh gửi thất bại, xung đột cập nhật và quyền truy cập; xác minh fallback không làm mất ticket hoặc lộ dữ liệu trong phạm vi adapter cục bộ/mô phỏng
- [ ] 6.3 Hoàn thiện README, cấu hình mẫu và dữ liệu seed; xác minh một thành viên khác có thể dựng ứng dụng theo hướng dẫn
- [ ] 6.4 Tập dượt demo và hoàn thiện báo cáo D6/AI Feature Report; xác minh demo có tình huống AI thiếu nguồn hoặc không khả dụng

## Bằng chứng triển khai cục bộ

- Code và README: `helpdesk/`.
- Kiểm thử API/nghiệp vụ: `helpdesk/tests/test_workflows.py`.
- Kiểm tra giao diện bốn vai trò: `helpdesk/tools/browser_smoke.py`.
- Kiểm tra FAQ và gửi lỗi/thử lại bằng trình duyệt: `helpdesk/tools/browser_automation_smoke.py`.
- Đánh giá NLP tổng hợp nhỏ: `helpdesk/tools/evaluate_ai.py`.
- Chi tiết kết quả và giới hạn: `helpdesk/docs/progress.md`.
- Các mục 2.1-2.4 còn mở vì cần thành viên thật xác nhận, lịch phân công và tài liệu nộp đầy đủ; scaffold, cấu hình demo và backlog tham chiếu đã có.
