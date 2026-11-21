# Kế hoạch tuần 1 — Phân tích và thiết kế Use Case

**Đề tài:** Nền tảng Helpdesk đa kênh tích hợp AI  
**Quy mô:** 05 thành viên  
**Giai đoạn:** Bắt đầu từ số 0  
**Thời lượng:** 07 ngày

## 1. Mục tiêu và phạm vi

Tuần đầu làm rõ yêu cầu, Actor và tương tác chính; thiết kế Use Case tổng thể và đặc tả cơ bản. Chưa lập trình, chưa thiết kế cơ sở dữ liệu. Nhóm giữ đúng bốn nhóm chức năng của đề bài và giới hạn phạm vi ở tám Use Case dưới đây.

Thống nhất Actor và ranh giới hệ thống trước khi chia phần việc cá nhân. Cuối tuần bàn giao sơ đồ tổng thể, sơ đồ chi tiết và tám đặc tả thống nhất, đủ làm đầu vào cho giai đoạn sau.

## 2. Actor và ranh giới hệ thống

| Actor | Vai trò |
|---|---|
| Khách hàng (Customer) | Gửi, bổ sung và theo dõi yêu cầu; đánh giá sau hỗ trợ. |
| Nhân viên hỗ trợ (Agent) | Xem, phản hồi, cập nhật trạng thái và giải quyết ticket. |
| Trưởng nhóm (Supervisor) | Phân công, theo dõi SLA, giám sát và xử lý chuyển cấp. |
| Quản trị viên (Admin) | Quản trị người dùng, vai trò và cấu hình hệ thống. |
| Hệ thống kênh bên ngoài | Email/social gửi sự kiện và tin nhắn vào nền tảng qua adapter/webhook. |

AI/RAG là chức năng nội bộ nếu được xây dựng trong nền tảng, không mặc định là Actor. Kênh ngoài chỉ là Actor khi tương tác qua ranh giới hệ thống.

Các tác vụ tự động như tạo ticket, phân loại và kiểm tra SLA là hành vi nội bộ khởi phát bởi sự kiện/thời gian; không vẽ chính hệ thống Helpdesk làm Actor. Chỉ xem scheduler là Actor nếu đó thực sự là hệ thống ngoài.

## 3. Bốn nhóm chức năng và tám Use Case

| Nhóm chức năng | Mã | Use Case | Actor chính |
|---|---|---|---|
| Tiếp nhận và tổng hợp đa kênh | UC01 | Gửi và tiếp nhận yêu cầu hỗ trợ | Khách hàng; Hệ thống kênh bên ngoài |
| Tiếp nhận và tổng hợp đa kênh | UC02 | Tạo và tổng hợp ticket | Khách hàng; Hệ thống kênh bên ngoài |
| Quản lý và điều phối ticket | UC03 | Xử lý và cập nhật trạng thái ticket | Agent |
| Quản lý và điều phối ticket | UC04 | AI phân loại và định tuyến ticket | Supervisor (xử lý ngoại lệ); tác vụ tự động bắt đầu khi ticket được tiếp nhận |
| Quản lý và điều phối ticket | UC05 | Theo dõi SLA, cảnh báo và chuyển cấp | Supervisor; scheduler nội bộ là cơ chế thời gian |
| Quản lý Knowledge Base | UC06 | Quản lý bài viết Knowledge Base | Agent (soạn); Supervisor (duyệt/xuất bản) |
| Quản lý Knowledge Base | UC07 | AI RAG gợi ý câu trả lời có dẫn nguồn | Agent |
| Báo cáo và thống kê | UC08 | Xem báo cáo ticket, SLA và CSAT | Supervisor; Admin |

> Bốn nhóm chức năng chỉ dùng để sắp xếp phạm vi, không mặc định là quan hệ `«include»`/`«extend»`.

## 4. Phân công năm thành viên

| Thành viên | Phụ trách | Use Case | Đầu ra cá nhân |
|---|---|---|---|
| TV1 — Điều phối/tổng thể | Chốt Actor, phạm vi, ranh giới; sơ đồ tổng thể; tiếp nhận đa kênh | UC01, UC02 | Mô tả UC01–02; sơ đồ tổng thể bản nháp; danh sách Actor và biên bản thống nhất phạm vi |
| TV2 — Ticket Management | Quy trình Agent, trạng thái và ngoại lệ | UC03 | Đặc tả UC03; mô hình vòng đời ticket |
| TV3 — Routing & SLA | Phân loại AI, định tuyến, thời hạn, cảnh báo | UC04, UC05 | Đặc tả UC04–05; kịch bản không phân loại được, quá hạn và chuyển cấp |
| TV4 — KB & AI RAG | Quản trị KB, gợi ý có nguồn, fallback sang người | UC06, UC07 | Đặc tả UC06–07; luồng RAG và fallback |
| TV5 — Reporting/tổng hợp | Báo cáo ticket, SLA, CSAT; rà soát nhất quán và định dạng | UC08 | [Hồ sơ UC08](docs/TV5-UC08-Bao-cao.md): tuần 1 dùng Actor, sơ đồ Use Case tổng thể/chi tiết, đặc tả và bảng review; phần lớp, tương tác và GUI theo bài 02–03 là tài liệu phát triển tiếp theo |

Khối lượng được cân bằng theo độ phức tạp: UC03 là một Use Case nhưng bao phủ nghiệp vụ lõi; TV1 điều phối và tích hợp; TV5 rà soát chéo toàn bộ tài liệu.

## 5. Lịch làm việc bảy ngày

| Ngày | Công việc | Phụ trách và kết quả |
|---|---|---|
| 1 | Đọc đề; xác định mục tiêu, chức năng bắt buộc và phạm vi MVP | Cả nhóm; TV1 ghi biên bản thống nhất |
| 2 | Chốt Actor, bốn nhóm chức năng và tám Use Case | Cả nhóm; TV1 tổng hợp danh mục |
| 3 | Vẽ và review Use Case Diagram tổng thể | TV1 dựng; bốn thành viên review |
| 4–5 | Phân tích luồng, sơ đồ chi tiết và đặc tả từng UC | Mỗi thành viên theo phân công |
| 6 | Review chéo Actor, điều kiện, luồng chính, ngoại lệ và trùng lặp | Cả nhóm; TV5 tổng hợp lỗi và người sửa |
| 7 | Hoàn thiện bộ tài liệu, thống nhất đầu vào giai đoạn sau | TV1 và TV5 tích hợp; cả nhóm xác nhận phiên bản |

## 6. Quy ước thiết kế và cộng tác

- Mã Use Case thống nhất UC01–UC08; mỗi Use Case mô tả một mục tiêu nghiệp vụ quan sát được.
- Mọi đặc tả dùng cùng biểu mẫu ở mục 8.
- Chỉ dùng `«include»` cho hành vi bắt buộc được tái sử dụng; chỉ dùng `«extend»` cho hành vi mở rộng có điều kiện. Không dùng hai quan hệ này để biểu diễn nhóm chức năng.
- Với xử lý tự động, ghi rõ sự kiện khởi phát và hệ thống thực hiện. Chỉ vẽ scheduler thành Actor nếu nó nằm ngoài ranh giới hệ thống.
- Mỗi đầu việc có một người phụ trách, một người review, đầu ra và tiêu chí chấp nhận. Bảng chung dùng các cột `Backlog → Ready → Doing → Review → Done`.
- TV1 giữ phiên bản tài liệu hợp nhất; TV5 rà soát chéo, lỗi được ghi rõ người sửa và hạn hoàn tất.

## 7. Sản phẩm bàn giao cuối tuần

| STT | Sản phẩm | Tiêu chí hoàn thành |
|---|---|---|
| 1 | Tổng quan yêu cầu và phạm vi | Nêu bài toán, MVP và bốn nhóm chức năng |
| 2 | Danh sách Actor | Vai trò, quyền khái quát và hệ thống kênh ngoài |
| 3 | Danh sách tám Use Case | Mã, tên, Actor chính và mô tả ngắn |
| 4 | Use Case Diagram tổng thể | Có ranh giới hệ thống và tương tác Actor |
| 5 | Use Case Diagram chi tiết | Thể hiện tám UC trong phạm vi tuần 1, quan hệ UML hợp lý |
| 6 | Tám đặc tả Use Case | Cùng biểu mẫu; có luồng chính, luồng thay thế/ngoại lệ |
| 7 | Bản tổng hợp đã review | Không mâu thuẫn Actor, tên, điều kiện, ký hiệu; có phiên bản |

## 8. Biểu mẫu đặc tả Use Case

| Thuộc tính | Nội dung |
|---|---|
| Mã/tên Use Case | Ví dụ: UC03 — Xử lý và cập nhật trạng thái ticket |
| Actor chính/Actor phụ | Vai trò tham gia; hệ thống ngoài nếu có |
| Mục tiêu | Kết quả nghiệp vụ Actor muốn đạt |
| Tiền điều kiện | Điều kiện cần trước khi bắt đầu |
| Sự kiện kích hoạt | Thao tác hoặc sự kiện khởi phát |
| Luồng chính | Các bước thành công theo trình tự |
| Luồng thay thế/ngoại lệ | Dữ liệu thiếu, gửi thất bại, AI không chắc chắn… |
| Hậu điều kiện | Trạng thái và dữ liệu sau khi kết thúc |
| Quy tắc nghiệp vụ | Quyền, SLA, định tuyến và điều kiện liên quan |

## 9. Tiêu chí nghiệm thu

- Tám Use Case bao phủ đủ bốn nhóm chức năng và tính năng AI cơ bản của đề tài.
- Actor, vòng đời ticket, gợi ý AI và chuyển cho Agent nhất quán giữa sơ đồ và đặc tả.
- Sơ đồ đọc được, nối Actor đúng và không lạm dụng `include`/`extend`.
- Đặc tả đủ rõ để làm đầu vào cho ERD, API và kiểm thử ở tuần tiếp theo.
- Cả nhóm xác nhận một bản tài liệu hợp nhất vào ngày 7.

**Kết quả kỳ vọng:** Bộ tài liệu thống nhất về phạm vi, Actor và tám Use Case, đủ để chuyển sang thiết kế hệ thống mà không phải phân tích lại từ đầu.

## 10. Bối cảnh dự án hiện có

Trong workspace đã có một prototype Helpdesk và tài liệu thiết kế rộng hơn. Kế hoạch tuần 1 này mô tả quy trình học tập của nhóm bắt đầu phân tích từ số 0; prototype hiện có chỉ là tài liệu tham khảo để đối chiếu, không thay thế sản phẩm bàn giao cá nhân hoặc xác nhận rằng nhóm đã hoàn thành tuần 1. Nhóm chỉ dùng chi tiết prototype sau khi đã thống nhất yêu cầu và ghi rõ nội dung nào được tái sử dụng.
