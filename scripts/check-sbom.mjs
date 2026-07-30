#!/usr/bin/env node

import { readFileSync } from "node:fs";

const [spdxPath, cyclonedxPath, grypePath] = process.argv.slice(2);
if (!spdxPath || !cyclonedxPath || !grypePath) {
  console.error(
    "Usage: node scripts/check-sbom.mjs <spdx.json> <cyclonedx.json> <grype.json>",
  );
  process.exit(2);
}

function readJson(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch (error) {
    throw new Error(`Could not read valid JSON from ${file}: ${error}`);
  }
}

const spdx = readJson(spdxPath);
if (
  spdx.spdxVersion !== "SPDX-2.3" ||
  typeof spdx.documentNamespace !== "string" ||
  !Array.isArray(spdx.packages) ||
  spdx.packages.length === 0
) {
  throw new Error("SPDX SBOM is missing required fields or contains no packages");
}

const cyclonedx = readJson(cyclonedxPath);
if (
  cyclonedx.bomFormat !== "CycloneDX" ||
  !cyclonedx.specVersion ||
  !Array.isArray(cyclonedx.components) ||
  cyclonedx.components.length === 0
) {
  throw new Error(
    "CycloneDX SBOM is missing required fields or contains no components",
  );
}

const grype = readJson(grypePath);
if (!Array.isArray(grype.matches) || typeof grype.descriptor !== "object") {
  throw new Error("Grype report is missing matches or scanner descriptor");
}

const severityCounts = {};
for (const match of grype.matches) {
  const severity = match.vulnerability?.severity || "Unknown";
  severityCounts[severity] = (severityCounts[severity] || 0) + 1;
}

console.log(
  `SBOM verified: SPDX packages=${spdx.packages.length}, CycloneDX components=${cyclonedx.components.length}; Grype matches=${grype.matches.length} (${Object.entries(severityCounts)
    .map(([severity, count]) => `${severity}=${count}`)
    .join(", ") || "none"}).`,
);
