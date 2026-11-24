import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { authApi } from "../api/auth.api";
import type { AuthResponse, UserProfile } from "../api/auth.api";
import { tokenStore, tokensSchema } from "../../../shared/api/session";
import { sessionExpiredEvent } from "../../../shared/api/client";

type AuthContextValue = {
  user: UserProfile | null;
  restoring: boolean;
  restoreError: unknown;
  retryRestore: () => void;
  acceptSession: (
    response: AuthResponse,
    signal?: AbortSignal,
  ) => Promise<void>;
  logout: () => Promise<void>;
};
const AuthContext = createContext<AuthContextValue | null>(null);
const profileKey = ["auth", "me"];

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [hasSession, setHasSession] = useState(() => !!tokenStore.get());
  const profile = useQuery({
    queryKey: profileKey,
    queryFn: ({ signal }) => authApi.me(signal),
    enabled: hasSession,
    retry: false,
    staleTime: Infinity,
  });

  useEffect(() => {
    const expired = () => {
      setHasSession(false);
      queryClient.removeQueries({ queryKey: profileKey });
    };
    window.addEventListener(sessionExpiredEvent, expired);
    return () => window.removeEventListener(sessionExpiredEvent, expired);
  }, [queryClient]);

  async function acceptSession(response: AuthResponse, signal?: AbortSignal) {
    signal?.throwIfAborted();
    const tokens = tokensSchema.parse(response);
    if (!response.user?.id) throw new Error("Missing user profile");
    await queryClient.cancelQueries({ queryKey: profileKey });
    signal?.throwIfAborted();
    queryClient.clear();
    tokenStore.set(tokens);
    queryClient.setQueryData(profileKey, response.user);
    setHasSession(true);
  }

  async function logout() {
    // Clear local credentials even when the network is unavailable.
    const request = authApi
      .logout(tokenStore.get()?.accessToken)
      .catch(() => undefined);
    tokenStore.clear();
    setHasSession(false);
    await queryClient.cancelQueries();
    queryClient.clear();
    void request;
  }

  return (
    <AuthContext.Provider
      value={{
        user: hasSession ? (profile.data ?? null) : null,
        restoring: hasSession && profile.isPending,
        restoreError: hasSession ? profile.error : null,
        retryRestore: () => {
          void profile.refetch();
        },
        acceptSession,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth requires AuthProvider");
  return context;
}
