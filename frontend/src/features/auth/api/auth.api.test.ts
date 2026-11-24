import { expect, it } from "vitest";
import { api } from "../../../shared/api/client";
import { tokenStore } from "../../../shared/api/session";
import { authApi } from "./auth.api";

it("sends the captured logout bearer even after the local session is cleared", async () => {
  const previous = api.defaults.adapter;
  tokenStore.set({
    accessToken: "test-logout-access",
    refreshToken: "test-refresh",
  });
  let authorization: unknown;
  api.defaults.adapter = async (config) => {
    authorization = config.headers.Authorization;
    return {
      config,
      status: 204,
      statusText: "No Content",
      headers: {},
      data: undefined,
    };
  };
  try {
    const request = authApi.logout(tokenStore.get()?.accessToken);
    tokenStore.clear();
    await request;
    expect(authorization).toBe("Bearer test-logout-access");
  } finally {
    api.defaults.adapter = previous;
    tokenStore.clear();
  }
});
