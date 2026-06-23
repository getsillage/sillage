import { describe, expect, it } from "vitest";
import { safeRedirect } from "../app/lib/auth/redirect";

describe("safeRedirect", () => {
  it("returns the fallback for null/undefined/non-string values", () => {
    expect(safeRedirect(null)).toBe("/");
    expect(safeRedirect(undefined)).toBe("/");
    // FormDataEntryValue can be a File; anything non-string is rejected.
    expect(safeRedirect(new File([], "x") as unknown as FormDataEntryValue)).toBe("/");
  });

  it("returns the fallback for empty or non-internal paths", () => {
    expect(safeRedirect("")).toBe("/");
    expect(safeRedirect("https://evil.example/login")).toBe("/");
    expect(safeRedirect("relative/path")).toBe("/");
  });

  it("rejects protocol-relative URLs that start with //", () => {
    expect(safeRedirect("//evil.example")).toBe("/");
  });

  it("honours a custom fallback when the target is unsafe", () => {
    expect(safeRedirect("//evil.example", "/login")).toBe("/login");
    expect(safeRedirect(null, "/home")).toBe("/home");
  });

  it("returns safe internal absolute paths unchanged", () => {
    expect(safeRedirect("/calendar")).toBe("/calendar");
    expect(safeRedirect("/entries/123?edit=1")).toBe("/entries/123?edit=1");
  });
});
