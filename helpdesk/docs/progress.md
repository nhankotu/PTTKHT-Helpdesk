# Tiến độ triển khai - mốc 2

## Phân biệt tiến độ nhóm và tiến độ prototype

Kế hoạch tuần 1 xác định phạm vi phân tích là bốn nhóm chức năng, tám Use Case UC01–UC08, phân công TV1–TV5 và lịch bảy ngày; xem [kế hoạch tuần 1](../../Ke-hoach-tuan-1-Use-Case-Chu-de-07.md). Đây là kế hoạch và khung phân công, chưa xác nhận nhóm đã hoàn tất sơ đồ, tám đặc tả, review, hoặc đã điền tên/thời hạn. Phần dưới chỉ mô tả prototype cục bộ và bằng chứng kiểm tra của prototype.

Hồ sơ cá nhân [TV5–UC08: Báo cáo ticket, SLA và CSAT](../../docs/TV5-UC08-Bao-cao.md) đã được hoàn thiện ở mức phân tích/thiết kế: 9 sơ đồ có PNG/SVG/PlantUML, 6 mockup GUI, đặc tả trường/control, công thức và dữ liệu minh họa có thể tính lại. Các lớp Controller/Repository và metadata mới trong hồ sơ là thiết kế đề xuất, chưa đánh dấu thành code đã triển khai. Việc duyệt bởi thành viên thật và xác nhận rubric vẫn chờ nhóm.

## Kết quả hiện tại

Đã có ứng dụng cục bộ chạy được tại `helpdesk/`, dùng FastAPI, SQLite và giao diện web tiếng Việt. Không chỉ là tài liệu hoặc giao diện giả lập dữ liệu: đăng nhập, ticket, hội thoại, KB, SLA và báo cáo đều đọc/ghi DB.

OpenSpec change `helpdesk-mvp-planning` theo dõi 30 đầu việc; 22 việc đã hoàn thành, 8 việc còn mở. Ba đầu việc vừa bổ sung ghi nhận kế hoạch tuần 1 gồm tám Use Case, phân công năm thành viên và hồ sơ phân tích UC08 cho TV5. Hai đầu việc kỹ thuật bổ sung trước đó là FAQ có phê duyệt và outbox/migration; kiểm thử lỗi kênh trong phạm vi mô phỏng cũng đã hoàn tất. Đây là số đầu việc checklist, không phải phần trăm khối lượng hoặc tuyên bố đã hoàn tất yêu cầu môn học.

## Đã triển khai

- Bốn vai trò, session cookie HttpOnly/SameSite, hash mật khẩu, quyền trên ticket và nhóm.
- Ticket, bộ lọc, phân trang, hội thoại, ghi chú riêng, trạng thái, version và lịch sử.
- Web intake và email mô phỏng; chống lặp sự kiện trong transaction.
- Worker nền với queue DB; phân loại Naive Bayes, luật định tuyến và chọn Agent sẵn sàng ít việc nhất.
- KB soạn nháp, xuất bản, thu hồi, phiên bản; kiểm tra lại nguồn trước khi dùng gợi ý.
- TF-IDF truy xuất bài KB và câu trả lời trích xuất có nguồn; gợi ý tự do chờ Agent duyệt.
- Supervisor/Admin duyệt câu hỏi FAQ theo phiên bản KB; tự trả lời bằng nguyên bài đã duyệt khi khớp chính xác. Không dùng văn bản LLM để tự gửi.
- Kiểm tra lại FAQ khi gửi: nguồn đổi/thu hồi, quy tắc tắt, khách nhắn thêm hoặc nhân viên can thiệp sẽ hủy câu đang chờ; duyệt lại phiên bản mới không làm sống lại câu cũ.
- Outbox gửi công khai, trạng thái PENDING/DELIVERED/FAILED/CANCELLED, retry cùng message_id và chỉ tính SLA sau khi gửi thành công.
- Migration schema 1 → 2 → 3 giữ ticket, tài khoản, session và KB; xử lý an toàn FAQ cũ không có snapshot phiên bản.
- Adapter Ollama có timeout, kiểm tra mã nguồn trích dẫn và fallback.
- SLA 24/7 với chính sách có phiên bản, cảnh báo định kỳ, dashboard và đánh giá hài lòng.
- Admin tạo người dùng, thay trạng thái nhận việc và tạo chính sách SLA mới.
- Hướng dẫn chạy, cấu hình mẫu, khóa dependency, dữ liệu seed, kiến trúc và backlog tham chiếu.

## Kiểm tra đã thực hiện

### API và nghiệp vụ

`uv run pytest -q`: **32 passed**. Có một cảnh báo deprecation từ cặp Starlette TestClient/httpx đang dùng; không có test thất bại.

Kiểm tra gồm: đăng nhập/đăng xuất; cookie flags; quyền khách hàng và Agent; ghi chú nội bộ; email trùng; vòng đời ticket; trả lời và đánh giá; xung đột version; mở lại; SLA dedup/snapshot; câu hỏi ngoài KB; lỗi mô hình; nguồn bị thu hồi; kết quả AI cũ; input rỗng; Origin khác; tài khoản trùng và phân công sai nhóm. Adapter Ollama được kiểm tra với nguồn hợp lệ/sai bằng phản hồi giả lập.

Mốc 2 bổ sung ca quyền phê duyệt FAQ, câu trùng sau chuẩn hóa, snapshot nguồn, một lần/ticket, yêu cầu người/ưu tiên cao, thu hồi giữa phân tích và gửi, nguồn đổi rồi duyệt lại, lỗi adapter, retry không trùng, khách không thấy tin chưa gửi, chặn đóng khi còn phản hồi lỗi và nâng cấp DB giữ dữ liệu. Đây là kiểm tra lỗi adapter email mô phỏng, chưa phải SMTP bên ngoài.

### Trình duyệt thực

`uv run --with playwright python tools/browser_smoke.py`: thành công trên Chrome headless có sẵn trên máy.

- Đăng nhập cả bốn vai trò.
- Khách tạo ticket; Supervisor dùng nháp KB, phản hồi và giải quyết.
- Agent soạn bài nháp; Supervisor xuất bản bài.
- Admin tạo tài khoản và lưu phiên bản chính sách SLA.
- Kiểm tra màn hình desktop 1440px và mobile 390px; trang không tràn ngang, bảng có vùng cuộn riêng, nút đăng xuất vẫn dùng được trên mobile.
- Không ghi nhận JavaScript page error trong luồng kiểm tra.

Ảnh kiểm tra tạo tại `data/qa/`. Smoke tạo dữ liệu demo trong DB đang chạy; không dùng với dữ liệu vận hành thật.

`tools/browser_automation_smoke.py` cũng đã chạy thành công bằng Chrome trên server/DB QA riêng: phê duyệt FAQ qua giao diện, khách mobile nhận câu tự động qua polling, email lỗi hiển thị trạng thái, nút giải quyết bị chặn, retry thành công rồi giải quyết. Không có JavaScript page error. Ảnh: `faq-approval.png`, `faq-customer-mobile.png`, `email-delivery-failed.png` trong `data/qa/`.

### Baseline NLP

`uv run python tools/evaluate_ai.py` đã chạy trên DB tạm với tập tổng hợp nhỏ, không có dữ liệu khách thật:

| Chỉ số | Kết quả đo |
|---|---:|
| Số câu phân loại | 12 |
| Accuracy | 1.0 |
| Macro-F1 | 1.0 |
| Truy xuất top-1 | 3/3 |
| Chuyển người theo các câu kiểm tra | 3/3 |
| Đáp án trích xuất khớp nội dung nguồn | 3/3 |
| Độ trễ trung bình gợi ý cục bộ của lần đo | 0.902 ms |
| Số lời gọi nhà cung cấp ngoài | 0 |

Tập này rất nhỏ và có cấu trúc gần miền kiến thức seed; kết quả không chứng minh chất lượng tổng quát hoặc chất lượng sinh LLM. Cần tập độc lập lớn hơn, ca mơ hồ, tài liệu mâu thuẫn và chấm chất lượng câu trả lời khi có mô hình thật. File kết quả tái tạo được ở `data/evaluation-result.json`.

## Chưa nghiệm thu hoặc chưa triển khai

- Chạy mô hình Ollama thật và đánh giá câu trả lời sinh; adapter thành công hiện chỉ được kiểm tra bằng mock.
- Tự gửi nội dung sinh tự do từ LLM; nhánh tự gửi hiện chỉ dành cho FAQ khớp chính xác và KB đã duyệt.
- Email/mailbox/SMTP thật và tình huống delivery thất bại ngoài hệ thống mô phỏng.
- Thông báo cảnh báo qua email, websocket, tệp đính kèm và bonus.
- Tài liệu D1/D2 nộp đầy đủ, khảo sát sản phẩm, lịch giao việc có tên thật và chạy trên các máy thành viên.
- Deploy môi trường công khai, kiểm tra tải và tài liệu vận hành production.
- Báo cáo D6, AI Feature Report chính thức và buổi tập dượt demo của nhóm.

## Bước ưu tiên tiếp theo

1. Nhóm chạy `README.md` trên từng máy và gán A-E cho tên thật.
2. Nghiệm thu mô hình sinh và nguồn trích dẫn bằng bộ câu hỏi độc lập; giữ nhánh tự gửi giới hạn trong FAQ đã duyệt.
3. Hoàn thiện các tài liệu nộp môn và kịch bản demo dựa trên code đang chạy.
