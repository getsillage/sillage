#!/usr/bin/env node
/**
 * Resolve verification gates for changed paths.
 *
 * Usage:
 *   node scripts/affected.mjs                 # print gates for working tree vs HEAD
 *   node scripts/affected.mjs --base <ref>    # print gates for base...HEAD
 *   node scripts/affected.mjs --json
 *   node scripts/affected.mjs --run           # run matching make targets (requires make)
 */
import { spawnSync } from "node:child_process";
import {
  changedFiles,
  loadChangeMatrix,
  resolveGates,
  root,
} from "./lib/repo.mjs";

const args = process.argv.slice(2);
const json = args.includes("--json");
const run = args.includes("--run");
const baseIdx = args.indexOf("--base");
const baseRef =
  baseIdx >= 0
    ? args[baseIdx + 1]
    : process.env.BASE_SHA || process.env.BASE_REF || "";

const matrix = loadChangeMatrix();
const files = changedFiles(baseRef || undefined);
const { gates, matchedRules } = resolveGates(files, matrix);

if (json) {
  process.stdout.write(
    `${JSON.stringify({ baseRef: baseRef || null, files, matchedRules, gates }, null, 2)}\n`,
  );
} else {
  console.log(`base: ${baseRef || "(working tree)"}`);
  console.log(`files: ${files.length}`);
  if (matchedRules.length > 0) {
    console.log(`rules: ${matchedRules.join(", ")}`);
  }
  console.log(`gates: ${gates.join(" ") || "(none)"}`);
  if (files.length > 0 && files.length <= 40) {
    for (const file of files) {
      console.log(`  - ${file}`);
    }
  } else if (files.length > 40) {
    console.log(`  (${files.length} files; use --json for the full list)`);
  }
}

if (!run) {
  process.exit(0);
}

const targetByGate = {
  go: "check-go",
  proto: "check-proto",
  web: "check-web",
  android: "check-android",
  scale: "check-scale",
  docs: "check-docs",
  container: "check-container",
  "supply-chain": "check-supply-chain",
  e2e: "check-e2e",
  restore: "check-restore",
  upgrade: "check-upgrade",
};

const targets = gates.map((gate) => targetByGate[gate]).filter(Boolean);
if (targets.length === 0) {
  console.error("No make targets resolved for affected gates.");
  process.exit(1);
}

console.log(`\nRunning: make ${targets.join(" ")}`);
const result = spawnSync("make", targets, { cwd: root, stdio: "inherit" });
process.exit(result.status ?? 1);
