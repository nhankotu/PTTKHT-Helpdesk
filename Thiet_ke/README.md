# HỒ SƠ THIẾT KẾ TỔNG HỢP — ĐỀ TÀI 07: NỀN TẢNG HELPDESK ĐA KÊNH TÍCH HỢP AI

Thư mục này chứa toàn bộ tài liệu thiết kế **Use Case** và **Cơ sở dữ liệu (MySQL 8.0+ InnoDB)** đã được hợp nhất từ 5 thành viên và xử lý toàn bộ xung đột nghiệp vụ/kỹ thuật.

## 1. Tài liệu Thiết kế Tổng hợp (Toàn hệ thống)
- **Thiết kế Use Case tổng hợp & Bảng xử lý xung đột:** [01_Thiet_Ke_Use_Case_Tong_Hop.md](./01_Thiet_Ke_Use_Case_Tong_Hop.md)
- **Thiết kế Cơ sở dữ liệu tổng hợp (MySQL 8.0+):** [02_Thiet_Ke_Database_Tong_Hop.md](./02_Thiet_Ke_Database_Tong_Hop.md)
- **Hướng dẫn tích hợp AI (OpenAI / ChatGPT API):** [03_Huong_Dan_Tich_Hop_AI_OpenAI.md](./03_Huong_Dan_Tich_Hop_AI_OpenAI.md)
- **Script DDL MySQL 8.0+ (34 bảng + 1 View):** [helpdesk_unified_schema_mysql.sql](./helpdesk_unified_schema_mysql.sql)

### Sơ đồ tổng thể
- [UseCase_Tong_The.png](./Bieu_do/Tong_the/UseCase_Tong_The.png) — Sơ đồ Use Case Tổng thể (UC00–UC08, 5 Actor chuẩn)
- [Ticket_Lifecycle_Unified.png](./Bieu_do/Tong_the/Ticket_Lifecycle_Unified.png) — Máy trạng thái Vòng đời Ticket thống nhất (7 trạng thái & SLA)
- [ERD_Tong_The.png](./Bieu_do/Tong_the/ERD_Tong_The.png) — Sơ đồ Tổng quan CSDL Hợp nhất MySQL 8.0+ (6 phân hệ)

## 2. Tài liệu Thiết kế Use Case tách theo từng thành viên
Tất cả 5 tài liệu dưới đây đã đồng bộ 100% về Actor, 7 trạng thái Ticket, 4 mức ưu tiên, quy tắc SLA và tên bảng MySQL 8.0+:

| Thành viên | Phân hệ / Use Case | Tài liệu |
|---|---|---|
| **TV1** | UC01 & UC02 — Tiếp nhận và tổng hợp yêu cầu đa kênh | [TV1_Thiet_Ke_UC01_UC02.md](./UseCase_Theo_Thanh_Vien/TV1_Thiet_Ke_UC01_UC02.md) |
| **TV2** | UC03 — Xử lý và cập nhật trạng thái ticket | [TV2_Thiet_Ke_UC03.md](./UseCase_Theo_Thanh_Vien/TV2_Thiet_Ke_UC03.md) |
| **TV3** | UC04 & UC05 — AI phân loại/định tuyến & Theo dõi SLA | [TV3_Thiet_Ke_UC04_UC05.md](./UseCase_Theo_Thanh_Vien/TV3_Thiet_Ke_UC04_UC05.md) |
| **TV4** | UC06 & UC07 — Knowledge Base & AI RAG có dẫn nguồn | [TV4_Thiet_Ke_UC06_UC07.md](./UseCase_Theo_Thanh_Vien/TV4_Thiet_Ke_UC06_UC07.md) |
| **TV5** | UC08 — Báo cáo thống kê Ticket, SLA và CSAT | [TV5_Thiet_Ke_UC08.md](./UseCase_Theo_Thanh_Vien/TV5_Thiet_Ke_UC08.md) |
