import { useEffect, useRef } from "react";
import type { AuthResponse } from "../api/auth.api";
import { useAuth } from "./useAuth";

/** A response belongs to the form that submitted it, never a later page. */
export function useAuthRequest<T>(
  request: (values: T, signal?: AbortSignal) => Promise<AuthResponse>,
) {
  const { acceptSession } = useAuth();
  const pending = useRef<AbortController | null>(null);
  useEffect(() => () => pending.current?.abort(), []);
  return async (values: T) => {
    pending.current?.abort();
    const controller = new AbortController();
    pending.current = controller;
    const response = await request(values, controller.signal);
    await acceptSession(response, controller.signal);
  };
}
