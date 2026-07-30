import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
  subscribeAccessToken,
} from "./auth";

beforeEach(() => {
  clearAccessToken();
});

afterEach(clearAccessToken);

describe("access token store", () => {
  it("keeps the token in memory and out of Web Storage", () => {
    setAccessToken("abc");
    expect(getAccessToken()).toBe("abc");
    expect(sessionStorage.length).toBe(0);
    expect(localStorage.length).toBe(0);
    clearAccessToken();
    expect(getAccessToken()).toBeNull();
  });

  it("notifies a subscriber on set and clear, and stops after unsubscribe", () => {
    const calls: (string | null)[] = [];
    const unsubscribe = subscribeAccessToken((t) => calls.push(t));
    setAccessToken("x");
    clearAccessToken();
    unsubscribe();
    setAccessToken("y"); // no longer observed
    expect(calls).toEqual(["x", null]);
  });

  it("notifies multiple independent subscribers", () => {
    const a = vi.fn();
    const b = vi.fn();
    const unsubscribeA = subscribeAccessToken(a);
    subscribeAccessToken(b);
    setAccessToken("z");
    expect(a).toHaveBeenCalledWith("z");
    expect(b).toHaveBeenCalledWith("z");

    unsubscribeA();
    clearAccessToken();
    expect(a).toHaveBeenCalledTimes(1);
    expect(b).toHaveBeenLastCalledWith(null);
  });
});
