import assert from "node:assert/strict";
import test from "node:test";

import {
  auditRepositorySettings,
  repositoryPolicies,
} from "./check-repository-settings.mjs";

test("accepts the complete release repository policy", () => {
  assert.deepEqual(auditRepositorySettings(completeSnapshot()), []);
});

test("reports missing security controls, branch protection, and required checks", () => {
  const snapshot = completeSnapshot();
  snapshot.repositories.sillage.repository.security_and_analysis.secret_scanning.status =
    "disabled";
  snapshot.repositories.sillage.protection.required_status_checks.checks = snapshot.repositories.sillage.protection.required_status_checks.checks.filter(
    (check) =>
      check.context !== "Supply Chain" &&
      check.context !== "Upgrade" &&
      check.context !== "Android Device (API 26)",
  );
  snapshot.repositories.website.repository.security_and_analysis.dependabot_security_updates.status =
    "disabled";
  snapshot.repositories[".github"].protection = null;
  snapshot.vulnerabilityReporting.enabled = false;
  snapshot.pages.https_enforced = false;

  const failures = auditRepositorySettings(snapshot);
  assert.ok(failures.includes("getsillage/sillage: Secret Scanning must be enabled"));
  assert.ok(failures.includes("getsillage/sillage: missing required status check Supply Chain"));
  assert.ok(failures.includes("getsillage/sillage: missing required status check Upgrade"));
  assert.ok(
    failures.includes("getsillage/sillage: missing required status check Android Device (API 26)"),
  );
  assert.ok(
    failures.includes("getsillage/website: Dependabot security updates must be enabled"),
  );
  assert.ok(failures.includes("getsillage/.github: main branch protection is missing"));
  assert.ok(failures.includes("sillage: private vulnerability reporting must be enabled"));
  assert.ok(failures.includes("website: GitHub Pages HTTPS must be enforced"));
});

function completeSnapshot() {
  const repositories = {};
  for (const policy of repositoryPolicies) {
    repositories[policy.name] = {
      repository: {
        security_and_analysis: {
          secret_scanning: { status: "enabled" },
          secret_scanning_push_protection: { status: "enabled" },
          dependabot_security_updates: { status: "enabled" },
        },
      },
      protection: {
        enforce_admins: { enabled: true },
        allow_force_pushes: { enabled: false },
        allow_deletions: { enabled: false },
        required_status_checks:
          policy.checks.length > 0
            ? {
                strict: true,
                checks: policy.checks.map((context) => ({ context })),
              }
            : null,
      },
    };
  }
  return {
    repositories,
    vulnerabilityReporting: { enabled: true },
    pages: {
      https_enforced: true,
      build_type: "workflow",
      source: { branch: "main" },
      public: true,
    },
  };
}
