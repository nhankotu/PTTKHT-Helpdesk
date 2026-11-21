# Chủ đề 07 — Nền tảng Helpdesk đa kênh tích hợp AI

> Hồ sơ Phân tích, Thiết kế và Lộ trình Xây dựng Hệ thống — Học phần **Thiết kế & Xây dựng Phần mềm (TK&XD PM)**, học kỳ 20261.

## 1. Tổng quan bài toán & Mục tiêu

Khách hàng gửi yêu cầu hỗ trợ qua nhiều kênh khác nhau (Web Form, Email, Live Chat, Facebook, Zalo). Khi dữ liệu phân tán, đội ngũ hỗ trợ dễ bỏ sót yêu cầu, phản hồi trùng lặp hoặc vi phạm thời hạn cam kết (SLA).

**Nền tảng Helpdesk đa kênh tích hợp AI** hợp nhất toàn bộ yêu cầu về một luồng ticket tập trung, cung cấp các năng lực cốt lõi:
- Tiếp nhận đa kênh theo định dạng chuẩn, chống nhận trùng (`Idempotency-Key`, `inbound_event`) và phát sự kiện bằng **Transactional Outbox**.
- Quản lý vòng đời ticket 7 trạng thái, tách biệt hoàn toàn **phản hồi công khai (`PUBLIC`)** và **ghi chú nội bộ (`INTERNAL`)**, chống sửa đồng thời bằng **Optimistic Locking (`ticket.version`)**.
- **AI phân loại danh mục** kết hợp luật định tuyến minh bạch (**Strategy Pattern**) để tự động phân công Agent ít tải nhất hoặc chuyển hàng chờ Supervisor.
- **Knowledge Base (KB)** quản trị theo phiên bản (`DRAFT → IN_REVIEW → PUBLISHED → ARCHIVED`) và **AI RAG gợi ý câu trả lời có dẫn nguồn** kèm cơ chế **Fallback an toàn sang Agent** khi thiếu căn cứ hoặc AI gặp lỗi.
- Theo dõi **SLA 2 mốc** (`FIRST_RESPONSE`, `RESOLUTION`), hỗ trợ tạm dừng khi chờ khách (`WAITING_CUSTOMER`), cảnh báo mốc 80%, chuyển cấp quá hạn chống trùng và thống kê báo cáo **Ticket – SLA – CSAT**.

---

## 2. Công nghệ & Kiến trúc chốt triển khai

| Thành phần | Công nghệ lựa chọn | Ghi chú kỹ thuật |
|---|---|---|
| **Kiến trúc** | **Modular Monolith** (phân lớp Controller – Service – Repository – Adapter) | Tách 6 phân hệ nghiệp vụ rõ ràng; áp dụng **Adapter**, **Strategy**, **Repository**, **Facade** theo nguyên lý SOLID. |
| **Backend** | **Java 17/21 + Spring Boot 3.x** | Spring Web, Spring Security + JWT/RBAC, Spring Data JPA (Hibernate, `@Version`), Spring Scheduling (SLA Worker & Outbox Worker), Bean Validation, OpenAPI/Swagger. |
| **Frontend** | **React 18+ (TypeScript + Vite)** | React Router, TanStack Query / Axios, Tailwind CSS. Giao diện chính: Cổng khách hàng, Không gian Agent 3 cột, Bảng điều khiển Supervisor/Admin & Báo cáo. |
| **Database** | **MySQL 8.0+ (InnoDB, `utf8mb4_unicode_ci`)** | 34 bảng chuẩn hóa 3NF + 1 View (`v_ticket_sla_summary`), `BIGINT AUTO_INCREMENT`, `DATETIME(6)` UTC (hiển thị `Asia/Ho_Chi_Minh`), Generated Column + `UNIQUE` cho ràng buộc duy nhất có điều kiện, Flyway migration. |
| **AI Service** | **LLM API + RAG Pipeline** | Tách 2 luồng: AI phân loại JSON có cấu trúc (`UC04`) và AI RAG truy xuất `kb_chunk` + kiểm chứng trích dẫn (`UC07`). |
| **Kiểm thử & Triển khai** | **JUnit 5, Mockito, Testcontainers (MySQL), Playwright, Docker Compose** | Bao phủ các ca kiểm thử trọng tâm: chống trùng webhook, cách ly ghi chú nội bộ, xung đột `version`, fallback AI, thu hồi KB, chống trùng cảnh báo SLA. |

---

## 3. Phạm vi chức năng & Phân công nhóm (5 thành viên)

| Thành viên | Phân hệ | Use Case | Tài liệu đặc tả chuẩn hóa |
|---|---|---|---|
| **TV1** | Tiếp nhận đa kênh & Điều phối tổng thể | **UC01** — Gửi và tiếp nhận yêu cầu<br>**UC02** — Chuẩn hóa và tổng hợp ticket | [TV1_Thiet_Ke_UC01_UC02.md](./Thiet_ke/UseCase_Theo_Thanh_Vien/TV1_Thiet_Ke_UC01_UC02.md) |
| **TV2** | Quản lý & Xử lý Ticket | **UC03** — Xử lý, phản hồi, ghi chú nội bộ & cập nhật trạng thái | [TV2_Thiet_Ke_UC03.md](./Thiet_ke/UseCase_Theo_Thanh_Vien/TV2_Thiet_Ke_UC03.md) |
| **TV3** | Phân loại, Định tuyến & SLA | **UC04** — AI phân loại và định tuyến<br>**UC05** — Theo dõi SLA, cảnh báo và chuyển cấp | [TV3_Thiet_Ke_UC04_UC05.md](./Thiet_ke/UseCase_Theo_Thanh_Vien/TV3_Thiet_Ke_UC04_UC05.md) |
| **TV4** | Knowledge Base & AI RAG | **UC06** — Quản lý bài viết KB theo phiên bản<br>**UC07** — AI RAG gợi ý câu trả lời có dẫn nguồn & Fallback | [TV4_Thiet_Ke_UC06_UC07.md](./Thiet_ke/UseCase_Theo_Thanh_Vien/TV4_Thiet_Ke_UC06_UC07.md) |
| **TV5** | Báo cáo & Tổng hợp chất lượng | **UC08** — Báo cáo thống kê Ticket, SLA và CSAT | [TV5_Thiet_Ke_UC08.md](./Thiet_ke/UseCase_Theo_Thanh_Vien/TV5_Thiet_Ke_UC08.md) |

> **Nền tảng dùng chung (`UC00 / FR01`):** Đăng nhập JWT, phân quyền 4 vai trò (`CUSTOMER`, `AGENT`, `SUPERVISOR`, `ADMIN`) và kiểm soát phạm vi dữ liệu theo nhóm hỗ trợ (`support_team`, `team_member`).

---

## 4. Quy ước nghiệp vụ thống nhất toàn hệ thống

- **7 trạng thái Ticket chuẩn:** `NEW` → `ASSIGNED` → `IN_PROGRESS` ↔ `WAITING_CUSTOMER` → `RESOLVED` → `CLOSED` (và `CANCELLED` cho ticket spam/hủy với lý do ≥ 10 ký tự).
- **4 mức độ ưu tiên (`priority`):** `URGENT` (15p/4h), `HIGH` (1h/8h), `NORMAL` (4h/24h), `LOW` (8h/48h) — tính 24/7, cảnh báo ở 80%.
- **Quy tắc SLA:**
  - `FIRST_RESPONSE` chỉ hoàn thành khi phản hồi công khai hữu ích (`is_auto_ack = FALSE`) gửi thành công (`SENT`/`DELIVERED`).
  - `WAITING_CUSTOMER` chỉ tạm dừng (`PAUSED`) mốc `RESOLUTION`, lưu tại `sla_pause`.
  - Mở lại ticket (`RESOLVED → IN_PROGRESS`) giữ nguyên `FIRST_RESPONSE` và lịch sử `breached_at`, chỉ tiếp tục `RESOLUTION` kèm `sla_pause(RESOLVED_UNTIL_REOPEN)`.
  - Ticket `CANCELLED` bị loại khỏi chỉ số "Ticket đang mở" và mẫu số "Tuân thủ SLA" trong báo cáo UC08.

---

## 5. Cấu trúc thư mục hồ sơ

```text
D:\PTTKHT-Helpdesk\
├── README.md                                        # Tổng quan dự án, công nghệ và mục lục
├── De_tai/                                          # Tài liệu đề bài & kế hoạch gốc
│   ├── Topics-TK và XD phần mềm-20261.pdf
│   ├── Phan-tich-thiet-ke-Chu-de-07-Helpdesk.md
│   └── Ke-hoach-tuan-1-Use-Case-Chu-de-07.md
└── Thiet_ke/                                        # Hồ sơ thiết kế hợp nhất (đã xử lý xung đột)
    ├── README.md                                    # Mục lục chi tiết bộ thiết kế
    ├── 01_Thiet_Ke_Use_Case_Tong_Hop.md             # Bảng xử lý xung đột, Actor, vòng đời & UC00–UC08
    ├── 02_Thiet_Ke_Database_Tong_Hop.md             # Thiết kế CSDL MySQL 8.0+ (34 bảng + 1 View, 3NF)
    ├── 03_Huong_Dan_Tich_Hop_AI_OpenAI.md           # Hướng dẫn tích hợp OpenAI API cho UC04 & UC07
    ├── helpdesk_unified_schema_mysql.sql            # Script DDL MySQL 8.0+ InnoDB hoàn chỉnh
    ├── UseCase_Theo_Thanh_Vien/                     # 5 tài liệu Use Case tách theo từng thành viên
    │   ├── TV1_Thiet_Ke_UC01_UC02.md
    │   ├── TV2_Thiet_Ke_UC03.md
    │   ├── TV3_Thiet_Ke_UC04_UC05.md
    │   ├── TV4_Thiet_Ke_UC06_UC07.md
    │   └── TV5_Thiet_Ke_UC08.md
    └── Bieu_do/
        ├── Tong_the/                                # UseCase_Tong_The, Ticket_Lifecycle_Unified, ERD_Tong_The
        ├── UseCase/                                 # Sơ đồ Use Case chi tiết UC01–UC08
        ├── SSD/                                     # Sơ đồ tuần tự hệ thống (SSD) UC01–UC08
        └── UC08_Chi_tiet/                           # Biểu đồ hoạt động, lớp, giao diện UC08
```

---

## 6. Lộ trình triển khai đề xuất (D3 – D6)

1. **Khởi tạo nền tảng (Sprint 1):** Thiết lập dự án Spring Boot + React (Vite/TS) + Docker MySQL 8.0, chạy Flyway từ [helpdesk_unified_schema_mysql.sql](./Thiet_ke/helpdesk_unified_schema_mysql.sql), hoàn thiện Auth/JWT/RBAC (`UC00`).
2. **Luồng cốt lõi (Sprint 2):** Tiếp nhận Web/Email mô phỏng chống trùng (`UC01–02`) → xử lý ticket, hội thoại, ghi chú nội bộ, Optimistic Locking (`UC03`).
3. **Điều phối & SLA (Sprint 3):** AI phân loại + định tuyến cân bằng tải (`UC04`) → bộ lập lịch SLA, tạm dừng/tiếp tục, cảnh báo & chuyển cấp (`UC05`).
4. **KB, AI RAG & Báo cáo (Sprint 4):** Quản trị KB theo phiên bản (`UC06`) → RAG có dẫn nguồn & Fallback (`UC07`) → Dashboard báo cáo Ticket/SLA/CSAT (`UC08`), hoàn thiện kiểm thử tích hợp và đánh giá AI.
