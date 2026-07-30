#!/usr/bin/env node

import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import test from "node:test";

const script = fileURLToPath(new URL("./compose-release-notes.mjs", import.meta.url));
const digest = `sha256:${"a".repeat(64)}`;

test("adds a digest-pinned install block to generated notes", () => {
  const body = compose({ generated: "## Changes\n\nGenerated changes." });

  assert.match(body, /<!-- sillage-install:start -->/);
  assert.match(body, new RegExp(`ghcr\\.io/getsillage/sillage@${digest}`));
  assert.match(body, /Sillage-v0\.2\.0\.spdx\.json/);
  assert.match(body, /gh attestation verify/);
  assert.match(body, /--signer-workflow getsillage\/sillage\/\.github\/workflows\/release\.yml/);
  assert.match(body, /## Changes\n\nGenerated changes\./);
});

test("replaces a marked install block without changing edited notes", () => {
  const existing = `<!-- sillage-install:start -->
## Install

old digest
<!-- sillage-install:end -->

---

## Changes

Hand-edited release notes.`;
  const body = compose({ existing });

  assert.doesNotMatch(body, /old digest/);
  assert.match(body, /Hand-edited release notes\./);
  assert.equal(body.match(/<!-- sillage-install:start -->/g)?.length, 1);
});

test("migrates the legacy install section", () => {
  const existing = `## Install

\`\`\`bash
docker pull ghcr.io/getsillage/sillage:latest
\`\`\`

---

## Known limitations

Keep this section.`;
  const body = compose({ existing });

  assert.doesNotMatch(body, /sillage:latest/);
  assert.match(body, /## Known limitations\n\nKeep this section\./);
});

function compose({ existing = "", generated = "fallback" }) {
  const directory = mkdtempSync(join(tmpdir(), "sillage-release-notes-"));
  try {
    const existingPath = join(directory, "existing.md");
    const generatedPath = join(directory, "generated.md");
    const outputPath = join(directory, "output.md");
    writeFileSync(existingPath, existing);
    writeFileSync(generatedPath, generated);
    execFileSync(process.execPath, [
      script,
      "--tag", "v0.2.0",
      "--image", "ghcr.io/getsillage/sillage",
      "--digest", digest,
      "--existing", existingPath,
      "--generated", generatedPath,
      "--output", outputPath,
    ]);
    return readFileSync(outputPath, "utf8");
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}
