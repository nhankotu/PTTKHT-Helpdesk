import { useEffect } from "react";
import {
  Link,
  Outlet,
  createBrowserRouter,
  useLocation,
} from "react-router-dom";
import { AuthLayout } from "../layouts/AuthLayout";
import { HomePage } from "../features/home/pages/HomePage";

function Root() {
  const { pathname, hash } = useLocation();
  useEffect(() => {
    document.title = `${pathname === "/login" ? "Đăng nhập" : pathname === "/register" ? "Tạo tài khoản" : "Kết nối và hỗ trợ"} | Helpdesk`;
    if (hash)
      requestAnimationFrame(() =>
        document.getElementById(hash.slice(1))?.scrollIntoView(),
      );
    else window.scrollTo(0, 0);
  }, [pathname, hash]);
  return (
    <>
      <a href="#main-content" className="skip-link">
        Đến nội dung chính
      </a>
      <Outlet />
    </>
  );
}

export const router = createBrowserRouter([
  {
    element: <Root />,
    HydrateFallback: () => (
      <main id="main-content" className="not-found" role="status">
        <p>Đang mở Helpdesk...</p>
      </main>
    ),
    children: [
      { path: "/", element: <HomePage /> },
      {
        element: <AuthLayout />,
        children: [
          {
            path: "/login",
            lazy: async () => ({
              Component: (await import("../features/auth/pages/LoginPage"))
                .LoginPage,
            }),
          },
          {
            path: "/register",
            lazy: async () => ({
              Component: (await import("../features/auth/pages/RegisterPage"))
                .RegisterPage,
            }),
          },
        ],
      },
      {
        path: "*",
        element: (
          <main id="main-content" className="not-found">
            <span>404</span>
            <h1>Trang này chưa có trên Helpdesk.</h1>
            <p>Quay về trang chủ để tiếp tục khám phá.</p>
            <Link to="/" className="button-link">
              Về trang chủ
            </Link>
          </main>
        ),
      },
    ],
  },
]);
