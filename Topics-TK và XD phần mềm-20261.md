# Chủ đề Project học phần TK&XD PM

> Học kỳ 20261

CHỦ ĐỀ PROJECT HỌC PHẦN TK&XD PM
HỌC KỲ 20261
## PHẦN I. GIỚI THIỆU CHUNG
1. Mục tiêu học tập
- Vận dụng quy trình kỹ nghệ phần mềm: từ đặc tả yêu cầu, phân tích, thiết kế đến xây dựng, kiểm thử và
triển khai một hệ thống có quy mô nhỏ.
- Thực hành thiết kế chất lượng: kiến trúc phần mềm, thiết kế cơ sở dữ liệu, áp dụng nguyên lý thiết kế
(SOLID, separation of concerns) và mẫu thiết kế (design patterns).
- Tích hợp AI vào sản phẩm phần mềm: xác định bài toán phù hợp, lựa chọn cách tiếp cận, đánh giá và xử
lý giới hạn của thành phần AI trong một hệ thống thực.
- Làm việc nhóm: quản lý dự án, kiểm soát phiên bản, phân công minh bạch và bảo đảm đóng góp cân đối
giữa các thành viên.
2. Hình thức
- Quy mô nhóm: 4 - 5 sinh viên/nhóm; mỗi nhóm nhận 01 chủ đề.
- Tính năng AI được tích hợp: mỗi chủ đề có một tính năng AI cơ bản thuộc nhóm bài toán có độ khó tương
đồng (phân loại/dự đoán/gợi ý/ghép nối bằng NLP hoặc ML). Các tính năng AI nâng cao dùng để tính điểm
thưởng.
- Phần mở rộng (bonus) không bắt buộc: khuyến khích sáng tạo dùng để tính điểm thưởng.
## PHẦN II. YÊU CẦU CHUNG & DELIVERABLES THEO GIAI ĐOẠN
Dự án được tổ chức thành 6 sản phẩm bàn giao (D1–D6) theo tiến trình. Mỗi sản phẩm được nộp đúng hạn ở
mốc tương ứng và là căn cứ đánh giá kết quả điểm quá trình.
D1. Đặc tả yêu cầu & phân tích
- Bản khảo sát hệ thống tương tự: phân tích 2–3 sản phẩm/hệ thống hiện có, rút ra chức năng tham chiếu
và điểm khác biệt của giải pháp nhóm.
- Đặc tả yêu cầu (SRS): danh mục yêu cầu chức năng và phi chức năng, có gán mã và mức ưu tiên (MoSCoW).
- Mô hình use case: sơ đồ use case tổng thể + đặc tả chi tiết (luồng chính/luồng phụ/ngoại lệ), trong đó có
use case liên quan tính năng AI.
- Backlog sản phẩm: danh sách user story kèm tiêu chí chấp nhận (acceptance criteria) trên công cụ quản lý
(File Excel/Jira/Trello/GitHub Projects/…).
D2. Thiết kế hệ thống
- Kiến trúc phần mềm: sơ đồ kiến trúc, lựa chọn kiểu kiến trúc (layered / MVC / client-server /
microservices…) và lý giải; vị trí thành phần AI trong kiến trúc.

---

<!-- Trang 2 -->

- Thiết kế cơ sở dữ liệu: ERD, lược đồ quan hệ đã chuẩn hoá (tối thiểu 3NF hoặc giải trình khi khử chuẩn
hoá có chủ đích).
- Thiết kế chi tiết: package diagram; class diagram; sequence diagram; đặc tả API chi tiết cho backend.
- Mẫu thiết kế & nguyên lý: áp dụng và giải thích các design patterns phù hợp; nêu rõ nguyên lý SOLID được
tuân thủ ở đâu.
- Thiết kế giao diện: wireframe/mockup các màn hình chính và sơ đồ luồng người dùng.
D3. Xây dựng phần mềm
- Sản phẩm chạy được: hiện thực đầy đủ phạm vi MVP của chủ đề, chạy thông các chức năng.
- Tính năng AI tích hợp: triển khai tính năng AI cơ bản, hoạt động trong luồng nghiệp vụ (không phải màn
hình demo tách rời).
- Kiểm soát phiên bản: một repository Git; lịch sử commit rõ ràng, nhánh theo tính năng, quy ước commit;
đóng góp phân bố ở mức chấp nhận được.
- Chất lượng mã & bảo mật cơ bản: tuân thủ coding convention; xác thực đăng nhập và phân quyền theo
vai trò; kiểm tra dữ liệu đầu vào; không lộ khoá bí mật/API key trong mã nguồn.
D4. Kiểm thử
- Kế hoạch & test case: bộ test case cho các chức năng chính, gồm ca hợp lệ, biên và ngoại lệ.
- Kiểm thử tự động: unit test cho các module logic cốt lõi và cho phần xử lý/hậu xử lý kết quả AI (ví dụ: kiểm
tra fallback khi AI lỗi).
- Kiểm thử tích hợp: kiểm thử tối thiểu 2 luồng nghiệp vụ đầu-cuối; ghi nhận lỗi và cách khắc phục.
D5. Triển khai & tài liệu vận hành
- Hướng dẫn cài đặt/chạy: README đầy đủ; tệp cấu hình mẫu; hướng dẫn nạp dữ liệu mẫu (seed).
- Tài liệu người dùng ngắn gọn: mô tả các chức năng chính và cách sử dụng tính năng AI.
- Triển khai (khuyến khích - bonus): đưa hệ thống lên môi trường demo (cloud/PaaS/container) kèm liên
kết truy cập.
D6. Báo cáo cuối kỳ & thuyết trình/demo
- Báo cáo tổng hợp: tổng hợp D1-D5, phần "AI Feature Report", đánh giá kết quả và bài học.
- Demo trực tiếp: trình diễn các luồng cốt lõi và tính năng AI; sẵn sàng cho tình huống AI trả kết quả sai/không
khả dụng.
- Báo cáo đóng góp cá nhân: mỗi thành viên tự đánh giá và đối chiếu với dữ liệu Git/bảng công việc.
Yêu cầu phi chức năng chung (mọi chủ đề)
- Bảo mật & quyền riêng tư: băm mật khẩu, phân quyền theo vai trò, tối thiểu hoá dữ liệu cá nhân thu thập.
- Khả dụng & trải nghiệm: giao diện đáp ứng (responsive), thông báo lỗi rõ ràng cho người dùng.
- Khả năng chịu lỗi của AI: hệ thống phải hoạt động ở chế độ suy giảm hợp lý khi thành phần AI lỗi/hết hạn
mức.

---

<!-- Trang 3 -->

- Minh bạch đóng góp: sử dụng Git + bảng công việc để làm dữ liệu đánh giá cá nhân.
## PHẦN III. YÊU CẦU VỀ TÍCH HỢP AI
Mỗi nhóm phải tích hợp tối thiểu 01 tính năng AI cơ bản tạo ra giá trị nghiệp vụ. Tính năng AI phải nằm trong
một luồng nghiệp vụ, có đầu vào/đầu ra rõ ràng và được đánh giá. Không chấp nhận "AI trang trí" (ví dụ: gọi
một API rồi hiển thị thô, không gắn với nghiệp vụ, không đánh giá, không xử lý lỗi).
Các cách tiếp cận được chấp nhận
- Dùng mô hình/dịch vụ có sẵn: gọi API mô hình ngôn ngữ lớn (LLM), thị giác máy tính, embeddings, OCR…
và thiết kế prompt/luồng xử lý phù hợp.
- Tự huấn luyện mô hình ML đơn giản: phân loại, hồi quy, gợi ý, phát hiện bất thường trên dữ liệu của hệ
thống (thật hoặc mô phỏng).
- Kết hợp (hybrid): luật nghiệp vụ + ML/LLM; ưu tiên phương án đơn giản, kiểm soát được.
Yêu cầu cụ thể
- Xác định bài toán: phát biểu rõ đầu vào, đầu ra, và giá trị mang lại cho người dùng.
- Dữ liệu: nêu nguồn dữ liệu, cách thu thập/sinh dữ liệu mẫu, và cách xử lý dữ liệu thiếu/nhiễu.
- Đánh giá: nêu chỉ số đánh giá phù hợp (ví dụ độ chính xác/precision–recall cho phân loại, đánh giá định
tính có ví dụ cho LLM) và kết quả thực nghiệm.
- Xử lý thất bại & fallback: hành vi của hệ thống khi AI trả kết quả sai, độ trễ cao hoặc không khả dụng.
- Đạo đức & quyền riêng tư: rủi ro sai lệch (bias), phạm vi dữ liệu cá nhân, và các cảnh báo/khuyến nghị
(đặc biệt với tư vấn sức khoẻ).
- Con người trong vòng lặp (human-in-the-loop): với quyết định nhạy cảm, kết quả AI chỉ mang tính hỗ trợ,
có bước xác nhận của người dùng/quản trị.
- Chi phí & độ trễ: cân nhắc hạn mức lời gọi, caching, và trải nghiệm khi phản hồi chậm.
Sản phẩm bàn giao AI: một "AI Feature Report" khoảng (1-3 trang) trình bày đủ các mục trên, nội dung này kèm
trong báo cáo D6.
## PHẦN IV. YÊU CẦU CỤ THỂ THEO TỪNG CHỦ ĐỀ
## Chủ đề 01. Hệ thống Quản lý Nhóm Thực hiện Dự án hoặc Bài tập lớn trong Môn học
1. Mô tả
Nhiều sinh viên phải giải quyết các bài tập lớn hoặc dự án thực hiện theo nhóm trong quá trình học tập.
Tuy nhiên, thực tế cho thấy sự chênh lệch trong mức độ đóng góp giữa các thành viên có thể ảnh hưởng đến
hiệu quả làm việc nhóm. Và những trải nghiệm học tập có thể trở nên kém hấp dẫn hơn do có sự xuất hiện của
"những thành viên tự do", tức là những thành viên cố gắng giảm thiểu nỗ lực của mình bằng cách dựa vào công
việc của người khác.

---

<!-- Trang 4 -->

Đối với người hướng dẫn, việc đánh giá sự đóng góp của từng thành viên trong nhóm là một công việc
tốn nhiều thời gian và dễ mắc sai sót. Do nhiều dự án phát triển phần mềm dựa vào việc sử dụng các công cụ
kiểm soát phiên bản (ví dụ nền tảng Git) nên việc phân tích nhật ký của các công cụ này là một cách phổ biến
để phân tích hoạt động của những người đóng góp. Tuy nhiên, việc sử dụng số lần commits và/hoặc số dòng
mã (LOC) có thể chưa cung cấp đầy đủ cho việc đánh giá.
Do đó, hệ thống quản lý nhóm thực hiện dự án được đề xuất nhằm hỗ trợ việc tổ chức, theo dõi và
đánh giá hiệu quả làm việc nhóm một cách minh bạch và chính xác. Hệ thống quản lý nhóm thực hiện dự án
không chỉ giúp nâng cao hiệu suất làm việc nhóm mà còn hỗ trợ giảng viên trong việc đánh giá công bằng sự
đóng góp của từng thành viên. Bằng cách tích hợp các công cụ theo dõi hoạt động, phân tích dữ liệu, và hỗ trợ
đánh giá định lượng, hệ thống này giúp tạo ra một môi trường học tập minh bạch và hiệu quả hơn.
2. Các đối tượng sử dụng hệ thống
- Sinh viên: Tham gia vào các nhóm dự án, thực hiện công việc theo phân công, cập nhật tiến độ và ghi
nhận đóng góp.
- Nhóm trưởng: Điều phối công việc trong nhóm, phân chia nhiệm vụ, theo dõi tiến độ của các thành
viên, và báo cáo tình hình thực hiện.
- Người hướng dẫn (giảng viên, trợ giảng): Tạo lập dự án, theo dõi hoạt động của các nhóm, đánh giá
mức độ tham gia của từng thành viên, và đưa ra phản hồi.
- Quản trị viên hệ thống: Quản lý tài khoản người dùng, bảo trì hệ thống, hỗ trợ xử lý các sự cố kỹ thuật.
3. Một số chức năng chính
(sinh viên có thể khảo sát thêm các hệ thống hoặc sản phẩm tương tự nhằm đề xuất thêm các chức năng bổ
sung nếu có)
a. Quản lý nhóm dự án
- Sinh viên có thể đăng ký nhóm hoặc hệ thống tự động gán nhóm dựa trên tiêu chí do người hướng dẫn
thiết lập.
- Nhóm trưởng có thể phân công công việc, cập nhật trạng thái của từng nhiệm vụ.
- Các thành viên có thể báo cáo tiến độ công việc, đề xuất thay đổi, và trao đổi qua diễn đàn hoặc công
cụ chat nội bộ.
b. Theo dõi và ghi nhận đóng góp của từng thành viên
- Hệ thống tích hợp với các nền tảng quản lý mã nguồn như Git để theo dõi lịch sử làm việc, commits, số
dòng mã (LOC), số lần đóng góp,… vào tài liệu dự án.
- Ghi nhận thời gian làm việc thông qua công cụ theo dõi hoạt động như nhật ký công việc, bảng công việc
(Kanban, Scrum).
- Đánh giá chất lượng đóng góp dựa trên các tiêu chí như số lượng task hoàn thành, mức độ phức tạp của
nhiệm vụ.
c. Hệ thống đánh giá công bằng
- Cung cấp chức năng đánh giá chéo giữa các thành viên trong nhóm để phản ánh mức độ hợp tác.

---

<!-- Trang 5 -->

- Giảng viên có thể xem báo cáo chi tiết về từng thành viên, bao gồm số lượng commits, tần suất làm việc,
và phản hồi từ đồng đội.
- Tích hợp phân tích dữ liệu để phát hiện các thành viên có mức đóng góp không cân đối.
4. Một số quy trình nghiệp vụ
a. Tạo lập và quản lý dự án
- Người hướng dẫn khởi tạo một dự án mới, xác định số lượng thành viên trong mỗi nhóm, và đặt tiêu chí
đánh giá.
- Nhóm trưởng phân chia công việc và đặt ra các deadlines cụ thể cho từng nhiệm vụ.
- Các thành viên truy cập vào hệ thống để nhận nhiệm vụ, cập nhật trạng thái, và tải lên tài liệu liên quan.
b. Giám sát tiến độ và đánh giá đóng góp
- Hệ thống theo dõi lịch sử làm việc của mỗi thành viên dựa trên dữ liệu từ Git, bảng công việc, hoặc các
công cụ hỗ trợ.
- Người hướng dẫn có thể xem biểu đồ hoạt động của từng nhóm và từng cá nhân để đưa ra đánh giá ban
đầu.
- Thành viên trong nhóm có thể phản hồi về quá trình làm việc của nhau để hỗ trợ đánh giá công bằng.
c. Phát hiện và xử lý thành viên "tự do"
- Hệ thống tự động xác định các sinh viên có mức độ đóng góp thấp dựa trên lịch sử làm việc.
- Nhóm trưởng hoặc giảng viên có thể yêu cầu giải trình hoặc điều chỉnh vai trò của thành viên đó trong
nhóm.
- Hệ thống gợi ý hình thức đánh giá bổ sung (ví dụ: phỏng vấn cá nhân, kiểm tra kiến thức) để đảm bảo
sự công bằng.
5. Tính năng AI cơ bản
- Phân tích đóng góp & phát hiện "thành viên tự do" — Từ dữ liệu Git và bảng công việc, tính điểm đóng góp
tổng hợp (không chỉ đếm commit/LOC) và dùng phát hiện bất thường (anomaly detection) hoặc luật thống kê
để gắn cờ thành viên có mức đóng góp lệch bất thường, kèm giải thích chỉ số. Kết quả chỉ mang tính hỗ trợ, có
bước xác nhận của giảng viên.
Tính năng AI mở rộng (bonus)
- Dùng LLM tóm tắt & đánh giá định tính đóng góp từ nội dung commit/pull request (ví dụ: phân biệt commit
sửa lỗi thực sự với commit hình thức).
- Gợi ý phân công nhiệm vụ theo kỹ năng/lịch sử làm việc của thành viên.
Dữ liệu
Cho phép dùng dữ liệu Git thật của chính nhóm qua GitHub/GitLab API (chế độ chỉ đọc), kết hợp bộ dữ liệu mẫu
do nhóm chuẩn bị để minh hoạ tình huống "free-rider".
Lưu ý khả thi & giới hạn phạm vi

---

<!-- Trang 6 -->

Bài toán đánh giá công bằng mang tính nghiên cứu; giới hạn phạm vi ở một bộ chỉ số minh bạch + cờ cảnh báo,
KHÔNG kỳ vọng "chấm điểm tự động" thay giảng viên. Tích hợp Git nên dùng API chỉ đọc và cache để kiểm soát
công sức.
Gợi ý kỹ thuật
Kiến trúc phân lớp; mẫu Strategy cho các công thức tính điểm đóng góp; Observer cho thông báo tiến độ. Tách
module thu thập dữ liệu Git khỏi module tính điểm.


---

<!-- Trang 7 -->

## Chủ đề 02. Công cụ hỗ trợ review mã nguồn (Pull Request) và sinh dữ liệu kiểm thử
1. Mô tả
Trong quy trình phát triển hiện đại, thay đổi mã nguồn được đưa vào thông qua các *pull request* (PR). Việc
review PR thủ công tốn thời gian, dễ bỏ sót, và khó bảo đảm rằng một thay đổi thực sự đáp ứng đặc tả (spec)
hoặc tiêu chí chấp nhận đã đặt ra đặc biệt với các dự án có tốc độ commit cao với sự tham gia của các Coding
Agent. Nhiều lỗi có thể được phát hiện sớm hơn nếu có công cụ hỗ trợ tự động sinh test case dựa trên diff thay
đổi, tóm tắt nội dung thay đổi để reviewer nắm nhanh, và dự đoán các rủi ro tiềm ẩn trước khi merge. Công cụ
này được xây dựng nhằm hỗ trợ nhóm phát triển tăng tốc quy trình review, đảm bảo chất lượng mã nguồn
trước khi đưa vào pipeline CI/CD. Theo tinh thần *spec-driven development*, mỗi tính năng gắn với một đặc
tả/tiêu chí chấp nhận tường minh.
2. Đối tượng sử dụng hệ thống
- 1. Nhà phát triển (tác giả PR)
- 2. Người review (Reviewer)
- 3. Trưởng nhóm kỹ thuật (Tech Lead)
- 4. Quản trị viên hệ thống
3. Một số chức năng chính
(sinh viên có thể khảo sát thêm các hệ thống hoặc sản phẩm tương tự nhằm đề xuất thêm các chức năng bổ
sung nếu có)
- Quản lý thông tin dự án
- Quản lý thông tin người dùng và quyền hạn
- Quản lý đặc tả & tiêu chí chấp nhận: tạo/lưu trữ spec, liên kết spec với issue và PR.
- Quản lý pull request: kết nối kho mã (GitHub/GitLab) ở chế độ chỉ đọc, lấy diff và metadata, hiển thị thay
đổi, theo dõi trạng thái PR, danh sách reviewer, lịch sử thay đổi
- Phân tích diff và sinh test tự động: phân tích các thay đổi trong PR, đánh dấu vùng thay đổi thiếu kiểm
thử, tự động sinh test case tương ứng
- Hỗ trợ review: tóm tắt thay đổi, gợi ý điểm cần chú ý, đối chiếu với đặc tả, gợi ý bình luận review.
- Báo cáo và thống kê (Bảng điều khiển): thời gian trung bình review, tỉ lệ PR cần sửa lại, chất lượng test
coverage theo thời gian
4. Một số quy trình nghiệp vụ
- Vòng đời PR có hỗ trợ AI: tạo spec → mở PR liên kết spec → hệ thống phân tích diff → sinh tóm tắt + gợi
ý review + test ứng viên → người review duyệt/chỉnh → hợp nhất (merge).
- Đối chiếu tuân thủ đặc tả: so khớp thay đổi với tiêu chí chấp nhận, gắn cờ hạng mục chưa được đáp ứng.
- Sinh & xác nhận kiểm thử: đề xuất test → tác giả xem xét, chỉnh sửa, đưa vào bộ kiểm thử.
5. Tính năng AI cơ bản

---

<!-- Trang 8 -->

Tóm tắt thay đổi + chấm rủi ro PR + gợi ý review/test từ diff & đặc tả - dùng LLM trên (diff + spec) để tạo tóm
tắt, phân loại mức rủi ro và đề xuất test.
Đánh giá: chỉ số phân loại rủi ro + đánh giá định tính có ví dụ.
Tính năng AI mở rộng (bonus): tự gắn nhãn PR theo vùng ảnh hưởng; phát hiện khoảng trống độ phủ kiểm thử.
Dữ liệu: dùng PR/diff từ kho công khai hoặc kho mẫu do nhóm chuẩn bị; bộ dữ liệu PR có nhãn "cần chỉnh
sửa/rủi ro" cho mô hình chấm rủi ro.
Lưu ý khả thi & giới hạn phạm vi
- kết nối qua API kho mã (chỉ đọc), **không tự động merge**; test do AI sinh là **đề xuất** cần con
người xác nhận (tránh để AI viết và chạy mã không kiểm soát).
- Chỉ cần hỗ trợ sinh test cho 1 ngôn ngữ lập trình và 1 framework test cụ thể (ví dụ Python + PyTest, hoặc
Java + JUnit)
Gợi ý kỹ thuật
kiến trúc phân lớp; mẫu Adapter cho các nền tảng Git khác nhau; Strategy cho công thức chấm rủi ro; hàng đợi
cho phân tích bất đồng bộ.


---

<!-- Trang 9 -->

## Chủ đề 03. Hệ thống hỗ trợ sự cố xe trên đường
1. Mô tả
Cứu hộ giao thông là dịch vụ thông dụng trở nên cần thiết và cấp bách. Đây cũng là dịch vụ được nhiều
người lựa chọn khi xe gặp sự cố hư hỏng. Nếu không may phương tiện giao thông của bạn gặp phải trục trặc khi
đang trên đường di chuyển như: Lốp xe bị thủng, xì hơi, nổ lốp cần thay thế mới hay vá lốp bơm hơi hay thay
thế lốp dự phòng; Bình ắc quy của xe có dấu hiệu bị yếu dần đi hoặc hết điện đột ngột khi đang di chuyển khiến
cho xe không thể nổ và khởi động được; Xe chết máy bất chợt hoặc hư hỏng không rõ nguyên nhân; Tai nạn
gặp phải không mong muốn xảy ra; Hoặc chẳng may vì một lý do nào đó mà xe dừng lại do thiếu xăng, chủ xe
sẽ rất khó đẩy xe đến cây xăng gần nhất…. Các tình huống phức tạp cần đến sự hỗ trợ của các dịch vụ cứu hộ
giao thông chuyên nghiệp nhưng cũng có tình huống đơn giản có thể được hướng dẫn hoặc trợ giúp của những
người tham gia giao thông khác.
Hệ thống hỗ trợ sự cố xe trên đường là một nền tảng số giúp kết nối người tham gia giao thông gặp sự
cố với các đơn vị cung cấp dịch vụ cứu hộ giao thông chuyên nghiệp. Hệ thống được thiết kế để tối ưu hóa quy
trình tiếp nhận, xử lý và giải quyết các sự cố xe cộ theo thời gian thực, giúp nâng cao hiệu quả cứu hộ và cải
thiện trải nghiệm người dùng.
2. Đối tượng sử dụng
- Người dùng cá nhân: Những người tham gia giao thông gặp sự cố xe và cần hỗ trợ từ hệ thống.
- Các công ty cung cấp dịch vụ cứu hộ: Các đơn vị kinh doanh dịch vụ cứu hộ, sửa chữa và hỗ trợ xe gặp
sự cố.
- Quản trị viên hệ thống: Người chịu trách nhiệm quản lý tài khoản, đảm bảo tính chính xác và bảo mật
thông tin trên nền tảng.
3. Một số chức năng chính của
(sinh viên có thể khảo sát thêm các hệ thống hoặc sản phẩm tương tự nhằm đề xuất thêm các chức năng bổ
sung nếu có)
3.1. Quản lý thông tin cứu hộ
- Quản lý công ty cứu hộ: Lưu trữ và cập nhật thông tin về các công ty cung cấp dịch vụ cứu hộ, bao gồm
tên, địa chỉ, số điện thoại liên hệ, phạm vi hoạt động và giấy phép hoạt động.
- Quản lý dịch vụ cứu hộ: Danh mục các dịch vụ hỗ trợ như vá lốp, thay lốp, nạp nhiên liệu, kéo xe, sửa
chữa tại chỗ và chi phí cho từng dịch vụ.
- Quản lý phương tiện cứu hộ: Ghi nhận các loại xe cứu hộ và thiết bị hỗ trợ đi kèm.
- Quy trình cứu hộ: Mô tả chi tiết từng bước xử lý khi nhận được yêu cầu cứu hộ, từ tiếp nhận thông tin
đến triển khai hỗ trợ.
3.2. Quản lý yêu cầu cứu hộ

---

<!-- Trang 10 -->

- Gửi yêu cầu cứu hộ: Người dùng có thể tạo yêu cầu cứu hộ bằng cách nhập thông tin về sự cố, vị trí hiện
tại, mô tả tình trạng xe và lựa chọn loại dịch vụ cần hỗ trợ.
- Tìm kiếm dịch vụ cứu hộ gần nhất: Hệ thống cung cấp danh sách các công ty cứu hộ theo vị trí, kèm
theo đánh giá của người dùng trước đó.
- Xác nhận và theo dõi yêu cầu cứu hộ: Người dùng có thể theo dõi trạng thái yêu cầu cứu hộ theo thời
gian thực và nhận thông báo khi có phản hồi từ công ty cứu hộ.
- Hủy yêu cầu cứu hộ: Nếu người dùng tự khắc phục được sự cố hoặc tìm được phương án khác, họ có
thể hủy yêu cầu trước khi công ty cứu hộ tiếp nhận.
3.3. Tương tác giữa các bên
- Hệ thống nhắn tin: Cung cấp chức năng trò chuyện giữa người gặp sự cố và đơn vị cứu hộ để làm rõ
thông tin và thỏa thuận chi phí.
- Hệ thống phản hồi và đánh giá: Sau khi dịch vụ hoàn tất, người dùng có thể đánh giá chất lượng dịch vụ
và phản hồi để giúp cải thiện trải nghiệm hệ thống.
- Hỗ trợ tư vấn từ cộng đồng: Trong các sự cố đơn giản, người dùng có thể nhận hướng dẫn từ những
người tham gia giao thông khác.
3.4. Quản trị hệ thống
- Quản lý tài khoản người dùng: Duy trì thông tin người dùng, xác thực danh tính và hỗ trợ các vấn đề
liên quan đến đăng nhập, bảo mật.
- Quản lý tài khoản công ty cứu hộ: Xác minh danh tính các đơn vị cứu hộ, kiểm tra tính hợp lệ của giấy
phép kinh doanh.
- Kiểm duyệt nội dung: Đảm bảo các thông tin đăng tải trên hệ thống (bao gồm đánh giá, phản hồi) không
vi phạm quy định.
- Báo cáo và thống kê: Cung cấp các báo cáo về số lượng yêu cầu cứu hộ, tần suất sử dụng dịch vụ, tỷ lệ
phản hồi, mức độ hài lòng của khách hàng.
4. Một số quy trình nghiệp vụ
4.1. Quy trình gửi yêu cầu cứu hộ
1. Người dùng đăng nhập vào hệ thống.
2. Chọn danh mục dịch vụ cứu hộ phù hợp với tình trạng xe.
3. Nhập thông tin về sự cố, vị trí GPS, hình ảnh mô tả (nếu có).
4. Hệ thống gợi ý danh sách đơn vị cứu hộ gần nhất kèm theo giá dịch vụ.
5. Người dùng chọn đơn vị cứu hộ và gửi yêu cầu.
6. Công ty cứu hộ tiếp nhận yêu cầu và phản hồi thời gian đến dự kiến.
7. Người dùng theo dõi trạng thái yêu cầu và cập nhật khi cần.
8. Sau khi hoàn tất cứu hộ, người dùng xác nhận dịch vụ và đánh giá chất lượng.
4.2. Quy trình tiếp nhận và xử lý yêu cầu của công ty cứu hộ

---

<!-- Trang 11 -->

1. Nhận thông báo về yêu cầu cứu hộ.
2. Kiểm tra thông tin sự cố và xác định phương tiện cứu hộ phù hợp.
3. Phản hồi thời gian dự kiến đến hiện trường.
4. Điều phối nhân viên cứu hộ đến hỗ trợ khách hàng.
5. Cập nhật tình trạng xử lý lên hệ thống.
6. Hoàn tất dịch vụ và ghi nhận thông tin thanh toán.
7. Xác nhận hoàn thành và nhận phản hồi từ khách hàng.
Tính năng AI cơ bản
- Phân loại sự cố & ghép nối thông minh — Từ mô tả bằng văn bản (và ảnh nếu có) của người dùng, tự động
phân loại loại sự cố, mức khẩn cấp và đề xuất dịch vụ phù hợp; sau đó ghép nối với đơn vị cứu hộ theo vị trí/khả
năng và ước lượng thời gian đến (ETA). Trình bày độ chính xác phân loại và logic ghép nối.
Tính năng AI mở rộng (bonus)
- Trợ lý hướng dẫn xử lý sự cố đơn giản (LLM) khi chưa cần cứu hộ chuyên nghiệp.
- Ước lượng chi phí dự kiến theo loại sự cố và khoảng cách.
Dữ liệu
Vị trí GPS và bản đồ có thể dùng dịch vụ bản đồ (Maps API) hoặc dữ liệu mô phỏng toạ độ. Chuẩn bị tập mô tả
sự cố có nhãn để huấn luyện/đánh giá bộ phân loại.
Lưu ý khả thi & giới hạn phạm vi
"Thời gian thực" có thể mô phỏng bằng cập nhật trạng thái định kỳ; không bắt buộc theo dõi phương tiện trực
tiếp. Phân loại theo văn bản là bắt buộc, phân loại theo ảnh là bonus để kiểm soát độ khó.
Gợi ý kỹ thuật
Kiến trúc client–server; mẫu State cho vòng đời yêu cầu cứu hộ (mới → đã nhận → đang xử lý → hoàn tất/huỷ);
Strategy cho thuật toán ghép nối/ETA.


---

<!-- Trang 12 -->

## Chủ đề 04. Hệ thống đi chợ tiện lợi
1. Mô tả
Hệ thống đi chợ tiện lợi được thiết kế nhằm hỗ trợ người dùng trong việc lập kế hoạch mua sắm, quản
lý thực phẩm trong tủ lạnh và lên thực đơn hàng ngày. Mục tiêu của hệ thống là giúp người dùng duy trì thói
quen tiêu dùng hiệu quả, giảm thiểu lãng phí thực phẩm và đảm bảo dinh dưỡng hợp lý.
Hệ thống cung cấp các công cụ quản lý danh sách mua sắm, theo dõi hạn sử dụng thực phẩm, đề xuất
món ăn từ nguyên liệu sẵn có, cũng như hỗ trợ xây dựng kế hoạch bữa ăn theo tuần. Ngoài ra, hệ thống cho
phép các thành viên trong gia đình chia sẻ thông tin mua sắm và phân công nhiệm vụ, giúp tối ưu hóa quá trình
đi chợ và sử dụng thực phẩm.
2. Đối tượng sử dụng hệ thống
Hệ thống được thiết kế để phục vụ nhiều nhóm đối tượng khác nhau, bao gồm:
- Người nội trợ: Lên danh sách mua sắm, quản lý thực phẩm và tìm kiếm công thức nấu ăn.
- Thành viên Gia đình: Cho phép các thành viên trong gia đình tạo nhóm và chia sẻ danh sách mua sắm.
- Hệ thống bao gồm một tài khoản quản trị viên:
o Quản lý tài khoản người dùng, bao gồm tạo, chỉnh sửa và xóa tài khoản.
o Quản lý danh mục dữ liệu như loại thực phẩm, đơn vị tính, công thức nấu ăn.
o Kiểm soát các nội dung do người dùng nhập liệu để đảm bảo tính chính xác và hợp lệ.
o Theo dõi và tối ưu hiệu suất hệ thống.
3. Một số chức năng chính
(sinh viên có thể khảo sát thêm các hệ thống hoặc sản phẩm tương tự nhằm đề xuất thêm các chức năng bổ
sung nếu có)
3.1 Quản lý danh sách mua sắm
- Cho phép người dùng tạo danh sách mua sắm theo ngày hoặc tuần.
- Hỗ trợ phân loại danh sách theo danh mục thực phẩm như rau củ, thịt cá, đồ khô, gia vị, v.v.
- Cung cấp tính năng chia sẻ danh sách giữa các thành viên trong nhóm gia đình.
- Cập nhật trạng thái mua sắm theo thời gian thực khi các thành viên hoàn tất việc mua hàng.
3.2 Quản lý thực phẩm trong tủ lạnh
- Nhập thông tin thực phẩm bao gồm tên, số lượng, ngày hết hạn và vị trí lưu trữ.
- Hệ thống gửi thông báo nhắc nhở khi thực phẩm sắp hết hạn (trước 3 ngày).
- Phân loại thực phẩm theo nhóm và đề xuất cách bảo quản tối ưu.
- Cho phép tìm kiếm thực phẩm theo tên hoặc danh mục.
3.3 Lên kế hoạch bữa ăn
- Hỗ trợ tạo thực đơn theo ngày hoặc tuần.

---

<!-- Trang 13 -->

- Đề xuất bữa ăn dựa trên thực phẩm hiện có trong tủ lạnh.
- Cung cấp công thức nấu ăn kèm hướng dẫn chế biến.
- Hỗ trợ lưu trữ công thức yêu thích và đánh dấu món ăn phổ biến.
3.4 Gợi ý món ăn thông minh
- Gợi ý món ăn dựa trên thực phẩm còn lại trong tủ lạnh.
- Cung cấp danh sách nguyên liệu cần bổ sung nếu người dùng muốn thực hiện một công thức nấu ăn cụ
thể.
- Hỗ trợ tìm kiếm món ăn theo nguyên liệu sẵn có.
3.5 Báo cáo và thống kê
- Thống kê thực phẩm đã mua theo thời gian.
- Phân tích xu hướng tiêu thụ thực phẩm trong gia đình.
- Báo cáo số lượng thực phẩm bị lãng phí do hết hạn.
4. Một số quy trình nghiệp vụ
4.1 Quy trình quản lý danh sách mua sắm
1. Người dùng tạo danh sách thực phẩm cần mua theo ngày hoặc tuần.
2. Danh sách được chia sẻ với các thành viên trong nhóm gia đình (nếu có).
3. Khi mua hàng, người dùng cập nhật trạng thái từng mặt hàng trong danh sách.
4. Sau khi mua xong, hệ thống tự động cập nhật thực phẩm vào kho lưu trữ tủ lạnh.
4.2 Quy trình quản lý thực phẩm trong tủ lạnh
1. Người dùng nhập thực phẩm mới vào hệ thống sau khi mua.
2. Hệ thống theo dõi thời gian sử dụng và nhắc nhở khi thực phẩm sắp hết hạn.
3. Khi chế biến món ăn, người dùng cập nhật số lượng thực phẩm đã sử dụng.
4. Hệ thống tự động điều chỉnh tồn kho thực phẩm sau mỗi lần sử dụng.
4.3 Quy trình gợi ý món ăn
1. Người dùng truy cập tính năng gợi ý món ăn.
2. Hệ thống kiểm tra thực phẩm hiện có trong tủ lạnh.
3. Hệ thống đề xuất các món ăn phù hợp và hiển thị công thức nấu ăn.
4. Người dùng có thể chọn món ăn và cập nhật số lượng thực phẩm đã sử dụng.
4.4 Quy trình lên kế hoạch bữa ăn
1. Người dùng chọn chế độ lên kế hoạch theo ngày hoặc tuần.
2. Hệ thống đề xuất thực đơn dựa trên thực phẩm sẵn có.
3. Người dùng xác nhận hoặc tùy chỉnh thực đơn theo sở thích.
4. Hệ thống tự động tạo danh sách mua sắm nếu có nguyên liệu thiếu hụt.

---

<!-- Trang 14 -->

Tính năng AI cơ bản
- Gợi ý món ăn thông minh từ nguyên liệu sẵn có — Dựa trên thực phẩm còn trong tủ lạnh và ràng buộc của
người dùng (khẩu vị, dị ứng, dinh dưỡng), hệ thống đề xuất món ăn và công thức, đồng thời liệt kê nguyên liệu
cần bổ sung. Đánh giá định tính chất lượng gợi ý bằng tập ví dụ và tiêu chí (khả thi, phù hợp ràng buộc, giảm
lãng phí).
Tính năng AI mở rộng (bonus)
- Nhận diện hoá đơn/ảnh thực phẩm (OCR/vision) để tự thêm mục vào kho.
- Dự đoán mức tiêu thụ để nhắc mua sớm và giảm lãng phí thực phẩm.
Dữ liệu
Có thể dùng LLM để sinh gợi ý công thức; cần chuẩn hoá danh mục thực phẩm/đơn vị tính. Nếu làm bonus dinh
dưỡng, nêu rõ nguồn dữ liệu dinh dưỡng tham chiếu.
Lưu ý khả thi & giới hạn phạm vi
Gợi ý bằng LLM khả thi cao và là trọng tâm; OCR/vision để ở mức bonus. Cần kiểm soát để gợi ý bám sát nguyên
liệu thực có (tránh "bịa" nguyên liệu), đây là điểm đánh giá về xử lý đầu ra AI.
Gợi ý kỹ thuật
Kiến trúc phân lớp; Repository cho kho thực phẩm; Facade bao bọc dịch vụ gợi ý AI; hàng đợi/nhắc lịch cho cảnh
báo hạn dùng.


---

<!-- Trang 15 -->

## Chủ đề 05. Hệ thống bảo đảm toàn vẹn Chuỗi Cung ứng Phần mềm
1. Mô tả
Một phần mềm hiện đại thường được xây dựng từ hàng trăm thư viện phụ thuộc (dependency) đến từ
nhiều nguồn khác nhau, tạo thành một chuỗi cung ứng phần mềm (software supply chain) phức tạp. Các cuộc
tấn công chuỗi cung ứng (như chèn mã độc vào thư viện phổ biến) ngày càng gia tăng, gây rủi ro lớn cho hệ
thống. Nền tảng Bảo đảm Toàn vẹn Chuỗi Cung ứng được xây dựng nhằm hỗ trợ tổ chức quản lý danh sách
thành phần phần mềm (SBOM - Software Bill of Materials), xác minh nguồn gốc và tính toàn vẹn của các artifact
(attestation), và kiểm soát việc triển khai (admission control) dựa trên chính sách bảo mật đã định nghĩa (policy
as code).
2. Đối tượng sử dụng hệ thống
Hệ thống được xây dựng để phục vụ các nhóm đối tượng sau:
- Lập trình viên: xem rủi ro phụ thuộc, nhận khuyến nghị nâng cấp.
- Kỹ sư bảo mật/chuỗi cung ứng: quản lý SBOM, chính sách, cảnh báo CVE mới.
- Kỹ sư nền tảng/phát hành: kiểm chứng provenance khi triển khai.
- Quản trị viên: quản lý tài khoản, chính sách, phân quyền.
3. Một số chức năng chính
(sinh viên có thể khảo sát thêm các hệ thống hoặc sản phẩm tương tự nhằm đề xuất thêm các chức năng bổ
sung nếu có)
- Quản lý thông tin dự án và quy trình
- Quản lý tài khoản và phân quyền
- Sinh & quản lý SBOM: nhập từ công cụ (Syft/CycloneDX/SPDX); kho thành phần & phiên bản.
- Đồ thị phụ thuộc & lỗ hổng: ánh xạ thành phần ↔ advisory (ví dụ nguồn OSV).
- Provenance & attestation: kiểm chứng chữ ký/nguồn gốc artifact (Sigstore/cosign); đánh giá mức SLSA.
- Policy-as-code & admission control: chặn artifact/phụ thuộc vi phạm chính sách.
- Cảnh báo & báo cáo rủi ro chuỗi cung ứng: theo dõi khi có CVE mới ảnh hưởng thành phần đã dùng.
- Báo cáo và thống kê
Tính năng AI cơ bản
- Chấm điểm & ưu tiên rủi ro thành phần + đối sánh ngữ nghĩa lỗ hổng↔thành phần & tóm tắt advisory — xếp
hạng thành phần cần nâng cấp trước (rủi ro = mức nghiêm trọng × mức sử dụng/khả năng tiếp cận × tín hiệu
khai thác); dùng embeddings/NLP để khớp advisory dạng văn bản với thành phần và tóm tắt tác động/khắc
phục. Đánh giá: chất lượng xếp hạng; độ chính xác đối sánh.
Tính năng AI mở rộng (bonus)
- dự đoán "blast radius" khi nâng cấp; phát hiện gói đáng ngờ (typosquatting) qua đặc trưng.

---

<!-- Trang 16 -->

Dữ liệu
dùng dữ liệu advisory (ví dụ OSV, có thể là bản chụp mẫu); SBOM sinh từ dự án mẫu bằng Syft; attestation mẫu
tạo bằng cosign.
Lưu ý khả thi & giới hạn phạm vi
tích hợp công cụ có sẵn (Syft/Grype/cosign/OSV) thay vì tự xây; policy engine có thể dùng OPA/Rego hoặc một
DSL luật đơn giản. Phần cơ bản nên là *nền tảng quản lý + đồ thị + chính sách + AI ưu tiên*.
Gợi ý kỹ thuật
mô hình/DB đồ thị; Adapter cho các định dạng SBOM; policy engine kiểu OPA; dịch vụ embeddings.


---

<!-- Trang 17 -->

## Chủ đề 06. Hệ thống Sợi dây gắn kết
1. Mô tả
Hoạt động thiện nguyện đóng vai trò quan trọng trong việc hỗ trợ cộng đồng và giúp đỡ những người
có hoàn cảnh khó khăn. Tuy nhiên, việc kết nối giữa các tình nguyện viên, tổ chức thiện nguyện và những cá
nhân hoặc tổ chức cần trợ giúp vẫn còn nhiều hạn chế do thiếu một nền tảng tập trung, tối ưu hóa quy trình
quản lý và điều phối nguồn lực. Hệ thống "Sợi dây gắn kết" ra đời với mục tiêu xây dựng một nền tảng trực
tuyến giúp kết nối hiệu quả giữa các bên, đảm bảo sự phân bổ nguồn lực thiện nguyện hợp lý và tối ưu.
Hệ thống hoạt động dựa trên nguyên tắc kết nối đúng người, đúng thời điểm, đúng nhu cầu, thông qua
việc ghi nhận thông tin về kỹ năng, khả năng hỗ trợ và quỹ thời gian của tình nguyện viên, đồng thời quản lý các
nhu cầu cần trợ giúp một cách minh bạch và chính xác. Bên cạnh đó, hệ thống còn hỗ trợ quản lý sự kiện thiện
nguyện, theo dõi kết quả hoạt động, cung cấp báo cáo thống kê nhằm nâng cao hiệu quả tổ chức và triển khai
các chương trình cộng đồng.
2. Đối tượng sử dụng hệ thống
Hệ thống "Sợi dây gắn kết" được xây dựng để phục vụ ba nhóm đối tượng chính:
- Tình nguyện viên cá nhân: Những người mong muốn tham gia hoạt động thiện nguyện, cung cấp thời
gian, kỹ năng, kiến thức để hỗ trợ cộng đồng.
- Hội/nhóm thiện nguyện: Các tổ chức từ thiện, câu lạc bộ sinh viên, doanh nghiệp xã hội có nhu cầu
quản lý và điều phối tình nguyện viên cho các sự kiện và dự án thiện nguyện.
- Người cần trợ giúp: Cá nhân hoặc tổ chức cần hỗ trợ trong các lĩnh vực như giáo dục, y tế, cứu trợ,
chăm sóc người già, trẻ em, v.v.
- Quản trị viên hệ thống: Người chịu trách nhiệm giám sát hoạt động của hệ thống, quản lý tài khoản,
kiểm soát nội dung và đảm bảo tính minh bạch trong việc kết nối thiện nguyện.
3. Một số chức năng chính
(sinh viên có thể khảo sát thêm các hệ thống hoặc sản phẩm tương tự nhằm đề xuất thêm các chức năng bổ
sung nếu có)
3.1. Quản lý thông tin hội thiện nguyện
Hệ thống cho phép các hội thiện nguyện đăng ký tài khoản và cung cấp các thông tin chi tiết bao gồm:
- Tên tổ chức, lĩnh vực hoạt động, mục tiêu hoạt động.
- Thông tin liên hệ, người đại diện.
- Lịch sử hoạt động, dự án đã thực hiện.
- Thông tin tài trợ hoặc các quỹ hỗ trợ (nếu có).
3.2. Tuyển dụng và quản lý tình nguyện viên
Hệ thống hỗ trợ quy trình đăng ký và quản lý tình nguyện viên theo các bước:

---

<!-- Trang 18 -->

1. Đăng ký tài khoản cá nhân: Cung cấp thông tin cá nhân, liên hệ.
2. Xác định khả năng hỗ trợ: Tình nguyện viên lựa chọn các kỹ năng có thể đóng góp như giảng dạy, y tế,
cứu trợ, công tác xã hội, truyền thông, tổ chức sự kiện, v.v.
3. Ghi nhận quỹ thời gian: Xác định mức độ sẵn sàng tham gia theo tuần/tháng.
4. Đánh giá và xếp hạng: Sau mỗi hoạt động, tình nguyện viên có thể nhận đánh giá từ tổ chức để tăng uy
tín trong hệ thống.
3.3. Quản lý sự kiện thiện nguyện
Hệ thống hỗ trợ hội thiện nguyện trong việc tổ chức và quản lý các sự kiện:
- Tạo mới và quản lý thông tin sự kiện: tên sự kiện, địa điểm, thời gian, số lượng tình nguyện viên cần
thiết.
- Quản lý danh sách đăng ký, kiểm tra sự tham gia.
- Thống kê kết quả sau sự kiện, báo cáo tác động cộng đồng.
3.4. Quản lý các yêu cầu trợ giúp
Người cần trợ giúp có thể đăng ký tài khoản và gửi yêu cầu hỗ trợ với các thông tin chi tiết như:
- Loại hình hỗ trợ cần thiết (nhu yếu phẩm, y tế, giáo dục, tài chính, v.v.).
- Thời gian mong muốn nhận được trợ giúp.
- Mức độ ưu tiên của yêu cầu.
3.5. Kết nối tình nguyện viên/hội thiện nguyện với nhu cầu trợ giúp
Hệ thống sử dụng thuật toán để tối ưu hóa việc ghép nối giữa tình nguyện viên và người cần trợ giúp, dựa trên
các tiêu chí:
- Khả năng phù hợp của tình nguyện viên với yêu cầu.
- Quỹ thời gian sẵn có.
- Mức độ ưu tiên của yêu cầu.
- Khoảng cách địa lý (nếu có liên quan).
3.6. Báo cáo và thống kê
Hệ thống cung cấp báo cáo theo từng nhóm đối tượng:
- Cho hội thiện nguyện: Thống kê số lượng tình nguyện viên tham gia, kết quả sự kiện, hiệu quả hoạt
động.
- Cho tình nguyện viên: Tổng hợp các hoạt động đã tham gia, số giờ thiện nguyện đã đóng góp.
- Cho người cần trợ giúp: Theo dõi tiến trình hỗ trợ, xác nhận kết quả nhận trợ giúp.
- Cho quản trị viên: Báo cáo tổng hợp về các hoạt động trên toàn hệ thống, đảm bảo tính minh bạch.
4. Một số quy trình nghiệp vụ tiêu biểu
4.1. Quy trình đăng ký và quản lý tình nguyện viên
1. Tình nguyện viên truy cập hệ thống và tạo tài khoản cá nhân.
2. Cập nhật thông tin cá nhân, kỹ năng và thời gian sẵn có.

---

<!-- Trang 19 -->

3. Hệ thống xác thực tài khoản qua email/SMS.
4. Khi có sự kiện hoặc yêu cầu hỗ trợ phù hợp, hệ thống thông báo để tình nguyện viên đăng ký tham gia.
5. Sau khi hoàn thành hoạt động, tình nguyện viên nhận được đánh giá và xếp hạng.
4.2. Quy trình tổ chức sự kiện thiện nguyện
1. Hội thiện nguyện đăng nhập hệ thống và tạo sự kiện mới.
2. Nhập thông tin chi tiết sự kiện và số lượng tình nguyện viên cần thiết.
3. Hệ thống đề xuất danh sách tình nguyện viên phù hợp.
4. Hội thiện nguyện xác nhận danh sách tham gia và gửi thông báo.
5. Sau khi sự kiện kết thúc, hội thiện nguyện cập nhật kết quả, báo cáo tác động.
4.3. Quy trình yêu cầu trợ giúp
1. Người cần trợ giúp tạo tài khoản và gửi yêu cầu.
2. Hệ thống kiểm duyệt yêu cầu và ghép nối với tình nguyện viên phù hợp.
3. Tình nguyện viên hoặc hội thiện nguyện xác nhận nhận nhiệm vụ.
4. Hoạt động hỗ trợ diễn ra, kết quả được ghi nhận.
Tính năng AI cơ bản
- Ghép nối ngữ nghĩa & phân loại ưu tiên — Dùng biểu diễn embeddings để ghép nối ngữ nghĩa giữa mô tả
nhu cầu (văn bản tự do) và kỹ năng tình nguyện viên, kết hợp tiêu chí quỹ thời gian/khoảng cách; đồng thời
phân loại mức ưu tiên/khẩn cấp của yêu cầu từ văn bản. Trình bày cách xếp hạng kết quả ghép nối và đánh giá
chất lượng.
Tính năng AI mở rộng (bonus)
- Trợ lý soạn/tóm tắt mô tả nhu cầu và tự động gắn thẻ lĩnh vực.
- Kiểm duyệt nội dung tự động (phát hiện nội dung không phù hợp) trước khi đăng.
Dữ liệu
Chuẩn bị tập mô tả nhu cầu và hồ sơ kỹ năng mẫu (đa dạng lĩnh vực) để thử nghiệm ghép nối; nhãn mức ưu
tiên cho phần phân loại.
Lưu ý khả thi & giới hạn phạm vi
Trọng tâm là chất lượng ghép nối; định nghĩa rõ tiêu chí và cách tính điểm phù hợp thay vì để "hộp đen". Kiểm
duyệt nội dung để ở mức bonus.
Gợi ý kỹ thuật
Kiến trúc phân lớp/service; Strategy cho công thức xếp hạng ghép nối; tách dịch vụ embeddings; hàng đợi thông
báo cho kết nối phù hợp.


---

<!-- Trang 20 -->

## Chủ đề 07. Nền tảng Helpdesk đa kênh
1. Mô tả
Các tổ chức hiện đại nhận yêu cầu hỗ trợ từ khách hàng qua nhiều kênh khác nhau: email, live chat,
mạng xã hội (Facebook, Zalo...), gây khó khăn trong việc tổng hợp, phân loại và điều phối xử lý kịp thời theo
cam kết chất lượng dịch vụ (SLA). Nền tảng Helpdesk Đa kênh được xây dựng nhằm tự động thu thập yêu cầu
từ nhiều nguồn vào một hệ thống ticket thống nhất, phân loại và điều phối (routing) đến đúng nhân viên/nhóm
xử lý, đồng thời tận dụng cơ sở tri thức (knowledge base) để hỗ trợ trả lời nhanh và chính xác hơn.
2. Đối tượng sử dụng hệ thống
- 1. Khách hàng/Người dùng gửi yêu cầu
- 2. Nhân viên hỗ trợ (Agent)
- 3. Trưởng nhóm hỗ trợ (Supervisor)
- 4. Quản trị viên hệ thống
3. Một số chức năng chính
(sinh viên có thể khảo sát thêm các hệ thống hoặc sản phẩm tương tự nhằm đề xuất thêm các chức năng bổ
sung nếu có)
- Thu thập & tổng hợp ticket đa kênh — nhận yêu cầu từ email/chat/social vào một luồng ticket duy nhất
- Quản lý và điều phối ticket — phân loại, gán agent/nhóm xử lý, theo dõi trạng thái, theo dõi SLA
- Quản lý cơ sở tri thức (Knowledge Base) — lưu trữ bài viết hướng dẫn, FAQ, liên kết ticket với bài viết
liên quan
- Báo cáo & thống kê — thời gian phản hồi trung bình, tỉ lệ tuân thủ SLA, mức độ hài lòng khách hàng
4. Một số quy trình nghiệp vụ tiêu biểu
- Tiếp nhận & xử lý ticket: nhận từ kênh → tạo ticket → AI phân loại + định tuyến → gợi ý/RAG trả lời →
gán nhân viên xử lý phù hợp
- Nhân viên xử lý ticket, tham khảo knowledge base → phản hồi khách hàng → giải quyết/ đóng ticket khi
hoàn tất → khảo sát hài lòng.
- Theo dõi SLA: cảnh báo và chuyển cấp khi sắp/đã vi phạm SLA, cảnh báo supervisor khi ticket có nguy cơ
trễ hạn.
- Cập nhật cơ sở tri thức: rút bài học từ ticket đã giải quyết.
Tính năng AI cơ bản
- Phân loại & định tuyến ticket + trả lời tự động bằng RAG trên cơ sở tri thức — phân loại + định tuyến;
sinh câu trả lời có dẫn nguồn từ KB (grounded), có “fallback chuyển người” khi độ tin cậy thấp. Đánh giá:
độ chính xác phân loại; chất lượng câu trả lời RAG (định tính, có tiêu chí).

---

<!-- Trang 21 -->

Tính năng AI mở rộng (bonus)
- phân tích cảm xúc & ưu tiên ticket tiêu cực;
- tóm tắt hội thoại dài cho nhân viên.
Dữ liệu
Chuẩn bị tập ticket mẫu (đã gán nhãn danh mục) + bài viết KB; có thể sinh dữ liệu…
Lưu ý khả thi & giới hạn phạm vi
RAG phải bám theo nguồn KB và bàn giao cho người khi không chắc chắn (tránh trả lời "bịa"). Kênh có thể mô
phỏng hoặc dùng một kênh thật.
Gợi ý kỹ thuật
kiến trúc phân lớp; Adapter cho từng kênh; pipeline RAG (embeddings + retriever + LLM); Strategy cho định
tuyến; bộ lập lịch SLA.

