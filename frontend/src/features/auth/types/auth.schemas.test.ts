import { describe, expect, it } from "vitest";
import { loginSchema, registerSchema } from "./auth.schemas";

const valid = {
  fullName: "Nguyễn An",
  email: "an@example.com",
  password: "Password123!",
  confirmPassword: "Password123!",
  phone: "",
};

describe("auth forms", () => {
  it("normalizes surrounding email/name whitespace but preserves password", () => {
    expect(
      registerSchema.parse({
        ...valid,
        fullName: "  Nguyễn An  ",
        email: " an@example.com ",
      }),
    ).toMatchObject({ fullName: "Nguyễn An", email: "an@example.com" });
    expect(
      loginSchema.parse({ email: " an@example.com ", password: " pass " })
        .password,
    ).toBe(" pass ");
  });
  it("rejects empty names, invalid email and passwords outside backend limits", () => {
    for (const patch of [
      { fullName: "   " },
      { email: "abc" },
      { password: "1234567", confirmPassword: "1234567" },
      { password: "a".repeat(101), confirmPassword: "a".repeat(101) },
      { fullName: "a".repeat(151) },
      { phone: "1".repeat(31) },
    ]) {
      expect(registerSchema.safeParse({ ...valid, ...patch }).success).toBe(
        false,
      );
    }
  });
  it("attaches mismatched confirmation errors to the confirmation field", () => {
    const result = registerSchema.safeParse({
      ...valid,
      confirmPassword: "different",
    });
    expect(result.success).toBe(false);
    if (!result.success)
      expect(result.error.issues[0].path).toEqual(["confirmPassword"]);
  });
  it("allows optional phone and existing login passwords under eight characters", () => {
    expect(registerSchema.safeParse(valid).success).toBe(true);
    expect(
      loginSchema.safeParse({ email: valid.email, password: "short" }).success,
    ).toBe(true);
    expect(
      loginSchema.safeParse({ email: valid.email, password: "" }).success,
    ).toBe(false);
  });
});
