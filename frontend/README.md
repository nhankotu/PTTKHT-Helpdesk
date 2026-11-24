# Helpdesk frontend

React + TypeScript strict + Vite, Tailwind CSS, Ant Design, React Router, TanStack Query, Axios, React Hook Form và Zod.

## Chạy local

Yêu cầu Node.js 24 LTS và npm. Từ thư mục repo, chạy MySQL và backend:

```powershell
docker compose up -d mysql
cd backend
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

Mở terminal khác:

```powershell
cd frontend
npm ci
npm run dev
```

Mở **http://localhost:5173**. Dùng `localhost` để khớp cấu hình CORS có sẵn của backend. Vite chuyển tiếp `/api` tới `http://127.0.0.1:8080`. Không cần tạo `.env` cho cấu hình mặc định; xem `.env.example` khi cần API URL khác. `VITE_*` là cấu hình công khai, không đặt secret ở đây.

## Các trang đã có

- `/`: trang chủ, bản minh họa hội thoại chuyển giữa Web form / Email / Live chat; thông tin tài khoản sau khi đăng nhập.
- `/login`: đăng nhập bằng email/mật khẩu cho các vai trò hiện có.
- `/register`: tạo tài khoản CUSTOMER, kiểm tra nhập lại mật khẩu, số điện thoại tùy chọn; backend trả phiên đăng nhập ngay khi tạo thành công.
- Route khác: trang 404 có liên kết quay về trang chủ.

Sau xác thực, người dùng quay về trang chủ. Chưa triển khai workspace/ticket/dashboard. Bản hội thoại trên trang chủ là minh họa, không tải dữ liệu ticket thật.

Profile `dev` của backend có `admin@helpdesk.local` / `Password123!` và các tài khoản mẫu khác trong `backend/docs/API_GUIDE.md`.

## Tổ chức nguồn

```text
src/
  app/                 # router, providers, App
  features/
    home/              # trang chủ và inbox minh họa
    auth/              # API, schema, form, session provider
  layouts/             # AuthLayout
  shared/
    api/               # Axios, tab session, OpenAPI types
    components/        # Brand, SiteHeader
  styles/              # design tokens, CSS theo breakpoint
```

Tailwind chỉ import theme/utilities, không bật Preflight để tránh reset Ant Design. Font Be Vietnam Pro được đóng gói local. Route auth tải theo nhu cầu. Type API sinh từ OpenAPI có sẵn, không viết lại DTO thủ công.

## Phiên đăng nhập

Access/refresh token được lưu trong `sessionStorage` cho tab hiện tại, không lưu mật khẩu hay token trong URL. Reload xác minh profile qua `/auth/me`; access token hết hạn được refresh tối đa một lần cho mỗi request, các request đồng thời dùng chung một lần refresh. Token refresh không hợp lệ sẽ xóa phiên. Backend tạm mất kết nối cho phép thử khôi phục lại từ trang chủ; đăng xuất luôn xóa phiên local dù API lỗi. Khi storage bị chặn, phiên hoạt động trong bộ nhớ tới lúc reload.

Backend hiện trả token qua JSON; FE tuân theo hợp đồng này. Khi triển khai production, cần thống nhất lại lưu trữ phiên/cookie với backend. Không xem kiểm tra route ở FE là thay thế cho phân quyền backend.

Điểm nhỏ chưa xử lý: nếu tự sửa sessionStorage thành JSON hỏng hoặc token chỉ chứa khoảng trắng, dữ liệu hỏng có thể vẫn còn trong storage. JSON hỏng không chặn trang hiển thị; lỗi khôi phục token có nút **Xóa phiên** trên trang chủ. Token hợp lệ do backend cấp không gặp trường hợp này.

## Kiểm tra

```powershell
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

E2E cần backend và MySQL đang chạy; Playwright tự khởi động Vite nếu cần. Một test tạo tài khoản CUSTOMER có email `fe-check-<timestamp>@example.com` trong database local, rồi kiểm tra đăng ký trùng, đăng nhập, logout và khôi phục phiên. Không chạy bộ test này trên production. Ảnh lỗi nằm trong `test-results/` (được Git bỏ qua); trace tắt để tránh lưu body xác thực.

Khi hợp đồng API thay đổi:

```powershell
npm run api:types
```

Lệnh đọc `../backend/docs/openapi.json`; cần cập nhật export backend trước khi sinh lại type. `npm run format` định dạng mã nguồn bằng Prettier.
