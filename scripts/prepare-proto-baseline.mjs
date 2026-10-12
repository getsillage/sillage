#!/usr/bin/env node

import { existsSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

// This retirement is limited to one removed service file and two reserved
// message slots. Every retained descriptor still participates in FILE checks.
const servicePath = "api/v1/sync_service.proto";
const fields = [
  { path: "api/v1/auth_service.proto", message: "BootstrapResponse", number: 5, type: "int32" },
  { path: "api/v1/memo_service.proto", message: "CreateMemoRequest", number: 1, type: "string" },
];
const retirementFingerprints = {
  service: "082137ee13eda0911a3f5aad0166a8713e45f58c234eacd559244eda7c888c2a",
  "BootstrapResponse.5": "d6db553ad5170a51be7035953b2a2719ecbf238f4235f790ecd729ba185f6e06",
  "CreateMemoRequest.1": "e744a754c7776cbe0234c8ad01a1b8f4aa30a76b6adf75f507e323c696c7a971",
};

function fingerprint(value) {
  return createHash("sha256").update(value).digest("hex");
}

function messageBody(source, message) {
  const pattern = new RegExp(`(message ${message}\\s*\\{)([^}]*)(\\})`);
  return { pattern, match: pattern.exec(source) };
}

export function prepareProtoBaseline(baseline, current, fingerprints = retirementFingerprints) {
  const previousService = join(baseline, servicePath);
  if (!existsSync(previousService) || existsSync(join(current, servicePath))) return [];
  if (fingerprint(readFileSync(previousService)) !== fingerprints.service) return [];

  // Compile the current schema so comments, strings, and field options cannot
  // masquerade as wire reservations. Reuse must fail the ordinary FILE check.
  const image = JSON.parse(execFileSync("buf", [
    "build", current, "--as-file-descriptor-set", "-o", "-#format=json",
  ], { encoding: "utf8", maxBuffer: 16 * 1024 * 1024 }));
  for (const { path, message, number } of fields) {
    const descriptor = image.file.find((file) => file.name === path)
      ?.messageType?.find((candidate) => candidate.name === message);
    if (!descriptor || descriptor.field?.some((field) => field.number === number)) return [];
    if (!descriptor.reservedRange?.some(({ start, end }) => start <= number && number < end)) return [];
  }

  const updates = [];
  for (const { path, message, number, type } of fields) {
    const filename = join(baseline, path);
    const source = readFileSync(filename, "utf8");
    const { pattern, match } = messageBody(source, message);
    if (!match) return [];
    const field = new RegExp(`^\\s*${type}\\s+\\w+\\s*=\\s*${number}\\s*;[^\\S\\n]*$`, "m");
    const declaration = field.exec(match[2]);
    const key = `${message}.${number}`;
    if (!declaration || fingerprint(declaration[0].trim()) !== fingerprints[key]) return [];
    updates.push({ filename, key, source: source.replace(pattern, (_, start, body, end) =>
      `${start}${body.replace(field, "")}${end}`,
    ) });
  }
  for (const { filename, source } of updates) writeFileSync(filename, source);
  unlinkSync(previousService);
  return [...updates.map(({ key }) => key), servicePath];
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [baseline, current] = process.argv.slice(2);
  if (!baseline || !current) {
    console.error("usage: prepare-proto-baseline.mjs <baseline-module> <current-module>");
    process.exit(2);
  }
  const changed = prepareProtoBaseline(baseline, current);
  if (changed.length > 0) {
    console.log(`Applied the explicit contract retirement to the temporary baseline: ${changed.join(", ")}`);
  }
}
