#!/usr/bin/env node

import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

export function auditReleaseVersion({ tag, version }) {
  const pattern = /^v(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$/;
  if (!pattern.test(tag)) {
    return [`release tag must match vX.Y.Z or vX.Y.Z-prerelease: ${tag}`];
  }
  if (typeof version !== "string" || !pattern.test(`v${version}`)) {
    return [`Web package version must match X.Y.Z or X.Y.Z-prerelease: ${version}`];
  }
  if (version !== tag.slice(1)) {
    return [`Web package version ${version} does not match tag ${tag.slice(1)}`];
  }
  return [];
}

function main() {
  const tag = process.argv[2];
  if (!tag) {
    console.error("usage: check-release-version.mjs vX.Y.Z[-prerelease]");
    process.exit(2);
  }
  let version;
  try {
    ({ version } = JSON.parse(readFileSync("apps/web/package.json", "utf8")));
  } catch (error) {
    console.error(`release version check failed: cannot read Web package version: ${error.message}`);
    process.exit(1);
  }
  const failures = auditReleaseVersion({ tag, version });
  if (failures.length > 0) {
    console.error(`release version check failed: ${failures.join("; ")}`);
    process.exit(1);
  }
  console.log(`release version check passed: ${tag}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
