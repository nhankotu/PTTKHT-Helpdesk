import { api } from "../../../shared/api/client";
import type { components } from "../../../shared/api/generated/schema";
import type { LoginValues, RegisterValues } from "../types/auth.schemas";

export type UserProfile = components["schemas"]["UserProfileDto"];
export type AuthResponse = components["schemas"]["AuthResponse"];

export const authApi = {
  login: async (values: LoginValues, signal?: AbortSignal) =>
    (await api.post<AuthResponse>("/auth/login", values, { signal })).data,
  register: async (
    { confirmPassword: _confirmation, ...values }: RegisterValues,
    signal?: AbortSignal,
  ) =>
    (
      await api.post<AuthResponse>(
        "/auth/register",
        {
          ...values,
          phone: values.phone || undefined,
        },
        { signal },
      )
    ).data,
  me: async (signal?: AbortSignal) =>
    (await api.get<UserProfile>("/auth/me", { signal })).data,
  logout: async (accessToken?: string) => {
    await api.post("/auth/logout", undefined, {
      headers: accessToken
        ? { Authorization: `Bearer ${accessToken}` }
        : undefined,
    });
  },
};
