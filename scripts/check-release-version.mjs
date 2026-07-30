#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";

const tag = process.argv[2];
if (!tag) {
  console.error("usage: check-release-version.mjs vX.Y.Z[-prerelease]");
  process.exit(2);
}

const parsed = parseTag(tag);
if (!parsed) {
  fail(`release tag must match vX.Y.Z or vX.Y.Z-prerelease: ${tag}`);
}

const currentBuild = readFile("android/app/build.gradle.kts");
const currentVersionName = matchRequired(currentBuild, /versionName\s*=\s*"([^"]+)"/, "versionName");
const currentVersionCode = Number(matchRequired(currentBuild, /versionCode\s*=\s*(\d+)/, "versionCode"));
if (!Number.isInteger(currentVersionCode) || currentVersionCode <= 0) {
  fail(`Android versionCode must be a positive integer: ${currentVersionCode}`);
}
const buildInfo = readFile("server/build_info.go");
const minimumAndroidVersionCode = Number(
  matchRequired(
    buildInfo,
    /minimumAndroidVersionCode\s+int32\s*=\s*(\d+)/,
    "minimumAndroidVersionCode",
  ),
);
if (
  !Number.isInteger(minimumAndroidVersionCode) ||
  minimumAndroidVersionCode <= 0 ||
  minimumAndroidVersionCode > currentVersionCode
) {
  fail(
    `minimum Android versionCode ${minimumAndroidVersionCode} must be positive and no greater than the released versionCode ${currentVersionCode}`,
  );
}
if (currentVersionName !== parsed.version) {
  fail(`Android versionName ${currentVersionName} does not match tag ${parsed.version}`);
}

let highestPrevious = null;
for (const previous of previousReleases(parsed)) {
  const previousBuild = readFileAtTag(previous.tag, "android/app/build.gradle.kts");
  const previousCode = Number(matchRequired(previousBuild, /versionCode\s*=\s*(\d+)/, `versionCode from ${previous.tag}`));
  if (!Number.isInteger(previousCode) || previousCode <= 0) {
    fail(`Android versionCode from ${previous.tag} must be a positive integer: ${previousCode}`);
  }
  if (!highestPrevious || previousCode > highestPrevious.code) {
    highestPrevious = { tag: previous.tag, code: previousCode };
  }
}
if (highestPrevious && currentVersionCode <= highestPrevious.code) {
  fail(`Android versionCode ${currentVersionCode} must be greater than ${highestPrevious.code} from ${highestPrevious.tag}`);
}

console.log(`release version check passed: ${tag} (Android ${currentVersionName}, versionCode ${currentVersionCode})`);

function parseTag(value) {
  const match = /^v(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$/.exec(value);
  if (!match) return null;
  return {
    tag: value,
    version: value.slice(1),
    major: Number(match[1]),
    minor: Number(match[2]),
    patch: Number(match[3]),
    prerelease: match[4] ?? "",
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

function previousReleases(current) {
  return execFileSync("git", ["tag", "--list", "v*"], { encoding: "utf8" })
    .split(/\r?\n/)
    .map((value) => parseTag(value.trim()))
    .filter(Boolean)
    .filter((candidate) => candidate.tag !== current.tag && compareVersion(candidate, current) < 0)
    .sort((left, right) => compareVersion(right, left));
}

function readFile(path) {
  try {
    return readFileSync(path, "utf8");
  } catch (error) {
    fail(`cannot read ${path}: ${error.message}`);
  }
}

function readFileAtTag(tagName, path) {
  try {
    return execFileSync("git", ["show", `${tagName}:${path}`], { encoding: "utf8" });
  } catch (error) {
    fail(`cannot read ${path} from ${tagName}: ${error.message}`);
  }
}

function matchRequired(text, pattern, name) {
  const match = pattern.exec(text);
  if (!match) fail(`missing Android ${name}`);
  return match[1];
}

function fail(message) {
  console.error(`release version check failed: ${message}`);
  process.exit(1);
}
