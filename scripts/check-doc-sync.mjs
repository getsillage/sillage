#!/usr/bin/env node
/**
 * When contract-sensitive paths change, require a documentation path in the same range
 * unless the commit messages include Docs-skip: <reason>.
 *
 *   BASE_SHA=<ref> node scripts/check-doc-sync.mjs
 *   node scripts/check-doc-sync.mjs --base <ref>
 */
import {
  changedFiles,
  loadChangeMatrix,
  matchGlob,
  runGit,
} from "./lib/repo.mjs";

const args = process.argv.slice(2);
const baseIdx = args.indexOf("--base");
const baseRef =
  baseIdx >= 0
    ? args[baseIdx + 1]
    : process.env.BASE_SHA || process.env.BASE_REF || "";

// Working-tree-only runs without a base are advisory (print and pass) so local
// partial edits are not blocked mid-change. CI must pass BASE_SHA.
if (!baseRef) {
  console.log("check-doc-sync: no BASE_SHA/BASE_REF; skipping hard enforcement.");
  process.exit(0);
}

const files = changedFiles(baseRef);
const matrix = loadChangeMatrix();
const failures = [];

let allowSkip = false;
try {
  const messages = runGit(["log", "--format=%B", `${baseRef}..HEAD`]);
  allowSkip = /^Docs-skip:\s+\S+/im.test(messages);
} catch {
  allowSkip = false;
}

if (allowSkip) {
  console.log("check-doc-sync: Docs-skip found in commit range; skipping enforcement.");
  process.exit(0);
}

for (const rule of matrix.rules || []) {
  const docs = rule.docs || [];
  if (docs.length === 0) continue;

  const codeHits = files.filter((file) =>
    (rule.paths || []).some((pattern) => matchGlob(file, pattern)),
  );
  if (codeHits.length === 0) continue;

  // If only documentation paths under this rule changed, no extra docs required.
  const nonDocHits = codeHits.filter(
    (file) => !docs.some((pattern) => matchGlob(file, pattern)),
  );
  if (nonDocHits.length === 0) continue;

  const docHits = files.filter((file) => docs.some((pattern) => matchGlob(file, pattern)));
  if (docHits.length === 0) {
    failures.push(
      [
        `rule ${rule.id || "(anonymous)"}: contract paths changed without documentation updates.`,
        `  code: ${nonDocHits.slice(0, 8).join(", ")}${nonDocHits.length > 8 ? ", ..." : ""}`,
        `  expected one of: ${docs.join(", ")}`,
        `  or include "Docs-skip: <reason>" in a commit message in this range.`,
      ].join("\n"),
    );
  }
}

if (failures.length > 0) {
  console.error(failures.join("\n\n"));
  process.exit(1);
}

console.log(`Doc-sync checks passed for ${files.length} changed file(s) against ${baseRef}.`);
