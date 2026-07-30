#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { pathToFileURL } from "node:url";

export const repositoryPolicies = [
  {
    name: "sillage",
    checks: [
      "Commits",
      "Secrets",
      "Go",
      "Proto",
      "Scale",
      "Upgrade",
      "Web",
      "Docs",
      "Container",
      "Supply Chain",
      "E2E",
      "Android",
      "Android Device (API 26)",
      "Android Device (API 35)",
      "CodeQL (go)",
      "CodeQL (javascript-typescript)",
      "CodeQL (java-kotlin)",
    ],
    dependabot: true,
  },
  { name: "website", checks: ["Check"], dependabot: true },
  { name: ".github", checks: [], dependabot: false },
];

export function auditRepositorySettings(snapshot, owner = "getsillage") {
  const failures = [];
  for (const policy of repositoryPolicies) {
    const data = snapshot.repositories?.[policy.name];
    const prefix = `${owner}/${policy.name}`;
    if (!data?.repository) {
      failures.push(`${prefix}: repository metadata is unavailable`);
      continue;
    }
    const security = data.repository.security_and_analysis || {};
    requireSecurityFeature(failures, prefix, security, "secret_scanning", "Secret Scanning");
    requireSecurityFeature(
      failures,
      prefix,
      security,
      "secret_scanning_push_protection",
      "Push Protection",
    );
    if (policy.dependabot) {
      requireSecurityFeature(
        failures,
        prefix,
        security,
        "dependabot_security_updates",
        "Dependabot security updates",
      );
    }

    const protection = data.protection;
    if (!protection) {
      failures.push(`${prefix}: main branch protection is missing`);
      continue;
    }
    if (protection.enforce_admins?.enabled !== true) {
      failures.push(`${prefix}: branch protection must enforce administrators`);
    }
    if (protection.allow_force_pushes?.enabled !== false) {
      failures.push(`${prefix}: force pushes must be disabled`);
    }
    if (protection.allow_deletions?.enabled !== false) {
      failures.push(`${prefix}: branch deletion must be disabled`);
    }

    if (policy.checks.length === 0) continue;
    const statusChecks = protection.required_status_checks;
    if (!statusChecks) {
      failures.push(`${prefix}: required status checks are missing`);
      continue;
    }
    if (statusChecks.strict !== true) {
      failures.push(`${prefix}: required status checks must be strict/up-to-date`);
    }
    const actual = new Set(
      (statusChecks.checks || []).map((check) => check.context).filter(Boolean),
    );
    for (const expected of policy.checks) {
      if (!actual.has(expected)) failures.push(`${prefix}: missing required status check ${expected}`);
    }
  }

  if (snapshot.vulnerabilityReporting?.enabled !== true) {
    failures.push("sillage: private vulnerability reporting must be enabled");
  }
  const pages = snapshot.pages;
  if (!pages) {
    failures.push("website: GitHub Pages settings are unavailable");
  } else {
    if (pages.https_enforced !== true) failures.push("website: GitHub Pages HTTPS must be enforced");
    if (pages.build_type !== "workflow") failures.push("website: GitHub Pages must deploy through Actions");
    if (pages.source?.branch !== "main") failures.push("website: GitHub Pages source branch must be main");
    if (pages.public !== true) failures.push("website: GitHub Pages site must be public");
  }
  return failures;
}

function requireSecurityFeature(failures, repository, security, key, label) {
  if (security[key]?.status !== "enabled") {
    failures.push(`${repository}: ${label} must be enabled`);
  }
}

function readRemoteSnapshot(owner) {
  const failures = [];
  const repositories = {};
  for (const policy of repositoryPolicies) {
    const prefix = `${owner}/${policy.name}`;
    repositories[policy.name] = {
      repository: ghJSON(`repos/${prefix}`, `read ${prefix} metadata`, failures),
      protection: ghJSON(
        `repos/${prefix}/branches/main/protection`,
        `read ${prefix} main protection`,
        failures,
        true,
      ),
    };
  }
  return {
    failures,
    snapshot: {
      repositories,
      vulnerabilityReporting: ghJSON(
        `repos/${owner}/sillage/private-vulnerability-reporting`,
        "read private vulnerability reporting",
        failures,
      ),
      pages: ghJSON(`repos/${owner}/website/pages`, "read website Pages settings", failures),
    },
  };
}

function ghJSON(endpoint, operation, failures, missingIsPolicyFailure = false) {
  try {
    return JSON.parse(
      execFileSync("gh", ["api", endpoint], {
        encoding: "utf8",
        stdio: ["ignore", "pipe", "pipe"],
      }),
    );
  } catch (error) {
    const stderr = error?.stderr?.toString().trim();
    if (!missingIsPolicyFailure || !stderr?.includes("Branch not protected")) {
      failures.push(`${operation}: ${stderr || error.message}`);
    }
    return null;
  }
}

function main() {
  const owner = process.env.SILLAGE_GITHUB_OWNER || "getsillage";
  const remote = readRemoteSnapshot(owner);
  const failures = [
    ...remote.failures,
    ...auditRepositorySettings(remote.snapshot, owner),
  ];
  if (failures.length > 0) {
    console.error("Repository settings audit failed:");
    for (const failure of failures) console.error(`- ${failure}`);
    process.exit(1);
  }
  console.log(`Repository settings audit passed for ${owner}/sillage, website, and .github.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
