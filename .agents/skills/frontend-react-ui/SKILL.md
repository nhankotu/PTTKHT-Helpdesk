---
name: frontend-react-ui
description: Thiết kế UI/UX chuyên nghiệp và tối ưu hiệu năng Frontend React 18+ (TypeScript, Vite, TanStack Query, Tailwind CSS, Ant Design). Dùng khi xây dựng Cổng khách hàng, Không gian Agent 3 cột, Dashboard Supervisor và viết test Vitest/Playwright.
---

# Frontend React, Performance & UI/UX Skill

## 1. Kiến trúc & Chống xung đột CSS (Tailwind + Ant Design)
- **Tổ chức theo feature:** `src/features/{auth,tickets,workspace,kb,reports}` với cấu trúc `api/`, `components/`, `hooks/`, `types/`.
- **Quy tắc sống chung Tailwind CSS & Ant Design:**
  - Cấu hình `corePlugins: { preflight: false }` trong Tailwind (hoặc tách `@layer`) để tránh reset CSS phá vỡ style gốc của Button, Table, Modal của Ant Design.
  - Dùng **Ant Design** cho component phức tạp có state (Table, Form, Modal, DatePicker, Select, Tabs) và **Tailwind CSS** cho layout (Flex/Grid, spacing, responsive, màu trạng thái).
- **TypeScript nghiêm ngặt:** Cấm dùng `any`; định nghĩa rõ interface/type đồng bộ với OpenAPI schema của Backend.

## 2. Tối ưu Hiệu năng React & TanStack Query (Vercel Best Practices)
- **Chống Request Waterfall:** Dùng `Promise.all` hoặc các `useQuery` song song khi tải dữ liệu độc lập (ví dụ: tải chi tiết ticket, lịch sử hội thoại và gợi ý AI cùng lúc thay vì nối tiếp).
- **Quản lý Server State bằng TanStack Query:**
  - Không copy dữ liệu từ API vào `useState` nếu không cần chỉnh sửa cục bộ.
  - Cấu hình `queryKey` phân cấp rõ ràng (`['tickets', ticketId, 'messages']`) để `invalidateQueries` chính xác sau mỗi mutation.
  - Xử lý Optimistic Locking (`409 Conflict` khi `ticket.version` lệch): Hiển thị cảnh báo rõ ràng cho Agent kèm nút "Tải lại bản mới nhất" thay vì im lặng hoặc đè dữ liệu.
- **Giảm Re-render cho màn hình nhiều dữ liệu:**
  - Tách phần nhập liệu (composer) thành component riêng để việc gõ phím không re-render toàn bộ danh sách tin nhắn.
  - Dùng `useMemo` / `useCallback` có chọn lọc cho danh sách ticket lớn và các bộ lọc bảng báo cáo.

## 3. Tiêu chuẩn UI/UX cho 3 Giao diện chính (UI-UX Pro Max)
Áp dụng hệ thống thiết kế SaaS chuyên nghiệp: phân cấp thị giác rõ ràng, khoảng trắng hợp lý, trạng thái Loading (Skeleton) / Empty / Error đầy đủ.

1. **Cổng Khách hàng (Customer Portal):**
   - Giao diện tối giản, tập trung vào biểu mẫu tạo ticket nhanh, theo dõi trạng thái trực quan (stepper/badge) và khung chat chỉ hiển thị tin `PUBLIC` + câu trả lời AI có gắn thẻ trích dẫn nguồn (`ai_citation`).
   - Tích hợp widget đánh giá CSAT ngay khi ticket chuyển sang `RESOLVED`.
2. **Không gian làm việc Agent 3 cột (Agent Workspace):**
   - **Cột trái (Danh sách Ticket):** Bộ lọc nhanh theo trạng thái, mức ưu tiên và chỉ báo đếm ngược SLA (vàng ở mốc cảnh báo 80%, đỏ khi quá hạn).
   - **Cột giữa (Hội thoại & Soạn thảo):** Phân biệt **cực kỳ trực quan** giữa tab **Trả lời công khai (PUBLIC — nền trắng/xanh nhạt)** và **Ghi chú nội bộ (INTERNAL — nền vàng nhạt/viền hổ phách + icon ổ khóa)** để Agent không bao giờ gửi nhầm nội bộ cho khách.
   - **Cột phải (Context, SLA & AI Copilot):** Hiển thị thông tin khách hàng, trạng thái SLA (có nút Pause/Resume nếu được phép), thẻ phân loại AI và gợi ý câu trả lời RAG kèm link xem trước đoạn trích KB.
3. **Bảng điều khiển Supervisor / Admin & Báo cáo (UC08):**
   - Tầng trên: KPI Cards (Tổng ticket, Tỷ lệ đạt SLA, Thời gian phản hồi đầu trung bình, Điểm CSAT).
   - Tầng giữa: Biểu đồ trực quan theo bộ lọc thời gian/nhóm (đọc từ API tổng hợp `v_ticket_sla_summary`).
   - Khu vực quản trị: Quản lý hàng đợi Fallback/Escalation, duyệt/xuất bản/thu hồi phiên bản KB (`kb_version`).

## 4. Kiểm thử Frontend (Vitest & Playwright)
- **Vitest + Testing Library:** Test các component quan trọng (phân biệt mode PUBLIC/INTERNAL, form validation, xử lý lỗi 409).
- **Playwright (E2E):** Dùng semantic locator (`getByRole`, `getByLabel`, `getByTestId`), không dùng CSS selector mong manh. Tập trung kiểm chứng luồng đăng nhập RBAC, tạo ticket, chuyển trạng thái và kiểm tra giao diện khách hàng tuyệt đối không thấy ghi chú nội bộ.
