import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Alert, Button, Input } from "antd";
import {
  ArrowRightOutlined,
  CheckOutlined,
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { registerSchema } from "../types/auth.schemas";
import type { RegisterValues } from "../types/auth.schemas";
import { authApi } from "../api/auth.api";
import { useAuth } from "../hooks/useAuth";
import { useAuthRequest } from "../hooks/useAuthRequest";
import { AuthField } from "../components/AuthField";
import { apiErrorMessage } from "../../../shared/api/client";

export function RegisterPage() {
  const { restoring } = useAuth();
  const authenticate = useAuthRequest(authApi.register);
  const navigate = useNavigate();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      fullName: "",
      email: "",
      phone: "",
      password: "",
      confirmPassword: "",
    },
  });
  const mutation = useMutation({
    mutationFn: authenticate,
    onSuccess: () => navigate("/", { replace: true }),
    onError: (error) => setSubmitError(apiErrorMessage(error)),
  });
  const busy = restoring || mutation.isPending;
  return (
    <div className="auth-form-container register-form-container">
      <span className="auth-form-kicker">Rất vui được kết nối với bạn</span>
      <h1>Tạo tài khoản</h1>
      <p className="auth-description">
        Bắt đầu trải nghiệm hỗ trợ dành riêng cho bạn.
      </p>
      <div className="register-benefits">
        <span>
          <CheckOutlined aria-hidden="true" /> Gửi yêu cầu dễ dàng
        </span>
        <span>
          <CheckOutlined aria-hidden="true" /> Theo dõi mọi phản hồi
        </span>
      </div>
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
        <AuthField
          id="fullName"
          label="Họ và tên"
          error={errors.fullName?.message}
        >
          <Controller
            name="fullName"
            control={control}
            render={({ field }) => (
              <Input
                {...field}
                id="fullName"
                autoComplete="name"
                placeholder="Nguyễn Minh An"
                prefix={<UserOutlined aria-hidden="true" />}
                size="large"
                status={errors.fullName ? "error" : undefined}
                aria-invalid={!!errors.fullName}
                aria-describedby={errors.fullName ? "fullName-help" : undefined}
                disabled={busy}
              />
            )}
          />
        </AuthField>
        <AuthField id="email" label="Email" error={errors.email?.message}>
          <Controller
            name="email"
            control={control}
            render={({ field }) => (
              <Input
                {...field}
                id="email"
                type="email"
                autoComplete="email"
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
          id="phone"
          label="Số điện thoại"
          optional
          error={errors.phone?.message}
        >
          <Controller
            name="phone"
            control={control}
            render={({ field }) => (
              <Input
                {...field}
                id="phone"
                type="tel"
                autoComplete="tel"
                placeholder="Nhập số điện thoại"
                prefix={<PhoneOutlined aria-hidden="true" />}
                size="large"
                status={errors.phone ? "error" : undefined}
                aria-invalid={!!errors.phone}
                aria-describedby={errors.phone ? "phone-help" : undefined}
                disabled={busy}
              />
            )}
          />
        </AuthField>
        <AuthField
          id="password"
          label="Mật khẩu"
          error={errors.password?.message}
          hint="Sử dụng từ 8 đến 100 ký tự."
        >
          <Controller
            name="password"
            control={control}
            render={({ field }) => (
              <Input.Password
                {...field}
                id="password"
                autoComplete="new-password"
                placeholder="Tạo mật khẩu của bạn"
                prefix={<LockOutlined aria-hidden="true" />}
                size="large"
                status={errors.password ? "error" : undefined}
                aria-invalid={!!errors.password}
                aria-describedby="password-help"
                disabled={busy}
              />
            )}
          />
        </AuthField>
        <AuthField
          id="confirmPassword"
          label="Nhập lại mật khẩu"
          error={errors.confirmPassword?.message}
        >
          <Controller
            name="confirmPassword"
            control={control}
            render={({ field }) => (
              <Input.Password
                {...field}
                id="confirmPassword"
                autoComplete="new-password"
                placeholder="Nhập lại mật khẩu vừa tạo"
                prefix={<LockOutlined aria-hidden="true" />}
                size="large"
                status={errors.confirmPassword ? "error" : undefined}
                aria-invalid={!!errors.confirmPassword}
                aria-describedby={
                  errors.confirmPassword ? "confirmPassword-help" : undefined
                }
                disabled={busy}
              />
            )}
          />
        </AuthField>
        <Button
          type="primary"
          htmlType="submit"
          size="large"
          block
          loading={mutation.isPending}
          disabled={busy}
          aria-label={restoring ? "Đang kiểm tra phiên..." : "Tạo tài khoản"}
          className="auth-submit"
        >
          {restoring ? "Đang kiểm tra phiên..." : "Tạo tài khoản"}{" "}
          <ArrowRightOutlined aria-hidden="true" />
        </Button>
        <p className="register-note">
          <LockOutlined aria-hidden="true" /> Tài khoản được tạo với vai trò
          khách hàng.
        </p>
      </form>
    </div>
  );
}
