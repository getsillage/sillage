#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

export function auditReleaseNotes({ tag, notes, build, previousTag = "", release = false }) {
  const failures = [];
  const parsedTag = parseTag(tag);
  if (!parsedTag) {
    return [`release tag must match vX.Y.Z or vX.Y.Z-prerelease: ${tag}`];
  }
  const versionName = match(build, /versionName\s*=\s*"([^"]+)"/, "versionName", failures);
  const versionCode = match(build, /versionCode\s*=\s*(\d+)/, "versionCode", failures);
  const minSdk = match(build, /minSdk\s*=\s*(\d+)/, "minSdk", failures);
  const targetSdk = match(build, /targetSdk\s*=\s*(\d+)/, "targetSdk", failures);
  if (versionName && versionName !== parsedTag.version) {
    failures.push(`Android versionName ${versionName} does not match ${tag}`);
  }

  const requiredSections = ["主要更新", "兼容性与升级", "已知限制", "验证"];
  for (const section of requiredSections) {
    const body = sectionBody(notes, section);
    if (body === null) {
      failures.push(`release notes are missing section: ${section}`);
    } else if (body.trim().length < 40) {
      failures.push(`release notes section is too short: ${section}`);
    }
  }

  for (const [label, value] of [
    ["versionName", versionName],
    ["versionCode", versionCode],
    ["minSdk", minSdk],
    ["targetSdk", targetSdk],
  ]) {
    if (value && !notes.includes(`${label} \`${value}\``)) {
      failures.push(`release notes must state Android ${label} ${value}`);
    }
  }

  for (const phrase of ["完整备份", "回滚", "API 26", "API 35", "物理设备"]) {
    if (!notes.includes(phrase)) failures.push(`release notes must mention ${phrase}`);
  }
  if (previousTag) {
    const compareURL = `https://github.com/getsillage/sillage/compare/${previousTag}...${tag}`;
    if (!notes.includes(compareURL)) {
      failures.push(`release notes must link the full comparison: ${compareURL}`);
    }
  }
  if (notes.includes("<!-- sillage-install:")) {
    failures.push("curated release notes must not contain the generated install block");
  }
  if (release && /(?:待完成|\bPENDING\b|\bTODO\b|\bTBD\b)/i.test(notes)) {
    failures.push("release notes still contain pending acceptance evidence");
  }
  return failures;
}

function sectionBody(notes, heading) {
  const pattern = new RegExp(`^## ${escapeRegExp(heading)}\\s*$`, "m");
  const match = pattern.exec(notes);
  if (!match) return null;
  const start = match.index + match[0].length;
  const rest = notes.slice(start);
  const next = /^##\s+/m.exec(rest);
  return next ? rest.slice(0, next.index) : rest;
}

function match(text, pattern, label, failures) {
  const result = pattern.exec(text);
  if (!result) {
    failures.push(`Android build config is missing ${label}`);
    return "";
  }
  return result[1];
}

function parseTag(value) {
  const result = /^v(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$/.exec(value);
  if (!result) return null;
  return {
    tag: value,
    version: value.slice(1),
    major: Number(result[1]),
    minor: Number(result[2]),
    patch: Number(result[3]),
    prerelease: result[4] ?? "",
  };
}

function compareVersion(left, right) {
  for (const key of ["major", "minor", "patch"]) {
    if (left[key] !== right[key]) return left[key] - right[key];
  }
  if (!left.prerelease && right.prerelease) return 1;
  if (left.prerelease && !right.prerelease) return -1;
  return left.prerelease.localeCompare(right.prerelease, "en", { numeric: true });
}

function previousStableTag(current) {
  return execFileSync("git", ["tag", "--list", "v*"], { encoding: "utf8" })
    .split(/\r?\n/)
    .map((value) => parseTag(value.trim()))
    .filter((candidate) => candidate && !candidate.prerelease && compareVersion(candidate, current) < 0)
    .sort((left, right) => compareVersion(right, left))[0]?.tag ?? "";
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function main() {
  const build = readFileSync("apps/native/androidApp/build.gradle.kts", "utf8");
  const configuredVersion = match(build, /versionName\s*=\s*"([^"]+)"/, "versionName", []);
  const tag = process.argv.slice(2).find((value) => !value.startsWith("--")) || `v${configuredVersion}`;
  const parsedTag = parseTag(tag);
  const notesPath = `.github/release-notes/${tag}.md`;
  let notes = "";
  try {
    notes = readFileSync(notesPath, "utf8");
  } catch (error) {
    console.error(`release notes are missing for ${tag}: ${error.message}`);
    process.exit(1);
  }
  const failures = auditReleaseNotes({
    tag,
    notes,
    build,
    previousTag: parsedTag ? previousStableTag(parsedTag) : "",
    release: process.argv.includes("--release"),
  });
  if (failures.length > 0) {
    console.error(`Release notes check failed for ${tag}:`);
    for (const failure of failures) console.error(`- ${failure}`);
    process.exit(1);
  }
  console.log(`Release notes verified for ${tag}${process.argv.includes("--release") ? " (publishable)" : " (candidate)"}.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
