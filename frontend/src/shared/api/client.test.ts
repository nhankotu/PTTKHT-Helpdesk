import axios, { AxiosError } from "axios";
import type { AxiosAdapter, InternalAxiosRequestConfig } from "axios";
import { describe, expect, it, vi } from "vitest";
import { createApiClient } from "./client";
import { createTokenStore } from "./session";

function unauthorized(config: InternalAxiosRequestConfig) {
  return Promise.reject(
    new AxiosError("Unauthorized", "ERR_BAD_REQUEST", config, undefined, {
      config,
      status: 401,
      statusText: "Unauthorized",
      data: {},
      headers: {},
    }),
  );
}

describe("API session recovery", () => {
  it("does not refresh or replay a rejected logout request", async () => {
    const store = createTokenStore();
    store.set({ accessToken: "expired", refreshToken: "expired" });
    const adapter = vi.fn<AxiosAdapter>((config) => unauthorized(config));
    const client = createApiClient(store, vi.fn(), axios.create({ adapter }));
    await expect(client.post("/auth/logout")).rejects.toBeInstanceOf(
      AxiosError,
    );
    expect(adapter).toHaveBeenCalledTimes(1);
  });
  it("refreshes concurrent expired requests once and replays them", async () => {
    const store = createTokenStore();
    store.set({ accessToken: "old", refreshToken: "refresh" });
    let refreshCount = 0;
    const adapter: AxiosAdapter = async (config) => {
      if (config.url === "/auth/refresh") {
        refreshCount++;
        await new Promise((resolve) => setTimeout(resolve, 5));
        return {
          config,
          status: 200,
          statusText: "OK",
          headers: {},
          data: { accessToken: "new", refreshToken: "new-refresh" },
        };
      }
      if (config.headers.Authorization !== "Bearer new")
        return unauthorized(config);
      return {
        config,
        status: 200,
        statusText: "OK",
        headers: {},
        data: { email: "an@example.com" },
      };
    };
    const client = createApiClient(store, vi.fn(), axios.create({ adapter }));
    await Promise.all([client.get("/auth/me"), client.get("/auth/me")]);
    expect(refreshCount).toBe(1);
    expect(store.get()?.accessToken).toBe("new");
  });
  it("does not refresh invalid login credentials", async () => {
    const store = createTokenStore();
    const adapter = vi.fn<AxiosAdapter>((config) => unauthorized(config));
    const client = createApiClient(store, vi.fn(), axios.create({ adapter }));
    await expect(client.post("/auth/login", {})).rejects.toBeInstanceOf(
      AxiosError,
    );
    expect(adapter).toHaveBeenCalledTimes(1);
  });
  it("clears a failed refresh and signals session expiration", async () => {
    const store = createTokenStore();
    store.set({ accessToken: "old", refreshToken: "expired" });
    const invalid = vi.fn();
    const client = createApiClient(
      store,
      invalid,
      axios.create({ adapter: (config) => unauthorized(config) }),
    );
    await expect(client.get("/auth/me")).rejects.toBeInstanceOf(AxiosError);
    expect(store.get()).toBeNull();
    expect(invalid).toHaveBeenCalledOnce();
  });
  it("does not resurrect a session when logout happens during refresh", async () => {
    const store = createTokenStore();
    store.set({ accessToken: "old", refreshToken: "refresh" });
    const client = createApiClient(
      store,
      vi.fn(),
      axios.create({
        adapter: async (config) => {
          if (config.url === "/auth/refresh") {
            store.clear();
            return {
              config,
              status: 200,
              statusText: "OK",
              headers: {},
              data: { accessToken: "new", refreshToken: "new-refresh" },
            };
          }
          return unauthorized(config);
        },
      }),
    );
    await expect(client.get("/auth/me")).rejects.toThrow();
    expect(store.get()).toBeNull();
  });
});
