import { z } from "zod";

export const tokensSchema = z.object({
  accessToken: z.string().min(1),
  refreshToken: z.string().min(1),
});
export type SessionTokens = z.infer<typeof tokensSchema>;
const key = "helpdesk.session";
type SessionStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export function createTokenStore(storage?: SessionStorage) {
  let tokens: SessionTokens | null = null;
  let revision = 0;
  try {
    const raw = storage?.getItem(key);
    if (raw) {
      const result = tokensSchema.safeParse(JSON.parse(raw));
      if (result.success) tokens = result.data;
      else storage?.removeItem(key);
    }
  } catch {
    /* An unavailable or corrupt store must not prevent rendering. */
  }
  return {
    get: () => tokens,
    revision: () => revision,
    set(next: SessionTokens) {
      tokens = tokensSchema.parse(next);
      revision++;
      try {
        storage?.setItem(key, JSON.stringify(tokens));
      } catch {
        /* Keep a memory-only session. */
      }
    },
    clear() {
      tokens = null;
      revision++;
      try {
        storage?.removeItem(key);
      } catch {
        /* Memory has already been cleared. */
      }
    },
  };
}
export type TokenStore = ReturnType<typeof createTokenStore>;

function browserStorage() {
  try {
    return window.sessionStorage;
  } catch {
    return undefined;
  }
}
export const tokenStore = createTokenStore(browserStorage());
