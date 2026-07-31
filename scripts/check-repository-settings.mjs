#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { pathToFileURL } from "node:url";

export const repositoryPolicies = [
  {
    name: "sillage",
    description:
      "Self-hosted, single-user space for private records, history review, and AI answers grounded in your own notes.",
    issues: true,
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
  {
    name: "website",
    description: "Bilingual product website for Sillage, with product guidance and a Docker quick start.",
    issues: false,
    checks: ["Check"],
    dependabot: true,
  },
  {
    name: ".github",
    description: "Organization profile, shared community health files, and Sillage brand standards.",
    issues: false,
    checks: [],
    dependabot: false,
  },
];

const productHomepage = "https://getsillage.github.io/website/";

export function auditRepositorySettings(snapshot, owner = "getsillage") {
  const failures = [];
  auditOrganizationSettings(snapshot, failures, owner);

  for (const policy of repositoryPolicies) {
    const data = snapshot.repositories?.[policy.name];
    const prefix = `${owner}/${policy.name}`;
    if (!data?.repository) {
      failures.push(`${prefix}: repository metadata is unavailable`);
      continue;
    }
    const repository = data.repository;
    requireEqual(failures, prefix, repository, "description", policy.description, "description");
    requireEqual(failures, prefix, repository, "homepage", productHomepage, "homepage");
    requireEqual(failures, prefix, repository, "has_issues", policy.issues, "Issues setting");
    requireEqual(failures, prefix, repository, "has_projects", false, "Projects setting");
    requireEqual(failures, prefix, repository, "has_wiki", false, "Wiki setting");
    requireEqual(failures, prefix, repository, "has_discussions", false, "Discussions setting");
    requireEqual(failures, prefix, repository, "allow_squash_merge", true, "squash merges");
    requireEqual(failures, prefix, repository, "allow_merge_commit", false, "merge commits");
    requireEqual(failures, prefix, repository, "allow_rebase_merge", false, "rebase merges");
    requireEqual(failures, prefix, repository, "allow_auto_merge", true, "auto-merge");
    requireEqual(
      failures,
      prefix,
      repository,
      "delete_branch_on_merge",
      true,
      "automatic branch deletion",
    );
    requireEqual(
      failures,
      prefix,
      repository,
      "allow_update_branch",
      true,
      "pull request branch updates",
    );
    requireEqual(
      failures,
      prefix,
      repository,
      "use_squash_pr_title_as_default",
      true,
      "squash commit title policy",
    );

    if (data.actions?.sha_pinning_required !== true) {
      failures.push(`${prefix}: Actions must require full-length commit SHA pins`);
    }
    if (data.workflowPermissions?.default_workflow_permissions !== "read") {
      failures.push(`${prefix}: default workflow permissions must be read-only`);
    }
    if (data.workflowPermissions?.can_approve_pull_request_reviews !== false) {
      failures.push(`${prefix}: workflows must not approve pull request reviews`);
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
    if (protection.required_linear_history?.enabled !== true) {
      failures.push(`${prefix}: main must require linear history`);
    }
    if (protection.required_conversation_resolution?.enabled !== true) {
      failures.push(`${prefix}: pull request conversations must be resolved`);
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

function auditOrganizationSettings(snapshot, failures, owner) {
  const organization = snapshot.organization;
  if (!organization) {
    failures.push(`${owner}: organization settings are unavailable`);
  } else {
    const required = [
      ["two_factor_requirement_enabled", true, "two-factor authentication requirement"],
      ["default_repository_permission", "read", "default repository permission"],
      ["members_can_create_repositories", false, "member repository creation"],
      ["members_can_create_pages", false, "member Pages creation"],
      ["members_can_delete_repositories", false, "member repository deletion"],
      ["members_can_change_repo_visibility", false, "member visibility changes"],
      ["members_can_create_teams", false, "member team creation"],
      ["readers_can_create_discussions", false, "reader discussion creation"],
      ["dependency_graph_enabled_for_new_repositories", true, "new-repository dependency graph"],
      ["dependabot_alerts_enabled_for_new_repositories", true, "new-repository Dependabot alerts"],
      [
        "dependabot_security_updates_enabled_for_new_repositories",
        true,
        "new-repository Dependabot security updates",
      ],
      ["secret_scanning_enabled_for_new_repositories", true, "new-repository secret scanning"],
      [
        "secret_scanning_push_protection_enabled_for_new_repositories",
        true,
        "new-repository push protection",
      ],
    ];
    for (const [key, expected, label] of required) {
      requireEqual(failures, owner, organization, key, expected, label);
    }
  }

  if (snapshot.organizationActions?.sha_pinning_required !== true) {
    failures.push(`${owner}: organization Actions must require full-length commit SHA pins`);
  }
  if (snapshot.organizationWorkflowPermissions?.default_workflow_permissions !== "read") {
    failures.push(`${owner}: organization workflow permissions must be read-only`);
  }
  if (snapshot.organizationWorkflowPermissions?.can_approve_pull_request_reviews !== false) {
    failures.push(`${owner}: organization workflows must not approve pull request reviews`);
  }
  const retentionDays = snapshot.organizationArtifactRetention?.days;
  if (!Number.isInteger(retentionDays) || retentionDays > 30) {
    failures.push(`${owner}: Actions artifacts and logs must be retained for at most 30 days`);
  }
}

function requireSecurityFeature(failures, repository, security, key, label) {
  if (security[key]?.status !== "enabled") {
    failures.push(`${repository}: ${label} must be enabled`);
  }
}

function requireEqual(failures, prefix, source, key, expected, label) {
  if (source?.[key] !== expected) {
    failures.push(`${prefix}: ${label} must be ${JSON.stringify(expected)}`);
  }
}

function readRemoteSnapshot(owner) {
  const failures = [];
  const repositories = {};
  for (const policy of repositoryPolicies) {
    const prefix = `${owner}/${policy.name}`;
    repositories[policy.name] = {
      repository: ghJSON(`repos/${prefix}`, `read ${prefix} metadata`, failures),
      actions: ghJSON(
        `repos/${prefix}/actions/permissions`,
        `read ${prefix} Actions permissions`,
        failures,
      ),
      workflowPermissions: ghJSON(
        `repos/${prefix}/actions/permissions/workflow`,
        `read ${prefix} workflow permissions`,
        failures,
      ),
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
      organization: ghJSON(`orgs/${owner}`, `read ${owner} organization settings`, failures),
      organizationActions: ghJSON(
        `orgs/${owner}/actions/permissions`,
        `read ${owner} Actions permissions`,
        failures,
      ),
      organizationWorkflowPermissions: ghJSON(
        `orgs/${owner}/actions/permissions/workflow`,
        `read ${owner} workflow permissions`,
        failures,
      ),
      organizationArtifactRetention: ghJSON(
        `orgs/${owner}/actions/permissions/artifact-and-log-retention`,
        `read ${owner} artifact retention`,
        failures,
      ),
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
  console.log(`Organization and repository settings audit passed for ${owner}.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
