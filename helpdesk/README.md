# Deskflow - Helpdesk đa kênh

Bản triển khai cục bộ của chủ đề 7, học phần TK&XD PM 20261. Backend FastAPI, dữ liệu SQLite, giao diện HTML/CSS/JavaScript tiếng Việt. Dữ liệu được lưu thật trên máy, không mất sau khi tải lại trang.

## Quan hệ với kế hoạch nhóm

Kế hoạch phân tích tuần 1 giới hạn ở bốn nhóm chức năng và tám Use Case UC01–UC08; xem [kế hoạch tuần 1](../Ke-hoach-tuan-1-Use-Case-Chu-de-07.md) và [tài liệu phân tích tổng thể](../Phan-tich-thiet-ke-Chu-de-07-Helpdesk.md). Ứng dụng ở đây là prototype đã triển khai độc lập để tham khảo. Nó không thay thế việc nhóm hoàn thành sơ đồ, tám đặc tả và review theo phân công tuần 1; trạng thái code cũng không xác nhận các sản phẩm học phần đã được nghiệm thu.

Mã Use Case dùng xuyên tài liệu: UC01–02 tiếp nhận và tổng hợp ticket; UC03 xử lý ticket; UC04 phân loại/định tuyến; UC05 SLA; UC06 quản lý KB; UC07 RAG; UC08 báo cáo ticket, SLA và CSAT. OpenSpec và backlog theo dõi các yêu cầu kỹ thuật chi tiết.

Phần [TV5–UC08: Báo cáo ticket, SLA và CSAT](../docs/TV5-UC08-Bao-cao.md) có đặc tả, sơ đồ UML đã xuất PNG/SVG và mockup giao diện. Hồ sơ ghi rõ công thức báo cáo hiện có và các lớp/metadata đề xuất cho bước thiết kế tiếp theo.

## Chạy ứng dụng

Yêu cầu: `uv` và Python 3.11 trở lên. Môi trường hiện tại đã có uv; `uv` có thể tự quản lý Python tương thích.

```powershell
Set-Location D:\PTTKHT\helpdesk
uv sync --locked
uv run uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Hoặc từ thư mục `D:\PTTKHT`, chạy `./helpdesk/start.ps1` bằng PowerShell. Mở [http://127.0.0.1:8000](http://127.0.0.1:8000). Dùng Ctrl+C để dừng máy chủ.

- Giao diện: `/`.
- API có tài liệu tương tác: `/docs` (giao diện Swagger tải thư viện từ CDN).
- Hợp đồng API đầy đủ dạng JSON: `/openapi.json`.
- Kiểm tra máy chủ: `/api/health`.
- Cơ sở dữ liệu mặc định: `helpdesk/data/helpdesk.sqlite3`; tự tạo schema và dữ liệu mẫu trong lần chạy đầu.
- Không chạy nhiều worker ứng dụng trong bản cục bộ này; chỉ dùng một tiến trình uvicorn.

## Tài khoản mẫu

Mật khẩu mặc định cho các tài khoản bên dưới là `Demo@20261`. Đây là dữ liệu phục vụ demo cục bộ. Có thể thay mật khẩu seed bằng biến `HELPDESK_DEMO_PASSWORD` trước lần khởi tạo DB đầu tiên; thay biến này không đổi mật khẩu trong DB đã tồn tại.

| Email | Vai trò | Nhóm |
|---|---|---|
| `admin@helpdesk.local` | Admin | Toàn hệ thống |
| `supervisor@helpdesk.local` | Supervisor | Toàn tổ chức demo |
| `agent@helpdesk.local` | Agent | Kỹ thuật |
| `billing@helpdesk.local` | Agent | Thanh toán |
| `account@helpdesk.local` | Agent | Tài khoản |
| `customer@helpdesk.local` | Khách hàng | Nguyễn Linh |
| `customer2@helpdesk.local` | Khách hàng | Trần Nam |

Các nút chọn vai trò ở màn hình đăng nhập điền tài khoản demo và mật khẩu mặc định để tiện dùng thử. Khi thay mật khẩu seed, cần tự nhập mật khẩu đã cấu hình.

## Luồng dùng thử

1. Đăng nhập Khách hàng, tạo yêu cầu về việc quên mật khẩu.
2. Hệ thống lưu ticket và hàng đợi AI; worker phân loại và gán nhóm/Agent.
3. Đăng xuất, đăng nhập Supervisor hoặc Agent thuộc nhóm Tài khoản.
4. Mở ticket, xem nguồn KB và chọn **Dùng làm bản nháp**. Nội dung chỉ được gửi khi nhân viên bấm gửi.
5. Chọn **Nhận xử lý**, gửi phản hồi rồi chọn **Đã giải quyết**.
6. Khách hàng có thể đánh giá, xác nhận hoàn tất hoặc mở lại.
7. Supervisor theo dõi số liệu trong Tổng quan và cảnh báo tại Theo dõi SLA.

Mô phỏng email: Supervisor/Admin chọn **Mô phỏng email**, nhập khách hàng, nội dung và mã sự kiện. Gửi lại cùng mã sự kiện mở ticket đã có; không tạo ticket/tin nhắn trùng. Mọi email đến/đi ở bản này đều là mô phỏng trong cơ sở dữ liệu, không có SMTP hay mailbox thật.

## Chức năng hiện có

- Session cookie HttpOnly/SameSite; mật khẩu PBKDF2; giới hạn thử đăng nhập trong tiến trình.
- Kiểm tra quyền theo chủ ticket và nhóm Agent; ghi chú nội bộ không đưa cho khách hoặc mô hình.
- Ticket, hội thoại, trạng thái hợp lệ, lịch sử và phát hiện cập nhật đồng thời bằng version.
- Biểu mẫu web, email mô phỏng và chống sự kiện trùng trong transaction.
- Phân loại Naive Bayes cục bộ; luật định tuyến tới ba nhóm và Agent ít việc nhất đang sẵn sàng.
- KB có bản nháp, xuất bản, thu hồi và lịch sử phiên bản. Nguồn cũ bị thu hồi sẽ không tiếp tục được dùng cho gợi ý.
- Tìm KB bằng TF-IDF; câu trả lời mặc định là trích nguyên nội dung bài viết, có tên nguồn và phiên bản.
- Adapter sinh câu trả lời Ollama tùy chọn; mọi kết quả vẫn là bản nháp cần nhân viên duyệt.
- FAQ đã duyệt: Supervisor/Admin phê duyệt câu hỏi cho một phiên bản KB; khi khớp chính xác, hệ thống gửi nguyên nội dung KB và nguồn, không dùng văn bản sinh tự do.
- Outbox gửi tin nhắn: trạng thái chờ gửi/thành công/thất bại/hủy, thử lại cùng tin nhắn, chỉ ghi nhận SLA sau khi gửi thành công.
- Hàng đợi SQLite bền vững; phục hồi job RUNNING về QUEUED khi khởi động lại.
- SLA 24/7, phiên bản chính sách, cảnh báo 80%/quá hạn, dashboard, khảo sát hài lòng.
- Admin tạo tài khoản, cập nhật khả năng nhận việc và thêm phiên bản chính sách SLA.

## AI và giới hạn cần phân biệt

Chế độ mặc định `LOCAL_EXTRACTIVE` dùng dữ liệu huấn luyện tổng hợp nhỏ và truy xuất văn bản KB. Đây là baseline NLP/trích xuất; chưa phải bằng chứng chất lượng của một hệ thống RAG sinh bằng LLM trên dữ liệu thực tế. Cần mở rộng dữ liệu và đo chất lượng trước khi nộp báo cáo cuối kỳ.

Gợi ý sinh tự do vẫn là bản nháp để Agent đọc, chỉnh sửa và gửi. Hệ thống chỉ tự gửi FAQ nếu khớp câu hỏi được Supervisor/Admin duyệt cho đúng phiên bản KB đang xuất bản. Dữ liệu seed không tự bật quy tắc nào. Câu hỏi ngoài KB, yêu cầu gặp người, hoàn tiền hoặc lỗi mô hình sẽ có lý do chuyển Agent. Khi ticket/nguồn KB thay đổi, gợi ý cũ bị đánh dấu hết hiệu lực.

### Bật FAQ có kiểm soát

1. Supervisor/Admin vào **Kho tri thức**, chọn **FAQ tự trả lời** trên bài đã xuất bản.
2. Nhập nguyên câu hỏi cho phép, ví dụ `Làm sao để đặt lại mật khẩu tài khoản?`, rồi duyệt.
3. Khách tạo ticket mới với chính nội dung câu hỏi đó, ưu tiên bình thường hoặc thấp. Viết hoa, dấu tiếng Việt, khoảng trắng và dấu câu được chuẩn hóa; thêm nội dung khác sẽ không còn khớp.
4. Worker gửi nguyên bài KB kèm nguồn. Ticket vẫn mở, khách có thể tiếp tục trao đổi hoặc yêu cầu nhân viên.

Mỗi ticket chỉ được tạo một trả lời FAQ tự động. Hệ thống kiểm tra phê duyệt lúc phân tích và ngay trước khi gửi. KB sửa/thu hồi, quy tắc tắt, khách bổ sung tin hoặc nhân viên tiếp quản sẽ ngăn/hủy câu đang chờ. Sau khi cập nhật KB phải duyệt lại phiên bản mới; việc duyệt lại không làm sống lại câu cũ trong hàng đợi.

### Thử lỗi gửi email và thử lại

Supervisor/Admin vào **Hộp thư hỗ trợ → Kênh email mô phỏng**, đặt số lần gửi tiếp theo thất bại (0-5), rồi phản hồi một ticket email. Agent sẽ thấy **Gửi thất bại**, khách chưa thấy tin đó và SLA chưa ghi nhận phản hồi. Chọn **Thử gửi lại** để gửi cùng tin nhắn; hệ thống không tạo bản sao. Chưa thể giải quyết/đóng ticket khi còn phản hồi chờ gửi hoặc bị lỗi.

Đây là mô phỏng lỗi adapter tại máy, không phải kiểm tra SMTP hay dịch vụ email ngoài hệ thống. Để giá trị 0 cho demo thông thường.

Để dùng mô hình Ollama đã cài và tải trên máy:

```powershell
$env:HELPDESK_OLLAMA_MODEL = 'ten-model-da-tai'
$env:HELPDESK_OLLAMA_URL = 'http://127.0.0.1:11434'
uv run uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Máy này chưa được xác nhận có mô hình Ollama chạy thật. Bộ kiểm thử chỉ kiểm tra adapter bằng phản hồi giả lập hợp lệ, lỗi kết nối và nguồn sai. Không ghi nhận phần sinh LLM thực tế là đã nghiệm thu.

File `.env.example` chỉ là cấu hình mẫu; ứng dụng đọc biến môi trường. Nếu dùng một file `.env` tự tạo, khởi động bằng `uv run --env-file .env uvicorn app.main:app --host 127.0.0.1 --port 8000`.

## SLA demo

| Ưu tiên | Phản hồi | Giải quyết |
|---|---:|---:|
| Khẩn cấp | 15 phút | 4 giờ |
| Cao | 1 giờ | 8 giờ |
| Bình thường | 4 giờ | 24 giờ |
| Thấp | 8 giờ | 48 giờ |

Worker quét hạn khoảng 15 giây/lần và xử lý outbox khoảng 0,5 giây/lượt. Cảnh báo là bản ghi trên dashboard; chưa có email thông báo thật. `WAITING_CUSTOMER` vẫn tính thời gian, ticket mở lại dùng hạn ban đầu. Phản hồi Agent hoặc FAQ được tính khi adapter ghi nhận gửi thành công; pending/failed/cancelled không tính. Mỗi ticket giữ phiên bản chính sách đã áp dụng khi tạo.

Tỷ lệ SLA là tỷ lệ các mốc đã hoàn tất hoặc đã đến hạn được đáp ứng đúng hạn. Báo cáo lọc theo cohort ticket được tạo trong khoảng 7/30/90 ngày hoặc tham số `days` của API; không phải chỉ đếm các sự kiện xảy ra trong khoảng đó.

## Kiểm thử và đánh giá

```powershell
uv run pytest -q
uv run python tools/evaluate_ai.py
```

Kiểm thử dùng cơ sở dữ liệu tạm riêng, không sửa dữ liệu demo. Đánh giá AI xuất kết quả tới `data/evaluation-result.json` và nêu rõ giới hạn của tập dữ liệu.

Kiểm tra trình duyệt tùy chọn, khi server ở cổng 8000 đang chạy và máy có Chrome:

```powershell
uv run --with playwright python tools/browser_smoke.py
```

Có thể đặt `HELPDESK_BROWSER` tới đường dẫn trình duyệt Chromium khác. Bài smoke tạo một ticket kiểm thử trong DB của server đang chạy và lưu ảnh tại `data/qa/`. Không dùng smoke này trên dữ liệu vận hành thật.

Để kiểm tra riêng FAQ, cập nhật trên màn hình khách và gửi lỗi/thử lại bằng trình duyệt, chạy server với DB QA trống riêng ở cổng 8010, sau đó chạy `uv run --with playwright python tools/browser_automation_smoke.py` ở cửa sổ khác. Có thể đặt `HELPDESK_BASE_URL` cho cả hai script để đổi URL. Script automation tạo quy tắc FAQ và sự kiện email có mã cố định, nên mỗi lần nghiệm thu nên dùng một DB QA mới (không xóa DB đang chứa dữ liệu người dùng).

## Cấu trúc

```text
helpdesk/
  app/
    main.py          # API, xác thực và các đầu vào HTTP
    db.py            # Schema có phiên bản và dữ liệu seed
    security.py      # Hash mật khẩu và session token
    services.py      # Workflow ticket, định tuyến, job AI và quét SLA
    ai.py            # Naive Bayes, TF-IDF và adapter Ollama
    automation.py    # FAQ khớp chính xác, phê duyệt gắn với phiên bản KB
    delivery.py      # Outbox và adapter cổng web/email mô phỏng
  static/            # Giao diện tiếng Việt, responsive
  tests/             # Các ca bảo vệ nghiệp vụ và tích hợp
  tools/             # Đánh giá AI và smoke trình duyệt
  data_examples/     # Tập đánh giá tổng hợp nhỏ
  docs/              # Kiến trúc, backlog, tiến độ
  data/              # DB và đầu ra runtime; được ignore
```

## Việc còn lại

- Hoàn thành sản phẩm bàn giao tuần 1: sơ đồ Use Case tổng thể, sơ đồ chi tiết tám UC, tám đặc tả cùng mẫu và biên bản review; TV1–TV5 cập nhật tên thật, reviewer và hạn cụ thể.
- Sau khi nhóm chốt tuần 1, xác nhận stack/chính sách và lập backlog có tiêu chí, người phụ trách, hạn; hoàn thiện ERD, wireframe, class/sequence/package diagram và hợp đồng API cho D2.
- Chạy hướng dẫn trên máy các thành viên; khảo sát 2-3 sản phẩm và hoàn thiện tài liệu D1/D2 theo yêu cầu môn.
- Kiểm chứng mô hình sinh thực tế và grounding; FAQ tự động hiện chỉ dùng nguyên bài KB đã duyệt.
- Tích hợp mailbox/kênh và thông báo thật nếu chọn làm mở rộng.
- Đính kèm tệp, thông báo realtime và các tính năng bonus.
- Triển khai HTTPS, cấu hình vận hành và đo tải trước khi đưa lên môi trường công khai.

Tiến độ được theo dõi ở `../openspec/changes/helpdesk-mvp-planning/tasks.md`. Những việc chưa có bằng chứng nghiệm thu vẫn để mở.
