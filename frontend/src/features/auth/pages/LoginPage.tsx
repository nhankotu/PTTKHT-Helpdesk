import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Alert, Button, Input } from "antd";
import {
  ArrowRightOutlined,
  LockOutlined,
  MailOutlined,
} from "@ant-design/icons";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { loginSchema } from "../types/auth.schemas";
import type { LoginValues } from "../types/auth.schemas";
import { authApi } from "../api/auth.api";
import { useAuth } from "../hooks/useAuth";
import { useAuthRequest } from "../hooks/useAuthRequest";
import { AuthField } from "../components/AuthField";
import { apiErrorMessage } from "../../../shared/api/client";

export function LoginPage() {
  const { restoring } = useAuth();
  const authenticate = useAuthRequest(authApi.login);
  const navigate = useNavigate();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });
  const mutation = useMutation({
    mutationFn: authenticate,
    onSuccess: () => navigate("/", { replace: true }),
    onError: (error) => setSubmitError(apiErrorMessage(error)),
  });
  const busy = restoring || mutation.isPending;
  return (
    <div className="auth-form-container">
      <span className="auth-form-kicker">Chào mừng bạn trở lại</span>
      <h1>Đăng nhập</h1>
      <p className="auth-description">
        Tiếp tục kết nối, cùng nhau giải quyết.
      </p>
      <form
        noValidate
        onSubmit={handleSubmit((values) => {
          if (!busy) {
            setSubmitError(null);
            mutation.mutate(values);
          }
        })}
        className="auth-form"
        aria-busy={busy}
      >
        {submitError && (
          <Alert
            type="error"
            showIcon
            title={submitError}
            closable
            onClose={() => setSubmitError(null)}
          />
        )}
        <AuthField id="email" label="Email" error={errors.email?.message}>
          <Controller
            name="email"
            control={control}
            render={({ field }) => (
              <Input
                {...field}
                id="email"
                type="email"
                autoComplete="username"
                placeholder="ban@example.com"
                prefix={<MailOutlined aria-hidden="true" />}
                size="large"
                status={errors.email ? "error" : undefined}
                aria-invalid={!!errors.email}
                aria-describedby={errors.email ? "email-help" : undefined}
                disabled={busy}
              />
            )}
          />
        </AuthField>
        <AuthField
          id="password"
          label="Mật khẩu"
          error={errors.password?.message}
        >
          <Controller
            name="password"
            control={control}
            render={({ field }) => (
              <Input.Password
                {...field}
                id="password"
                autoComplete="current-password"
                placeholder="Nhập mật khẩu của bạn"
                prefix={<LockOutlined aria-hidden="true" />}
                size="large"
                status={errors.password ? "error" : undefined}
                aria-invalid={!!errors.password}
                aria-describedby={errors.password ? "password-help" : undefined}
                disabled={busy}
              />
            )}
          />
        </AuthField>
        <p className="session-note">
          <LockOutlined aria-hidden="true" /> Phiên đăng nhập được lưu trong tab
          trình duyệt này.
        </p>
        <Button
          type="primary"
          htmlType="submit"
          size="large"
          block
          loading={mutation.isPending}
          disabled={busy}
          aria-label={restoring ? "Đang kiểm tra phiên..." : "Đăng nhập"}
          className="auth-submit"
        >
          {restoring ? "Đang kiểm tra phiên..." : "Đăng nhập"}{" "}
          <ArrowRightOutlined aria-hidden="true" />
        </Button>
        <div className="auth-form-bottom">
          <span>Một tài khoản cho mọi kết nối</span>
          <p>Dành cho khách hàng và đội ngũ hỗ trợ.</p>
        </div>
      </form>
    </div>
  );
}
