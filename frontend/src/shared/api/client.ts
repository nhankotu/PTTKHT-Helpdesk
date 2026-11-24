import axios from "axios";
import type { AxiosInstance, InternalAxiosRequestConfig } from "axios";
import { tokenStore, tokensSchema } from "./session";
import type { TokenStore } from "./session";

export const sessionExpiredEvent = "helpdesk:session-expired";
const publicEndpoints = ["/auth/login", "/auth/register", "/auth/refresh"];
const noRefreshEndpoints = [...publicEndpoints, "/auth/logout"];
type RetryConfig = InternalAxiosRequestConfig & { sessionRetried?: boolean };

export function createApiClient(
  store: TokenStore,
  onInvalid: () => void,
  transport?: AxiosInstance,
) {
  const client =
    transport ??
    axios.create({
      baseURL: import.meta.env.VITE_API_BASE_URL || "/api/v1",
      timeout: 15000,
    });
  let refreshing: Promise<void> | null = null;
  client.interceptors.request.use((config) => {
    if (!publicEndpoints.includes(config.url ?? "") && store.get()) {
      config.headers.Authorization = `Bearer ${store.get()!.accessToken}`;
    }
    return config;
  });
  client.interceptors.response.use(
    (response) => response,
    async (error: unknown) => {
      if (
        !axios.isAxiosError(error) ||
        error.response?.status !== 401 ||
        !error.config
      )
        throw error;
      const config = error.config as RetryConfig;
      if (noRefreshEndpoints.includes(config.url ?? "") || !store.get())
        throw error;
      if (config.sessionRetried) {
        store.clear();
        onInvalid();
        throw error;
      }
      config.sessionRetried = true;
      if (!refreshing) {
        const snapshot = store.revision();
        const refreshToken = store.get()!.refreshToken;
        refreshing = client
          .post("/auth/refresh", { refreshToken })
          .then((response) => {
            if (store.revision() !== snapshot)
              throw new Error("Session changed during refresh");
            store.set(tokensSchema.parse(response.data));
          })
          .catch((refreshError: unknown) => {
            if (
              store.revision() === snapshot &&
              axios.isAxiosError(refreshError) &&
              [401, 403].includes(refreshError.response?.status ?? 0)
            ) {
              store.clear();
              onInvalid();
            }
            throw refreshError;
          })
          .finally(() => {
            refreshing = null;
          });
      }
      await refreshing;
      return client.request(config);
    },
  );
  return client;
}

export const api = createApiClient(tokenStore, () =>
  window.dispatchEvent(new Event(sessionExpiredEvent)),
);

export function apiErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    if (!error.response || error.response.status >= 500)
      return "Không thể kết nối đến hệ thống. Vui lòng thử lại sau ít phút.";
    const data: unknown = error.response.data;
    if (
      data &&
      typeof data === "object" &&
      "detail" in data &&
      typeof data.detail === "string"
    )
      return data.detail;
    if (error.response.status === 401)
      return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
    if (error.response.status === 429)
      return "Bạn thao tác quá nhanh. Vui lòng đợi một chút rồi thử lại.";
    if (error.response.status === 403)
      return "Yêu cầu bị từ chối. Vui lòng mở giao diện tại localhost:5173 hoặc liên hệ quản trị viên.";
  }
  return "Chưa thể hoàn tất thao tác. Vui lòng thử lại.";
}
