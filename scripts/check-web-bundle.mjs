#!/usr/bin/env node

import { gzipSync } from "node:zlib";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { basename, join, resolve } from "node:path";

const dist = resolve(process.argv[2] || "server/router/frontend/dist");
const assets = join(dist, "assets");
const index = readFileSync(join(dist, "index.html"), "utf8");
const entryScripts = [
  ...index.matchAll(/<script\b[^>]*\bsrc=["']([^"']+)["'][^>]*>/g),
]
  .map((match) => match[1])
  .filter((source) => source.includes("/assets/") && source.endsWith(".js"))
  .map((source) => basename(source));

if (entryScripts.length !== 1) {
  fail(`expected exactly one entry script in index.html, found ${entryScripts.length}`);
}

const files = readdirSync(assets).filter((name) => statSync(join(assets, name)).isFile());
const jsFiles = files.filter((name) => name.endsWith(".js"));
const cssFiles = files.filter((name) => name.endsWith(".css"));
const entry = entryScripts[0];

checkFile(entry, 430 * 1024, 140 * 1024, "entry JavaScript");
for (const css of cssFiles) {
  checkFile(css, 85 * 1024, 20 * 1024, "stylesheet");
}
for (const file of jsFiles) {
  if (file !== entry) {
    checkFile(file, 180 * 1024, 55 * 1024, "lazy JavaScript chunk");
  }
}

for (const route of ["AskPage-", "EntryPage-", "HomePage-", "SettingsPage-", "TimelinePage-"]) {
  if (!jsFiles.some((name) => name.startsWith(route))) {
    fail(`missing route-split chunk with prefix ${route}`);
  }
}

console.log(
  `Web bundle budgets passed: ${jsFiles.length} JS chunks, ${cssFiles.length} CSS file(s), entry ${formatSize(statSync(join(assets, entry)).size)}.`,
);

function checkFile(name, rawLimit, gzipLimit, kind) {
  const path = join(assets, name);
  const bytes = readFileSync(path);
  const raw = bytes.length;
  const gzip = gzipSync(bytes, { level: 9 }).length;
  if (raw > rawLimit) {
    fail(`${kind} ${name} is ${formatSize(raw)}; limit is ${formatSize(rawLimit)}`);
  }
  if (gzip > gzipLimit) {
    fail(`${kind} ${name} is ${formatSize(gzip)} gzip; limit is ${formatSize(gzipLimit)}`);
  }
}

function formatSize(bytes) {
  return `${(bytes / 1024).toFixed(1)} KiB`;
}

function fail(message) {
  console.error(`Web bundle budget failed: ${message}`);
  process.exit(1);
}
