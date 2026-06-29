import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
  subscribeAccessToken,
} from "./auth";

beforeEach(() => {
  sessionStorage.clear();
  clearAccessToken();
});

afterEach(() => {
  sessionStorage.clear();
});

describe("access token store", () => {
  it("sets, reads, and clears the token in sessionStorage", () => {
    setAccessToken("abc");
    expect(getAccessToken()).toBe("abc");
    expect(sessionStorage.getItem("sillage.accessToken")).toBe("abc");
    clearAccessToken();
    expect(getAccessToken()).toBeNull();
    expect(sessionStorage.getItem("sillage.accessToken")).toBeNull();
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
