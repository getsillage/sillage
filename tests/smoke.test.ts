import { env } from "cloudflare:test";
import { describe, expect, it } from "vitest";

// Smoke test: confirms the Workers test pool boots with our wrangler bindings
// available. Replaced/expanded by real integration tests in later milestones.
describe("infra smoke", () => {
  it("exposes the D1 and KV bindings to tests", () => {
    expect(env.DB).toBeDefined();
    expect(env.SESSIONS).toBeDefined();
    expect(env.BLOBS).toBeDefined();
  });
});
