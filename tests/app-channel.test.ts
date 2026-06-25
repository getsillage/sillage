import { env } from "cloudflare:test";
import { describe, expect, it } from "vitest";
import { getAppVersionBadge, shouldBypassAuth } from "../app/lib/app-channel";

type TestEnv = typeof env & { APP_RELEASE_CHANNEL?: "beta" | "production" };

describe("app release channel", () => {
  it("shows the development badge during local development", () => {
    const betaEnv = { ...env, APP_RELEASE_CHANNEL: "beta" } as TestEnv;
    expect(getAppVersionBadge(betaEnv, { isDevelopment: true })).toEqual({
      label: "开发版",
      tone: "development",
    });
    expect(shouldBypassAuth(betaEnv, { isDevelopment: true })).toBe(false);
  });

  it("shows the beta badge and bypasses auth for beta deployments", () => {
    const betaEnv = { ...env, APP_RELEASE_CHANNEL: "beta" } as TestEnv;
    expect(getAppVersionBadge(betaEnv, { isDevelopment: false })).toEqual({
      label: "β版",
      tone: "beta",
    });
    expect(shouldBypassAuth(betaEnv, { isDevelopment: false })).toBe(true);
  });

  it("does not show a badge or bypass auth for production", () => {
    const productionEnv = { ...env, APP_RELEASE_CHANNEL: "production" } as TestEnv;
    expect(getAppVersionBadge(productionEnv, { isDevelopment: false })).toBeNull();
    expect(shouldBypassAuth(productionEnv, { isDevelopment: false })).toBe(false);
  });
});
