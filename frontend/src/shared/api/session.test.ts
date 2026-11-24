import { describe, expect, it } from "vitest";
import { createTokenStore } from "./session";

function storage(initial: string | null = null) {
  let value = initial;
  return {
    getItem: () => value,
    setItem: (_: string, next: string) => {
      value = next;
    },
    removeItem: () => {
      value = null;
    },
  };
}

describe("tab session", () => {
  it("ignores corrupted storage without preventing rendering", () => {
    expect(createTokenStore(storage("{broken")).get()).toBeNull();
    expect(createTokenStore(storage('{"accessToken":42}')).get()).toBeNull();
  });
  it("stores tokens and removes the session on logout", () => {
    const backing = storage();
    const store = createTokenStore(backing);
    store.set({ accessToken: "access", refreshToken: "refresh" });
    expect(createTokenStore(backing).get()?.accessToken).toBe("access");
    store.clear();
    expect(createTokenStore(backing).get()).toBeNull();
  });
  it("works in memory when browser storage is disabled", () => {
    const denied = {
      getItem: () => {
        throw new Error("denied");
      },
      setItem: () => {
        throw new Error("denied");
      },
      removeItem: () => {
        throw new Error("denied");
      },
    };
    const store = createTokenStore(denied);
    store.set({ accessToken: "access", refreshToken: "refresh" });
    expect(store.get()?.accessToken).toBe("access");
    expect(() => store.clear()).not.toThrow();
    expect(store.get()).toBeNull();
  });
});
