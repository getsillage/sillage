#!/usr/bin/env node

import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "..");
const lockfile = resolve(repoRoot, "apps/native/androidApp/gradle.lockfile");
const output = resolve(repoRoot, "apps/native/androidApp/src/main/res/raw/third_party_notices.txt");

const licenseSources = {
  apache: {
    path: resolve(repoRoot, "third_party/licenses/android/apache-2.0/LICENSE.txt"),
    sha256: "50e6751797c50dedd75ef1b8a0d9e42f5f8472e9fbce91f34718e9f97b0c780a",
    title: "Apache License 2.0",
    url: "https://www.apache.org/licenses/LICENSE-2.0.txt",
  },
  bsd2: {
    path: resolve(repoRoot, "third_party/licenses/android/commonmark-java@0.13.0/LICENSE.txt"),
    sha256: "4c1ea7a8104cd00a7e62b794cc317aedca2e68c647048b5f546fc7388dddd306",
    title: "BSD 2-Clause License",
    url: "https://github.com/atlassian/commonmark-java/tree/commonmark-parent-0.13.0",
  },
  mpl2: {
    path: resolve(repoRoot, "third_party/licenses/android/mpl-2.0/LICENSE.txt"),
    sha256: "3f3d9e0024b1921b067d6f7f88deb4a60cbe7a78e76c64e3f1d7fc3b779b9d04",
    title: "Mozilla Public License 2.0",
    url: "https://www.mozilla.org/MPL/2.0/",
  },
};

const packageLicense = (coordinate) => {
  if (coordinate.startsWith("com.atlassian.commonmark:")) return "bsd2";
  if (
    coordinate.startsWith("androidx.") ||
    coordinate.startsWith("com.google.guava:") ||
    coordinate.startsWith("com.squareup.okhttp3:") ||
    coordinate.startsWith("com.squareup.okio:") ||
    coordinate.startsWith("io.noties.markwon:") ||
    coordinate.startsWith("org.jetbrains.kotlin:") ||
    coordinate.startsWith("org.jetbrains.kotlinx:") ||
    coordinate.startsWith("org.jetbrains:annotations:")
  ) {
    return "apache";
  }
  throw new Error(`Android dependency has no reviewed license policy: ${coordinate}`);
};

const sha256 = (value) => createHash("sha256").update(value).digest("hex");
for (const [name, source] of Object.entries(licenseSources)) {
  if (!existsSync(source.path)) throw new Error(`Missing Android license source (${name}): ${source.path}`);
  const content = readFileSync(source.path);
  const actual = sha256(content);
  if (actual !== source.sha256) {
    throw new Error(`Android license source checksum mismatch (${name}): expected ${source.sha256}, got ${actual}`);
  }
}

const okhttpNoticePath = resolve(repoRoot, "third_party/licenses/android/okhttp@4.12.0/publicsuffix-NOTICE");
const okhttpNoticeSha256 = "8a9c58fbded5ea3474315e4a9824ca8b1486098a1a03e897e0b19419d5e1876a";
const okhttpNotice = readFileSync(okhttpNoticePath);
if (sha256(okhttpNotice) !== okhttpNoticeSha256) {
  throw new Error("OkHttp public-suffix notice checksum mismatch");
}

const coordinates = readFileSync(lockfile, "utf8")
  .split(/\r?\n/)
  .filter((line) => line && !line.startsWith("#") && /(^|[,=])releaseRuntimeClasspath([,]|$)/.test(line))
  .map((line) => line.slice(0, line.indexOf("=")))
  .sort();

if (coordinates.length === 0) throw new Error("Android releaseRuntimeClasspath lock is empty");
const grouped = new Map(Object.keys(licenseSources).map((name) => [name, []]));
for (const coordinate of coordinates) grouped.get(packageLicense(coordinate)).push(coordinate);

const readLicense = (name) => readFileSync(licenseSources[name].path, "utf8").trimEnd();
const lines = [
  "Sillage Android — Open-source software notices",
  "",
  "This file is generated from apps/native/androidApp/gradle.lockfile and is shipped in the APK",
  "so users can inspect the licenses of release-runtime dependencies. Do not edit it",
  "manually; run `node scripts/generate-android-third-party-notices.mjs --write`.",
  "",
  `Release-runtime dependency count: ${coordinates.length}`,
  "",
];

for (const name of ["apache", "bsd2"]) {
  const source = licenseSources[name];
  lines.push(`===== ${source.title} =====`, `Source: ${source.url}`, "", "Packages:", ...grouped.get(name).map((coordinate) => `- ${coordinate}`), "", readLicense(name), "");
}

if (coordinates.some((coordinate) => coordinate.startsWith("com.squareup.okhttp3:okhttp:"))) {
  const notice = okhttpNotice.toString("utf8").trimEnd();
  lines.push(
    "===== Additional OkHttp bundled-data notice =====",
    "The OkHttp public suffix data is compiled from the Public Suffix List and is subject to the Mozilla Public License 2.0.",
    "",
    notice,
    "",
    `===== ${licenseSources.mpl2.title} =====`,
    `Source: ${licenseSources.mpl2.url}`,
    "",
    readLicense("mpl2"),
    "",
  );
}

const generated = `${lines.join("\n").replace(/\n+$/, "")}\n`;
if (process.argv.includes("--write")) {
  mkdirSync(dirname(output), { recursive: true });
  writeFileSync(output, generated);
} else {
  if (!existsSync(output)) throw new Error(`Missing generated Android notices: ${output}`);
  const existing = readFileSync(output, "utf8");
  if (existing !== generated) {
    throw new Error("Android third-party notices are stale; run with --write and review the result");
  }
}

console.log(`Android third-party notices cover ${coordinates.length} release-runtime dependencies.`);
