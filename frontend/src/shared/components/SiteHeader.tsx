import { useState } from "react";
import { Link } from "react-router-dom";
import { Button, Drawer } from "antd";
import {
  MenuOutlined,
  ArrowRightOutlined,
  LogoutOutlined,
} from "@ant-design/icons";
import { Brand } from "./Brand";
import { useAuth } from "../../features/auth/hooks/useAuth";

export function SiteHeader() {
  const [open, setOpen] = useState(false);
  const { user, restoring, logout } = useAuth();
  const nav = (
    <>
      <a href="/#platform" onClick={() => setOpen(false)}>
        Nền tảng
      </a>
      <a href="/#how-it-works" onClick={() => setOpen(false)}>
        Cách hoạt động
      </a>
      <a href="/#start" onClick={() => setOpen(false)}>
        Bắt đầu sử dụng
      </a>
    </>
  );
  return (
    <header className="site-header">
      <div className="page-container header-inner">
        <Brand />
        <nav aria-label="Điều hướng chính" className="desktop-nav">
          {nav}
        </nav>
        <div className="header-actions">
          {restoring ? (
            <span className="text-sm text-slate-500" role="status">
              Đang tải...
            </span>
          ) : user ? (
            <Button
              icon={<LogoutOutlined aria-hidden="true" />}
              onClick={() => void logout()}
            >
              Đăng xuất
            </Button>
          ) : (
            <>
              <Link to="/login" className="login-link">
                Đăng nhập
              </Link>
              <Link to="/register" className="button-link small">
                Tạo tài khoản <ArrowRightOutlined aria-hidden="true" />
              </Link>
            </>
          )}
          <Button
            className="mobile-menu-button"
            type="text"
            aria-label="Mở menu"
            icon={<MenuOutlined aria-hidden="true" />}
            onClick={() => setOpen(true)}
          />
        </div>
      </div>
      <Drawer
        title="Khám phá Helpdesk"
        open={open}
        onClose={() => setOpen(false)}
        size={300}
      >
        <nav className="mobile-nav" aria-label="Điều hướng trên di động">
          {nav}
        </nav>
      </Drawer>
    </header>
  );
}
