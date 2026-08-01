#!/usr/bin/env node

import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

export function auditAndroidDeviceMatrix({ gradle, ci, release, repositorySettings }) {
  const failures = [];
  const minSdk = readSdk(gradle, "minSdk", failures);
  const targetSdk = readSdk(gradle, "targetSdk", failures);
  const job = readWorkflowJob(ci, "android-device", failures);
  const apiLevels = readApiLevels(job, failures);

  if (job && !job.includes('name: Android Device (API ${{ matrix.api-level }})')) {
    failures.push("Android device CI job must expose the API level in its job name");
  }
  if (job && !job.includes('api-level: ${{ matrix.api-level }}')) {
    failures.push("Android emulator runner must use the API level from the device matrix");
  }
  if (job && /^    if:/m.test(job)) {
    failures.push(
      "Android device matrix must not use a job-level condition because skipped matrices lose their required API-specific check names",
    );
  }
  if (job && !job.includes("Skip unaffected Android device gate")) {
    failures.push("Android device matrix must expose a no-op path for unaffected pull requests");
  }

  if (minSdk && targetSdk && apiLevels.length > 0) {
    const expected = [...new Set([minSdk, targetSdk])].sort((a, b) => a - b);
    const actual = [...new Set(apiLevels)].sort((a, b) => a - b);
    if (actual.length !== expected.length || actual.some((value, index) => value !== expected[index])) {
      failures.push(
        `Android device matrix must contain exactly the min/target SDK boundaries (${expected.join(", ")}); found ${actual.join(", ") || "none"}`,
      );
    }

    for (const apiLevel of expected) {
      const jobName = `Android Device (API ${apiLevel})`;
      if (!release.includes(`"${jobName}"`)) {
        failures.push(`Release preflight must require successful job ${jobName}`);
      }
      if (!repositorySettings.includes(`"${jobName}"`)) {
        failures.push(`Repository settings policy must require status check ${jobName}`);
      }
    }
  }

  return { minSdk, targetSdk, apiLevels, failures };
}

function readSdk(gradle, name, failures) {
  const match = gradle.match(new RegExp(`\\b${name}\\s*=\\s*(\\d+)`));
  if (!match) {
    failures.push(`Android build config is missing a literal ${name}`);
    return null;
  }
  return Number(match[1]);
}

function readWorkflowJob(workflow, jobId, failures) {
  const lines = workflow.split(/\r?\n/);
  const start = lines.findIndex((line) => line === `  ${jobId}:`);
  if (start < 0) {
    failures.push(`CI workflow is missing the ${jobId} job`);
    return "";
  }
  let end = lines.length;
  for (let index = start + 1; index < lines.length; index += 1) {
    if (/^  [A-Za-z0-9_-]+:$/.test(lines[index])) {
      end = index;
      break;
    }
  }
  return lines.slice(start, end).join("\n");
}

function readApiLevels(job, failures) {
  if (!job) return [];
  const match = job.match(/^\s+api-level:\s*\[([^\]]+)\]\s*$/m);
  if (!match) {
    failures.push("Android device CI job is missing an inline api-level matrix");
    return [];
  }
  const values = match[1]
    .split(",")
    .map((value) => Number(value.trim()))
    .filter(Number.isInteger);
  if (values.length === 0 || new Set(values).size !== values.length) {
    failures.push("Android device api-level matrix must contain unique integer values");
  }
  return values;
}

function main() {
  const result = auditAndroidDeviceMatrix({
    gradle: readFileSync(new URL("../apps/native/androidApp/build.gradle.kts", import.meta.url), "utf8"),
    ci: readFileSync(new URL("../.github/workflows/ci.yml", import.meta.url), "utf8"),
    release: readFileSync(new URL("../.github/workflows/release.yml", import.meta.url), "utf8"),
    repositorySettings: readFileSync(
      new URL("./check-repository-settings.mjs", import.meta.url),
      "utf8",
    ),
  });
  if (result.failures.length > 0) {
    console.error("Android device matrix check failed:");
    for (const failure of result.failures) console.error(`- ${failure}`);
    process.exit(1);
  }
  console.log(
    `Android device matrix verified: API ${result.apiLevels.join(", API ")} (minSdk ${result.minSdk}, targetSdk ${result.targetSdk}).`,
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
