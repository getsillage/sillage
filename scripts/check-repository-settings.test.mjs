import assert from "node:assert/strict";
import test from "node:test";

import {
  auditRepositorySettings,
  repositoryPolicies,
} from "./check-repository-settings.mjs";

test("accepts the complete release repository policy", () => {
  assert.deepEqual(auditRepositorySettings(completeSnapshot()), []);
});

test("reports organization, repository, security, and branch protection drift", () => {
  const snapshot = completeSnapshot();
  snapshot.organization.two_factor_requirement_enabled = false;
  snapshot.organization.members_can_delete_repositories = true;
  snapshot.organizationActions.sha_pinning_required = false;
  snapshot.organizationArtifactRetention.days = 90;
  snapshot.repositories.sillage.repository.security_and_analysis.secret_scanning.status =
    "disabled";
  snapshot.repositories.sillage.repository.allow_merge_commit = true;
  snapshot.repositories.sillage.actions.sha_pinning_required = false;
  snapshot.repositories.sillage.workflowPermissions.default_workflow_permissions = "write";
  snapshot.repositories.sillage.protection.required_linear_history.enabled = false;
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
  assert.ok(
    failures.includes("getsillage: two-factor authentication requirement must be true"),
  );
  assert.ok(failures.includes("getsillage: member repository deletion must be false"));
  assert.ok(
    failures.includes(
      "getsillage: organization Actions must require full-length commit SHA pins",
    ),
  );
  assert.ok(
    failures.includes(
      "getsillage: Actions artifacts and logs must be retained for at most 30 days",
    ),
  );
  assert.ok(failures.includes("getsillage/sillage: Secret Scanning must be enabled"));
  assert.ok(failures.includes("getsillage/sillage: merge commits must be false"));
  assert.ok(
    failures.includes("getsillage/sillage: Actions must require full-length commit SHA pins"),
  );
  assert.ok(
    failures.includes("getsillage/sillage: default workflow permissions must be read-only"),
  );
  assert.ok(failures.includes("getsillage/sillage: main must require linear history"));
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
        description: policy.description,
        homepage: "https://getsillage.github.io/website/",
        has_issues: policy.issues,
        has_projects: false,
        has_wiki: false,
        has_discussions: false,
        allow_squash_merge: true,
        allow_merge_commit: false,
        allow_rebase_merge: false,
        allow_auto_merge: true,
        delete_branch_on_merge: true,
        allow_update_branch: true,
        use_squash_pr_title_as_default: true,
        security_and_analysis: {
          secret_scanning: { status: "enabled" },
          secret_scanning_push_protection: { status: "enabled" },
          dependabot_security_updates: { status: "enabled" },
        },
      },
      actions: { sha_pinning_required: true },
      workflowPermissions: {
        default_workflow_permissions: "read",
        can_approve_pull_request_reviews: false,
      },
      protection: {
        enforce_admins: { enabled: true },
        allow_force_pushes: { enabled: false },
        allow_deletions: { enabled: false },
        required_linear_history: { enabled: true },
        required_conversation_resolution: { enabled: true },
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
    organization: {
      two_factor_requirement_enabled: true,
      default_repository_permission: "read",
      members_can_create_repositories: false,
      members_can_create_pages: false,
      members_can_delete_repositories: false,
      members_can_change_repo_visibility: false,
      members_can_create_teams: false,
      readers_can_create_discussions: false,
      dependency_graph_enabled_for_new_repositories: true,
      dependabot_alerts_enabled_for_new_repositories: true,
      dependabot_security_updates_enabled_for_new_repositories: true,
      secret_scanning_enabled_for_new_repositories: true,
      secret_scanning_push_protection_enabled_for_new_repositories: true,
    },
    organizationActions: { sha_pinning_required: true },
    organizationWorkflowPermissions: {
      default_workflow_permissions: "read",
      can_approve_pull_request_reviews: false,
    },
    organizationArtifactRetention: { days: 30 },
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
