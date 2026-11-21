---
name: backend-spring-boot
description: Phát triển và kiểm thử Backend Java 17/21 + Spring Boot 3.x theo kiến trúc Modular Monolith với MySQL 8.0, Spring Data JPA, Flyway và Testcontainers. Dùng khi viết Controller, Service, Repository, Entity, Migration hoặc Unit/Integration Test.
---

# Backend Spring Boot 3.x & Testing Skill (MySQL 8.0)

*Chuyển thể từ `sivalabs-agent-skills` (spring-boot) và `github/awesome-copilot` (spring-boot-testing, java-junit), tối ưu cho kiến trúc Modular Monolith + MySQL 8.0.*

## 1. Kiến trúc Modular Monolith & Tổ chức Code
- **Package theo module nghiệp vụ (Package-by-Feature):** Tách rõ 6 phân hệ nghiệp vụ; tuyệt đối không gọi trực tiếp Repository của module khác.
- **Giao tiếp liên module:** Dùng Spring Modulith / Application Events kết hợp bảng `outbox_event` (Transactional Outbox).
- **Kiểm soát ranh giới:** Luôn có bài test ArchUnit hoặc `ApplicationModules.of(Application.class).verify()` để chặn tham chiếu chéo giữa các module.
- **Phân lớp trong mỗi module:** `Controller` → `Service` → `Repository` / `Adapter`. Không để logic nghiệp vụ trong Controller và không trả trực tiếp JPA `@Entity` ra REST API (luôn dùng DTO/Record).

## 2. Spring Data JPA, MySQL 8.0 & Flyway
- **Database chuẩn:** MySQL 8.0+ (`InnoDB`, `utf8mb4`).
  - Khóa chính: `BIGINT AUTO_INCREMENT` (`@GeneratedValue(strategy = GenerationType.IDENTITY)`).
  - Thời gian: Luôn dùng `Instant` / `OffsetDateTime` ở tầng Java, lưu `DATETIME(6)` chuẩn **UTC** trong MySQL. Tiêm `java.time.Clock` vào Service thay vì gọi trực tiếp `Instant.now()` để đảm bảo tính xác định (determinism) khi test.
- **Optimistic Locking:** Bắt buộc dùng `@Version` cho các thực thể có xung đột đồng thời (đặc biệt là `ticket.version`).
- **Transaction & Hiệu năng:**
  - Đặt `@Transactional(readOnly = true)` cho các hàm truy vấn đọc.
  - Tránh lỗi N+1 bằng `@EntityGraph`, `JOIN FETCH` hoặc DTO Projection.
  - Quản lý schema 100% bằng **Flyway** (`spring.jpa.hibernate.ddl-auto=validate`). Không sửa file migration đã chạy.

## 3. REST API & Bảo mật
- **Spring Security + JWT/RBAC:** Kiểm tra quyền ở cả tầng Endpoint (`@PreAuthorize`) và tầng nghiệp vụ (kiểm tra quyền sở hữu ticket / nhóm `support_team`).
- **Validation & Error Handling:** Dùng Jakarta Bean Validation (`@Valid`) trên request DTO. Chuẩn hóa lỗi trả về theo RFC 7807 (`ProblemDetail`) qua `@RestControllerAdvice`.
- **OpenAPI:** Tài liệu hóa đầy đủ endpoint và mã lỗi bằng `springdoc-openapi`.

## 4. Chiến lược Kiểm thử (Spring Boot 3.x + JUnit 5 + Testcontainers)
Áp dụng mô hình Test Pyramid — **chọn tầng test hẹp nhất đủ mang lại độ tin cậy**:

| Đối tượng kiểm thử | Tầng Test | Công cụ / Annotation |
| :--- | :--- | :--- |
| Logic nghiệp vụ, State Machine, Strategy | Unit Test (không khởi chạy Spring) | JUnit 5 + Mockito + AssertJ |
| JPA Query, Optimistic Locking, Outbox, View | Persistence Slice | `@DataJpaTest` + Testcontainers MySQL |
| Controller, Validation, Security RBAC, JSON | Web Slice | `@WebMvcTest` + `@MockitoBean` (Boot 3.4+) |
| Luồng E2E (Webhook idempotency, SLA, AI fallback) | Integration Test | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers |

### Quy tắc bắt buộc khi viết Test:
1. **Cấm dùng H2 in-memory:** Mọi test Persistence và Integration phải chạy trên **MySQL 8.0 thật qua Testcontainers** (`@ServiceConnection` với `MySQLContainer<?>` dùng chung 1 container cho cả suite để chạy nhanh).
2. **Tối ưu Context Caching:** Gom nhóm các test dùng chung cấu hình/profile; hạn chế tối đa `@DirtiesContext` và `@TestPropertySource` lẻ tẻ gây khởi tạo lại Spring Context.
3. **Cấu trúc ca kiểm thử:** Mỗi tính năng viết theo thứ tự: *Happy path → Edge cases → Exceptions/Errors*. Dùng `@DisplayName` mô tả rõ ý định.
4. **Kỷ luật Mocking & Assertion:**
   - Dùng AssertJ (`assertThat(...)`) so sánh giá trị/trạng thái cụ thể, tránh `assertNotNull` chung chung.
   - Chỉ mock các ranh giới bên ngoài (Repository, HTTP client gọi LLM, Clock); không mock DTO/Record hoặc chính class đang test.
   - Tuyệt đối không xóa, `@Disabled`, nới lỏng assertion hay thêm `-DskipTests` để ép build xanh.
