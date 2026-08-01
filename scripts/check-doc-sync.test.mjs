import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import test from "node:test";

import { isDependabotMaintenance } from "./lib/doc-sync-policy.mjs";

test("allows main push verification to skip pull-request range policy", () => {
  const result = spawnSync(
    process.execPath,
    ["scripts/check-doc-sync.mjs", "--base", "definitely-not-a-git-ref"],
    {
      cwd: new URL("..", import.meta.url),
      encoding: "utf8",
      env: { ...process.env, DOC_SYNC_ENFORCE: "false" },
    },
  );

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /enforced at the pull request boundary/);
});

test("accepts trusted Dependabot dependency surfaces", () => {
  assert.equal(
    isDependabotMaintenance(
      [".github/workflows/ci.yml", "apps/web/package.json", "apps/web/pnpm-lock.yaml"],
      "true",
    ),
    true,
  );
});

test("rejects source changes and untrusted pull requests", () => {
  assert.equal(isDependabotMaintenance(["apps/web/src/main.tsx"], "true"), false);
  assert.equal(isDependabotMaintenance(["apps/web/package.json"], "false"), false);
});
