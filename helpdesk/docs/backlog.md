# Backlog tham chiếu theo nhóm 5 người

A-E là vai trò kỹ thuật đề xuất cho giai đoạn triển khai, chưa phải tên thành viên đã xác nhận. Phân công phân tích tuần 1 dùng TV1–TV5 và UC01–UC08 trong [kế hoạch tuần 1](../../Ke-hoach-tuan-1-Use-Case-Chu-de-07.md); không suy ra TV1=A, TV2=B... M1: nền tảng ticket, M2: kênh/định tuyến, M3: KB/AI, M4: SLA/tích hợp. Mã UC nối backlog triển khai với phạm vi phân tích; một UC có thể bao gồm nhiều story.

| Use Case | User story | Chủ trì triển khai | Yêu cầu | Tiêu chí chấp nhận | Mốc |
|---|---|---|---|---|
| Xuyên UC (phân quyền) | Là khách hàng, tôi đăng nhập và chỉ thấy dữ liệu của mình | A | FR01 | Chưa đăng nhập bị 401; đọc ticket người khác bị từ chối | M1 |
| UC01, UC02 | Là khách hàng, tôi gửi yêu cầu và nhận mã theo dõi | B/C | FR02, FR03 | Ticket và tin nhắn được lưu trước khi chạy AI; gửi email cùng mã không tạo trùng | M1/M2 |
| UC03 | Là nhân viên, tôi lọc yêu cầu để chọn việc cần xử lý | B | FR04 | Lọc trạng thái/ưu tiên/từ khóa và phân trang hoạt động trong phạm vi quyền | M1 |
| UC04 | Là trưởng nhóm, tôi muốn AI đề xuất danh mục và nhóm | C/D | FR05, FR06 | Câu hỏi mẫu được phân loại; không đủ căn cứ thì chờ phân loại thủ công; sửa phân công được ghi lịch sử | M2/M3 |
| UC03 | Là Agent, tôi trao đổi và lưu ghi chú riêng | B | FR07, FR08 | Tin công khai tới khách; ghi chú nội bộ không lộ qua API khách hoặc prompt | M1 |
| UC03 | Là nhân viên, tôi chuyển trạng thái và biết ai đã thay đổi | B | FR09 | Chuyển trạng thái hợp lệ; version cũ bị 409; lịch sử có người và thời điểm | M1 |
| UC06 | Là nhân viên, tôi soạn KB và xin trưởng nhóm xuất bản | D/A | FR10 | Agent chỉ sửa nháp của mình; bài nháp/thu hồi không dùng cho truy xuất công khai | M3 |
| UC07 | Là Agent, tôi nhận câu trả lời có nguồn và phương án chuyển người | D/C | FR11 | KB phù hợp có nháp/citation; nguồn sai hoặc lỗi mô hình thì fallback; sinh LLM thật còn chờ nghiệm thu | M3 |
| UC07 | Là Supervisor, tôi cho phép tự trả lời một FAQ đã biết | D/C | FR11 | Chỉ câu khớp chính xác và đúng phiên bản KB đã duyệt được tự gửi; thu hồi/đổi nguồn trước khi gửi sẽ hủy câu chờ | M3 |
| UC03, UC05 | Là Agent, tôi thấy phản hồi bị lỗi và có thể thử lại | C/B | FR07, FR12 | Pending/failed không tới khách hoặc tính SLA; retry dùng cùng message_id; không đóng khi còn phản hồi chưa gửi | M2/M4 |
| UC05 | Là Supervisor, tôi được cảnh báo ticket gần/quá hạn | E | FR12 | Quét định kỳ, không nhân đôi cảnh báo, chính sách cũ không đổi khi tạo bản mới | M4 |
| UC03, UC08 | Là khách hàng, tôi đánh giá sau khi giải quyết | E/B | FR13 | Chỉ chủ ticket đã giải quyết/đóng được đánh giá một lần; kết quả được tổng hợp vào báo cáo CSAT | M4 |
| UC08 | Là Supervisor, tôi xem số liệu hỗ trợ để điều phối | E | FR14 | Chỉ số dựa trên ticket thực trong cohort ngày được chọn; không có dữ liệu trả giá trị trống rõ ràng | M4 |

Các story đã có phần triển khai được đối chiếu bằng `tests/test_workflows.py` và smoke trình duyệt. Bảng này là backlog kỹ thuật tham chiếu, không phải đầu ra tám đặc tả Use Case tuần 1 và không xác nhận nhóm đã duyệt tên người hoặc deadline.
