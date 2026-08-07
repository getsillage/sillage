#!/usr/bin/env node

import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "..");

const apacheLicense = {
  path: resolve(repoRoot, "third_party/licenses/android/apache-2.0/LICENSE.txt"),
  sha256: "50e6751797c50dedd75ef1b8a0d9e42f5f8472e9fbce91f34718e9f97b0c780a",
  title: "Apache License 2.0",
  url: "https://www.apache.org/licenses/LICENSE-2.0.txt",
};

const apacheCoordinatePrefixes = [
  "androidx.",
  "net.java.dev.jna:",
  "org.jetbrains.androidx.",
  "org.jetbrains.compose.",
  "org.jetbrains.kotlin:",
  "org.jetbrains.kotlinx:",
  "org.jetbrains.skiko:",
  "org.jetbrains:annotations:",
];

const targets = [
  {
    name: "Desktop",
    lockfile: resolve(repoRoot, "apps/native/desktopApp/gradle.lockfile"),
    output: resolve(repoRoot, "apps/native/desktopApp/src/main/resources/third_party_notices.txt"),
    configuration: "runtimeClasspath",
    scope: "desktop runtime",
  },
  {
    name: "iOS",
    lockfile: resolve(repoRoot, "apps/native/iosApp/gradle.lockfile"),
    output: resolve(repoRoot, "apps/native/iosApp/Sillage/ThirdPartyNotices.txt"),
    configuration: "iosArm64CompileKlibraries",
    scope: "iOS device compile",
  },
];

export function coordinatesForConfiguration(lockfileText, configuration) {
  return lockfileText
    .split("\n")
    .filter((line) => !line.startsWith("#") && line.includes("="))
    .filter((line) => line.slice(line.indexOf("=") + 1).split(",").includes(configuration))
    .map((line) => line.slice(0, line.indexOf("=")))
    .filter(Boolean)
    .sort();
}

export function assertSupportedNativeCoordinates(coordinates) {
  for (const coordinate of coordinates) {
    if (!apacheCoordinatePrefixes.some((prefix) => coordinate.startsWith(prefix))) {
      throw new Error(`Unsupported native notice license coordinate: ${coordinate}`);
    }
  }
}

export function renderNativeNotices({ name, lockfilePath, scope, coordinates, licenseText }) {
    const relativeLockfile = lockfilePath.replace(`${repoRoot}/`, "");
    const scopeLabel = scope.startsWith("iOS") ? scope : `${scope[0].toUpperCase()}${scope.slice(1)}`;
  const lines = [
    `Sillage ${name} - Open-source software notices`,
    "",
    `This file is generated from ${relativeLockfile} and is shipped with the ${name} application`,
    `so users can inspect the licenses of ${scope} dependencies. Do not edit it manually;`,
    "run `node scripts/generate-native-third-party-notices.mjs --write`.",
    "",
    `${scopeLabel} dependency count: ${coordinates.length}`,
    "",
    `===== ${apacheLicense.title} =====`,
    `Source: ${apacheLicense.url}`,
    "",
    "Packages:",
    ...coordinates.map((coordinate) => `- ${coordinate}`),
    "",
    licenseText,
    "",
  ];
  return `${lines.join("\n").replace(/\n+$/, "")}\n`;
}

function verifiedApacheLicense() {
  if (!existsSync(apacheLicense.path)) {
    throw new Error(`Missing reviewed license source: ${apacheLicense.path}`);
  }
  const content = readFileSync(apacheLicense.path);
  const digest = createHash("sha256").update(content).digest("hex");
  if (digest !== apacheLicense.sha256) {
    throw new Error(`Reviewed Apache license digest changed: expected ${apacheLicense.sha256}, got ${digest}`);
  }
  return content.toString("utf8").trimEnd();
}

export function generateNativeNotices({ write = false } = {}) {
  const licenseText = verifiedApacheLicense();
  const counts = new Map();

  for (const target of targets) {
    const coordinates = coordinatesForConfiguration(
      readFileSync(target.lockfile, "utf8"),
      target.configuration,
    );
    if (coordinates.length === 0) {
      throw new Error(`No ${target.scope} dependencies found in ${target.lockfile}`);
    }
    assertSupportedNativeCoordinates(coordinates);
    const generated = renderNativeNotices({
      name: target.name,
      lockfilePath: target.lockfile,
      scope: target.scope,
      coordinates,
      licenseText,
    });

    if (write) {
      mkdirSync(dirname(target.output), { recursive: true });
      writeFileSync(target.output, generated);
    } else if (!existsSync(target.output)) {
      throw new Error(`Missing generated ${target.name} notices: ${target.output}`);
    } else if (readFileSync(target.output, "utf8") !== generated) {
      throw new Error(
        `${target.name} third-party notices are stale; run with --write and review the result`,
      );
    }
    counts.set(target.name, coordinates.length);
  }

  return counts;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const counts = generateNativeNotices({ write: process.argv.includes("--write") });
  console.log(
    [...counts].map(([name, count]) => `${name}: ${count}`).join(", ") +
      " packaged third-party dependencies covered.",
  );
}
