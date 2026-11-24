import { z } from "zod";

const email = z
  .string()
  .trim()
  .email("Vui lòng nhập email hợp lệ.")
  .max(255, "Email tối đa 255 ký tự.");
export const loginSchema = z.object({
  email,
  password: z
    .string()
    .min(1, "Vui lòng nhập mật khẩu.")
    .max(100, "Mật khẩu tối đa 100 ký tự."),
});
export const registerSchema = z
  .object({
    fullName: z
      .string()
      .trim()
      .min(1, "Vui lòng nhập họ và tên.")
      .max(150, "Họ tên tối đa 150 ký tự."),
    email,
    password: z
      .string()
      .min(8, "Mật khẩu cần ít nhất 8 ký tự.")
      .max(100, "Mật khẩu tối đa 100 ký tự.")
      .refine(
        (value) => value.trim().length > 0,
        "Mật khẩu không được chỉ chứa khoảng trắng.",
      ),
    confirmPassword: z.string().min(1, "Vui lòng nhập lại mật khẩu."),
    phone: z
      .string()
      .trim()
      .max(30, "Số điện thoại tối đa 30 ký tự.")
      .optional(),
  })
  .refine((value) => value.password === value.confirmPassword, {
    message: "Mật khẩu nhập lại chưa khớp.",
    path: ["confirmPassword"],
  });

export type LoginValues = z.infer<typeof loginSchema>;
export type RegisterValues = z.infer<typeof registerSchema>;
