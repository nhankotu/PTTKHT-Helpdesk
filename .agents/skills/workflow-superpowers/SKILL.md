---
name: workflow-superpowers
description: Quy trình kỹ thuật chuẩn (Plan -> TDD -> Debug -> Verify) chuyển thể từ obra/superpowers. Luôn áp dụng trước khi thiết kế tính năng mới, sửa lỗi phức tạp hoặc trước khi báo hoàn thành một Use Case.
---

# Engineering Workflow Skill (Superpowers Edition)

## 1. Lập kế hoạch trước khi viết code (Plan-First)
Trước khi triển khai bất kỳ Use Case (UC00–UC08) hay thay đổi cấu trúc nào:
1. **Xác nhận phạm vi & ranh giới module:** Liệt kê rõ các file sẽ tạo/sửa thuộc phân hệ nào, bảng DB nào bị ảnh hưởng và có phát sinh sự kiện liên module (`outbox_event`) hay không.
2. **Chia nhỏ thành các bước kiểm chứng được:** Mỗi bước hoàn thành một đơn vị logic nhỏ (Entity/Migration → Repository → Service/State Machine → Controller → Test).
3. **Không đoán mò:** Nếu thiếu thông tin cột trong schema hoặc quy tắc chuyển trạng thái, đọc lại `helpdesk-domain` và file SQL tham chiếu trước khi code.

## 2. Kỷ luật TDD (Red – Green – Refactor)
Áp dụng bắt buộc cho toàn bộ logic nghiệp vụ trọng tâm (State Machine, Routing Strategy, SLA Calculation, Idempotency, AI Fallback, RBAC):
1. **RED:** Viết test thất bại trước để mô tả đúng hành vi mong muốn (hoặc tái hiện bug). Chạy test để chắc chắn nó fail đúng lý do.
2. **GREEN:** Viết lượng code tối thiểu nhất để test pass. Không over-engineer.
3. **REFACTOR:** Dọn dẹp code trùng lặp, tách hàm rõ nghĩa, giữ nguyên toàn bộ test xanh.

## 3. Gỡ lỗi có hệ thống (Systematic Debugging)
Khi gặp lỗi build, test fail hoặc bug runtime — **cấm sửa mò theo cảm tính**:
1. **Đọc kỹ bằng chứng:** Phân tích đầy đủ stack trace, câu SQL Hibernate sinh ra, HTTP status và log container.
2. **Tái hiện tối thiểu:** Viết một unit/slice test tái hiện đúng lỗi đó.
3. **Khoanh vùng nguyên nhân gốc (Root Cause):** Kiểm tra lần lượt từ ranh giới dữ liệu (DTO/JSON) → Transaction/Locking → Query DB → Logic Service.
4. **Sửa tận gốc & Khóa hồi quy:** Chỉ sửa 1 giả thuyết tại một thời điểm, chạy lại test để xác nhận fix thành công.

## 4. Kiểm chứng trước khi báo xong (Verification Before Completion)
Tuyệt đối không kết luận "đã xong" chỉ dựa vào việc đọc code. Trước khi chốt task, bắt buộc tự kiểm tra:
- [ ] Code biên dịch sạch không lỗi (`mvn test-compile` / `npm run build`).
- [ ] Toàn bộ test liên quan (đặc biệt là Testcontainers MySQL / Vitest) đã chạy và **PASS thực tế**.
- [ ] Không có rò rỉ dữ liệu nhạy cảm (`INTERNAL` note, stack trace raw, JWT secret).
- [ ] Không vi phạm ranh giới module (ArchUnit / Spring Modulith check pass).
