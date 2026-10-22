**KẾ HOẠCH TUẦN 1**

**PHÂN TÍCH VÀ THIẾT KẾ USE CASE**

Đề tài: Nền tảng Helpdesk đa kênh tích hợp AI  •  Quy mô nhóm: 05 thành viên  •  Giai đoạn: Bắt đầu từ số 0

**1\. Mục tiêu và phạm vi tuần 1**

Tuần đầu tập trung làm rõ yêu cầu của đề bài, nhận diện đối tượng sử dụng và các tương tác chính, sau đó thiết kế Use Case ở mức tổng thể và chi tiết. Chưa triển khai mã nguồn hoặc thiết kế cơ sở dữ liệu trong giai đoạn này.

Giữ đúng 04 nhóm chức năng chính của đề bài; thiết kế 08 Use Case cụ thể để kiểm soát khối lượng.

Thống nhất Actor và ranh giới hệ thống trước khi phân chia phần việc cá nhân.

Hoàn thiện sơ đồ tổng thể, sơ đồ chi tiết và tài liệu đặc tả đủ làm đầu vào cho các tuần tiếp theo.

**2\. Actor của hệ thống**

| Actor | Vai trò |
| :---- | :---- |
| Khách hàng (Customer) | Gửi, bổ sung và theo dõi yêu cầu; đánh giá sau hỗ trợ. |
| Nhân viên hỗ trợ (Agent) | Xem, phản hồi, cập nhật trạng thái và giải quyết ticket. |
| Trưởng nhóm (Supervisor) | Phân công, theo dõi SLA, giám sát và xử lý chuyển cấp. |
| Quản trị viên (Admin) | Quản trị người dùng, vai trò và cấu hình hệ thống. |
| Hệ thống kênh bên ngoài | Email/Social cung cấp sự kiện, tin nhắn cho nền tảng qua adapter/webhook. |

Lưu ý UML: AI/RAG là chức năng nội bộ nếu được xây dựng trong nền tảng; không mặc định vẽ AI như Actor. Các kênh bên ngoài chỉ là Actor khi tương tác qua ranh giới hệ thống.

**3\. Bốn nhóm chức năng và tám Use Case**

| Nhóm chức năng | Mã UC | Use Case cụ thể |
| :---- | :---- | :---- |
| 1\. Tiếp nhận & tổng hợp đa kênh | UC01 | Gửi và tiếp nhận yêu cầu hỗ trợ |
|  | UC02 | Tạo và tổng hợp ticket |
| 2\. Quản lý & điều phối ticket | UC03 | Xử lý và cập nhật trạng thái ticket |
|  | UC04 | AI phân loại và định tuyến ticket |
|  | UC05 | Theo dõi SLA, cảnh báo và chuyển cấp |
| 3\. Quản lý Knowledge Base | UC06 | Quản lý bài viết Knowledge Base |
|  | UC07 | AI RAG gợi ý câu trả lời có dẫn nguồn |
| 4\. Báo cáo & thống kê | UC08 | Xem báo cáo ticket, SLA và CSAT |

Bốn nhóm chức năng là cách tổ chức phạm vi phân tích, không phải quan hệ «include»/«extend» mặc định giữa các Use Case trong UML.

Kế hoạch tuần 1  |  Trang

**ĐỒ ÁN PHẦN MỀM  •  CHỦ ĐỀ 07**

**4\. Phân công 05 thành viên**

| Thành viên | Phụ trách chính | Use Case | Sản phẩm cá nhân |
| :---- | :---- | :---- | :---- |
| TV1 – Điều phối / Tổng thể | Thống nhất Actor, phạm vi; thiết kế Use Case tổng thể và tiếp nhận đa kênh. | UC01, UC02 | Mô tả UC01–02; sơ đồ tổng thể bản nháp; tổng hợp Actor. |
| TV2 – Ticket Management | Phân tích quy trình Agent xử lý ticket, các trạng thái và ngoại lệ. | UC03 | Đặc tả UC03; mô hình vòng đời ticket. |
| TV3 – Routing & SLA | Phân tích phân loại AI, điều phối, thời hạn SLA và cảnh báo. | UC04, UC05 | Đặc tả UC04–05; kịch bản quá hạn và chuyển cấp. |
| TV4 – KB & AI RAG | Quản lý KB; gợi ý câu trả lời có dẫn nguồn, cơ chế chuyển người khi không chắc chắn. | UC06, UC07 | Đặc tả UC06–07; luồng AI/RAG và fallback. |
| TV5 – Reporting / Tổng hợp | Báo cáo, thống kê; kiểm tra sự nhất quán tài liệu và định dạng. | UC08 | Đặc tả UC08; bảng rà soát và bản tài liệu hợp nhất. |

Cân bằng khối lượng: TV1 chịu trách nhiệm phối hợp cả nhóm; TV2 có 01 UC nhưng nghiệp vụ ticket là lõi; TV5 hỗ trợ kiểm tra chéo và tích hợp tài liệu.

**5\. Tiến độ thực hiện trong 07 ngày**

| Thời gian | Công việc | Người phụ trách / Kết quả |
| :---- | :---- | :---- |
| Ngày 1 | Đọc đề, xác định mục tiêu, chức năng bắt buộc và phạm vi MVP. | Cả nhóm → biên bản thống nhất phạm vi. |
| Ngày 2 | Xác định Actor, mô tả 04 nhóm chức năng và chốt 08 Use Case. | Cả nhóm; TV1 tổng hợp danh mục. |
| Ngày 3 | Thiết kế và review Use Case Diagram tổng thể. | TV1 dựng sơ đồ; 04 thành viên review. |
| Ngày 4–5 | Phân tích luồng nghiệp vụ, vẽ sơ đồ Use Case chi tiết và đặc tả từng UC. | Từng thành viên theo phân công. |
| Ngày 6 | Review chéo: Actor, điều kiện, luồng chính, ngoại lệ và sự trùng lặp. | Cả nhóm; TV5 tổng hợp lỗi. |
| Ngày 7 | Hoàn thiện phiên bản bàn giao; thống nhất hướng phân tích tiếp theo. | TV1 \+ TV5 tích hợp; toàn nhóm xác nhận. |

**6\. Quy ước thiết kế và cách làm việc**

Đặt mã UC thống nhất từ UC01 đến UC08; mỗi Use Case thể hiện một mục tiêu/nghiệp vụ có thể quan sát được.

Tài liệu đặc tả sử dụng chung một biểu mẫu: Actor, mục tiêu, tiền điều kiện, kích hoạt, luồng chính, luồng thay thế/ngoại lệ, hậu điều kiện.

Phân biệt «include» (hành vi bắt buộc được tái sử dụng) và «extend» (hành vi mở rộng có điều kiện); không sử dụng chỉ để nhóm chức năng.

Các bước tự động (như AI phân loại hoặc scheduler cảnh báo SLA) phải thể hiện tác nhân/sự kiện kích hoạt rõ ràng.

Kế hoạch tuần 1  |  Trang

**ĐỒ ÁN PHẦN MỀM  •  CHỦ ĐỀ 07**

**7\. Danh mục bàn giao cuối tuần**

| STT | Tài liệu / sản phẩm | Yêu cầu hoàn thành |
| :---- | :---- | :---- |
| 01 | Tổng quan yêu cầu và phạm vi | Mô tả bài toán, phạm vi MVP, 04 nhóm chức năng. |
| 02 | Danh sách Actor | Vai trò, quyền và các hệ thống kênh ngoài. |
| 03 | Danh sách 08 Use Case | Mã, tên, Actor chính, mô tả ngắn. |
| 04 | 01 Use Case Diagram tổng thể | Nêu được tương tác người dùng và ranh giới hệ thống. |
| 05 | 01 Use Case Diagram chi tiết | Thể hiện 08 Use Case chính của phạm vi tuần 1\. |
| 06 | 08 bản đặc tả Use Case cơ bản | Cùng mẫu mô tả; có luồng chính và ngoại lệ. |
| 07 | Bản tổng hợp đã review | Kiểm tra chéo, thống nhất ký hiệu và phiên bản. |

**8\. Biểu mẫu đặc tả Use Case (dùng chung)**

| Thuộc tính | Nội dung cần điền |
| :---- | :---- |
| Mã / Tên Use Case | Ví dụ: UC03 – Xử lý và cập nhật trạng thái ticket |
| Actor chính / Actor phụ | Vai trò thực hiện; bên ngoài tham gia nếu có |
| Mục tiêu | Kết quả nghiệp vụ mà Actor muốn đạt được |
| Tiền điều kiện | Điều kiện cần trước khi bắt đầu Use Case |
| Sự kiện kích hoạt | Thao tác hoặc sự kiện khởi phát |
| Luồng chính | Các bước 1, 2, 3… theo trình tự thành công |
| Luồng thay thế / ngoại lệ | Ví dụ: thông tin thiếu; gửi thất bại; AI không chắc chắn |
| Hậu điều kiện | Trạng thái và dữ liệu được cập nhật sau khi hoàn tất |
| Quy tắc nghiệp vụ | Quyền hạn, điều kiện SLA, logic gán ticket… |

**9\. Tiêu chí nghiệm thu**

Các Use Case phản ánh đầy đủ 04 nhóm chức năng bắt buộc trong đề bài và tính năng AI cơ bản.

Không mâu thuẫn về Actor, trạng thái ticket, quá trình AI gợi ý và quy trình chuyển người.

Các sơ đồ UML dễ đọc, kết nối đúng Actor–Use Case, không lạm dụng quan hệ include/extend.

Các đặc tả có thể dùng để thiết kế ERD, API và test case ở tuần tiếp theo.

**KẾT QUẢ KỲ VỌNG:** 01 bộ tài liệu thống nhất về phạm vi, Actor và 08 Use Case, đủ để nhóm bước sang giai đoạn thiết kế hệ thống mà không phải phân tích lại từ đầu.

