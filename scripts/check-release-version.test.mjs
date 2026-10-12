import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { auditReleaseVersion } from "./check-release-version.mjs";

test("accepts matching stable and prerelease Web versions", () => {
  for (const version of ["0.4.0", "1.0.0-rc.1"]) {
    assert.deepEqual(auditReleaseVersion({ tag: `v${version}`, version }), []);
  }
});

test("rejects a malformed tag, missing version, and a mismatched version", () => {
  assert.match(auditReleaseVersion({ tag: "0.4.0", version: "0.4.0" })[0], /release tag must match/);
  assert.match(auditReleaseVersion({ tag: "v0.4.0" })[0], /Web package version must match/);
  assert.match(auditReleaseVersion({ tag: "v0.4.0", version: "0.3.0" })[0], /does not match/);
});

test("reads the release version from the Web manifest and fails if it is missing", () => {
  const directory = mkdtempSync(join(tmpdir(), "sillage-release-version-"));
  const script = fileURLToPath(new URL("./check-release-version.mjs", import.meta.url));
  try {
    const missing = spawnSync(process.execPath, [script, "v0.4.0"], {
      cwd: directory,
      encoding: "utf8",
    });
    assert.equal(missing.status, 1);
    assert.match(missing.stderr, /cannot read Web package version/);
    mkdirSync(join(directory, "apps", "web"), { recursive: true });
    writeFileSync(join(directory, "apps", "web", "package.json"), '{"version":"0.4.0"}');
    assert.match(execFileSync(process.execPath, [script, "v0.4.0"], {
      cwd: directory,
      encoding: "utf8",
    }), /release version check passed: v0.4.0/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
