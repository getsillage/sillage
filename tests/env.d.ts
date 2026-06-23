import type { D1Migration } from "cloudflare:test";

// Augments the test-provided env with our Worker's binding types plus the
// migrations array injected by vitest.config.ts.
declare module "cloudflare:test" {
  interface ProvidedEnv extends Env {
    TEST_MIGRATIONS: D1Migration[];
  }
}
