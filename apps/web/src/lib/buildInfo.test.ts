import { describe, expect, it } from "vitest";
import { shortRevision, webBuildInfo, webServerBuildsMatch } from "./buildInfo";

describe("build metadata", () => {
  it("shortens revisions for diagnostics", () => {
    expect(shortRevision("0123456789abcdef")).toBe("0123456789ab");
    expect(shortRevision(undefined)).toBe("unknown");
  });

  it("does not report a mismatch for development or unknown builds", () => {
    expect(webBuildInfo.version).toBeTruthy();
    expect(webServerBuildsMatch("dev", "different")).toBe(true);
    expect(webServerBuildsMatch("v0.3.0", "unknown")).toBe(true);
  });
});
