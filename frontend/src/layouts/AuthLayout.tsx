import { Link, Outlet, useLocation, Navigate } from "react-router-dom";
import {
  ArrowLeftOutlined,
  CheckOutlined,
  CustomerServiceOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";
import { Brand } from "../shared/components/Brand";
import { useAuth } from "../features/auth/hooks/useAuth";

export function AuthLayout() {
  const { user } = useAuth();
  const isRegister = useLocation().pathname === "/register";
  if (user) return <Navigate to="/" replace />;
  return (
    <div className="auth-shell">
      <aside className="auth-story">
        <Brand />
        <div className="auth-story-main">
          <span className="story-symbol">
            <CustomerServiceOutlined aria-hidden="true" />
          </span>
          <h2>
            Hỗ trợ tận tâm.
            <br />
            Kết nối dài lâu.
          </h2>
          <p>
            Mỗi yêu cầu là một cuộc hội thoại.
            <br />
            Mỗi cuộc hội thoại là cơ hội để thấu hiểu.
          </p>
          <div className="journey-card">
            <div className="flex items-center justify-between">
              <span className="journey-ticket">Yêu cầu #1024</span>
              <span className="journey-status">
                <span className="online-dot" /> Đã giải quyết
              </span>
            </div>
            <h3>
              Một vấn đề được giải quyết.
              <br />
              Một khách hàng an tâm.
            </h3>
            <div className="journey-line">
              <span>
                <CheckOutlined aria-hidden="true" />
              </span>
              <i />
              <span>
                <CheckOutlined aria-hidden="true" />
              </span>
              <i />
              <span>
                <CheckOutlined aria-hidden="true" />
              </span>
            </div>
            <div className="journey-labels">
              <span>Tiếp nhận</span>
              <span>Hỗ trợ</span>
              <span>Hoàn tất</span>
            </div>
            <p>Tình huống minh họa trên Helpdesk</p>
          </div>
          <div className="story-note">
            <SafetyCertificateOutlined aria-hidden="true" />
            <span>
              Thông tin đúng người.
              <br />
              <strong>Hội thoại luôn liền mạch.</strong>
            </span>
          </div>
        </div>
        <span className="auth-story-footer">
          Helpdesk · Cùng bạn trong từng yêu cầu
        </span>
      </aside>
      <div className="auth-content">
        <div className="auth-topline">
          <Link to="/" className="back-link">
            <ArrowLeftOutlined aria-hidden="true" /> Trang chủ
          </Link>
          <p>
            {isRegister ? "Đã có tài khoản?" : "Chưa có tài khoản?"}{" "}
            <Link to={isRegister ? "/login" : "/register"}>
              {isRegister ? "Đăng nhập" : "Đăng ký"}
            </Link>
          </p>
        </div>
        <main id="main-content" className="auth-main">
          <Outlet />
        </main>
        <footer className="auth-footer">
          <SafetyCertificateOutlined aria-hidden="true" /> Kết nối an toàn. Hỗ
          trợ tận tâm.
        </footer>
      </div>
    </div>
  );
}
