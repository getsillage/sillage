#!/usr/bin/env node
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

const workflowDir = ".github/workflows";
const shaPattern = /^[0-9a-f]{40}$/;
const failures = [];

for (const name of readdirSync(workflowDir).filter((item) => item.endsWith(".yml") || item.endsWith(".yaml"))) {
  const path = join(workflowDir, name);
  const lines = readFileSync(path, "utf8").split(/\r?\n/);
  lines.forEach((line, index) => {
    const match = line.match(/^\s*uses:\s*([^\s#]+)@([^\s#]+)/);
    if (match && !shaPattern.test(match[2])) {
      failures.push(`${path}:${index + 1}: action ${match[1]} is not pinned to a full commit SHA`);
    }
  });
}

if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exit(1);
}

console.log("GitHub Actions pin checks passed.");
