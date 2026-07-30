# Engineering Governance

**Audience:** maintainers, external contributors, coding agents
**Owner:** project maintainers
**Enforcement:** `Makefile` targets, `scripts/*`, GitHub Actions, Dependabot, branch protection
**Last reviewed:** 2026-07-30

This document is the **standards registry** for Sillage. It explains how norms are layered, who they apply to, and how they are enforced. Product intent lives in the [Constitution](constitution.md) and [Product Guidance](product-guidance.md). Day-to-day commands live in [Contributing](../../CONTRIBUTING.md).

## Norm layers

| Layer | Role | Examples |
| --- | --- | --- |
| L0 Constitution | Slow-changing identity and red lines | [constitution.md](constitution.md) |
| L1 Contracts | Machine-checkable technical interfaces | Proto, SQLite schema, sync semantics, env surface |
| L2 Procedures | Human workflows | PR template, release steps, security disclosure, ADRs |
| L3 Execution | Single verification surface | `Makefile`, `scripts/`, `.github/workflows/` |

**Policy as code:** if a rule can be expressed as lint, test, or CI, it must not exist only as prose. Agent instructions ([CLAUDE.md](../../CLAUDE.md)) stay short and point here.

## Dual contribution tracks

| Track | Who | Git workflow | Binding docs |
| --- | --- | --- | --- |
| External | Fork / pull request contributors | Branch + PR; `main` requires green CI | [CONTRIBUTING.md](../../CONTRIBUTING.md), this file |
| Maintainer / agent | Repository maintainers and local coding agents | May push `main` when project agent rules allow | [CLAUDE.md](../../CLAUDE.md) **plus** the same quality gates |

Rules that apply to **both** tracks:

1. Do not lower CI standards or skip failing gates by weakening tests.
2. Do not commit secrets, user data, keystores, or live databases.
3. Update canonical docs in the same change as feature, contract, config, or architecture changes.
4. Preserve the constitution boundaries (single-user private records, source-grounded personal AI claims).

There is no third informal rulebook. Chat-only instructions are not durable project norms.

## Standards registry

| Domain | Normative source | Enforcement | Status |
| --- | --- | --- | --- |
| Product boundary | [constitution.md](constitution.md), [product-guidance.md](product-guidance.md) | Review + `scripts/check-terminology.mjs` (light) | Active |
| Terminology (`memo` / record / 记录) | product-guidance, architecture | Terminology check + review | Active |
| Module boundaries | [architecture.md](architecture.md) | Review | Active |
| API / Proto | `proto/api/v1/`, [api/README.md](api/README.md) | `make check-proto` | Active |
| Database schema | `store/migration/sqlite/LATEST.sql`, migrator | `make check-go` + migration tests | Active |
| Sync | [api/sync.md](api/sync.md) | Go/Android tests + review | Active |
| Security | [security.md](security.md), [SECURITY.md](../../SECURITY.md) | Tests + gitleaks + govulncheck + review | Active |
| Web UI | [design/README.md](design/README.md) | Web lint/tests, production build, route-split and raw/gzip bundle budgets, manual checklist | Active |
| Commits | Conventional Commits (below) | `make check-commits` / CI commits job | Active |
| Documentation hygiene | This file, docs hub | Markdown links, doc-sync, whitespace | Active |
| ADRs | [decisions/](decisions/) | Review for cross-module choices | Active |
| Dependencies | Dependabot config | Weekly PRs (limited) | Active |
| Dependencies and release supply chain | [CONTRIBUTING.md](../../CONTRIBUTING.md) Releases, [deployment.md](../user/deployment.md) | `make check-supply-chain`, pinned Syft/Grype, signed provenance and SPDX attestations | Active |
| Release artifacts | [CONTRIBUTING.md](../../CONTRIBUTING.md) Releases | `release.yml` only | Active |
| Release acceptance | [release-readiness.md](release-readiness.md) | CI-required jobs + release-candidate and published-artifact verification | Active |
| Agent session | [CLAUDE.md](../../CLAUDE.md) | Maintainer process | Active |
| Path → gate matrix | [../../scripts/change-matrix.yml](../../scripts/change-matrix.yml) | `make print-affected` / `check-affected` | Active |

## Unified verification

The repository root `Makefile` is the **single entry** for local and CI-equivalent checks.

| Command | Purpose |
| --- | --- |
| `make check` | all CI-equivalent code, secret, artifact, and E2E gates (requires Docker, gitleaks, and Playwright dependencies) |
| `make check-fast` | go + proto + web + docs |
| `make check-affected` | gates implied by the working tree or `BASE_SHA...HEAD` |
| `make check-go` / `check-proto` / `check-web` / `check-android` / `check-docs` / `check-actions` / `check-container` / `check-supply-chain` / `check-e2e` / `check-restore` | Individual gates |
| `make check-commits` | Conventional Commits for `BASE_SHA..HEAD` |
| `make print-affected` | Print matched rules and gates without running them |

CI jobs call the same scripts and Make targets. Prefer fixing a script once over copying commands into workflow YAML.

### Change matrix

`scripts/change-matrix.yml` maps path globs to gates and expected documentation surfaces. Helpers:

- `node scripts/affected.mjs` — resolve gates
- `node scripts/check-doc-sync.mjs` — require docs when contract paths change (needs `BASE_SHA`)

**Docs-skip escape hatch:** a commit in the range may include:

```text
Docs-skip: brief reason
```

Use sparingly (typo-only Proto comments, pure refactors with no external behavior change, etc.).

## Conventional Commits

Subject line format:

```text
<type>(<scope>): <description>
```

Allowed types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`, `style`, `perf`, `build`, `revert`.
Scope is optional (`web`, `android`, `server`, `proto`, `docs`, …).
Do not end the subject with a period. Merge and Revert commits are allowed as generated by Git/GitHub.

## ADRs

For significant, cross-module, hard-to-reverse choices, add:

```text
docs/development/decisions/YYYY-MM-DD-<topic>.md
```

Required sections only: **Context**, **Decision**, **Consequences**. Link superseded ADRs. Do not use ADRs for task plans or TODOs.

## Dependencies

Dependabot runs weekly for Go, npm (`web/`), Gradle (`android/`), Docker (`scripts/`), and GitHub Actions. Open PR limits are intentionally low to keep the queue reviewable. Security-critical updates may still be applied out of band with the same gates.

Third-party GitHub Actions are pinned to full commit SHAs, with the corresponding upstream release tag retained as a comment. `scripts/check-actions-pinned.mjs` prevents mutable tags or branches from entering workflow files. Go code is scanned with the module-version-pinned `govulncheck` command as part of `make check-go`. The production Web graph is checked with `pnpm audit --audit-level=high`. `make check-supply-chain` also regenerates and checks the Go/Web license inventory, builds the final container, emits SPDX and CycloneDX SBOMs with the pinned Syft image, and blocks high-severity findings from the pinned Grype image. Dependency changes must update the reviewed policy and preserved notice files together.

Files under `third_party/licenses/` are copied byte-for-byte from upstream packages and are therefore exempt from Sillage's whitespace normalization rule. The notice generator's byte comparison remains authoritative for those files; all project-authored files continue to pass `git diff --check`.

The release workflow adds signed GitHub provenance and SPDX attestations to the published image digest, and uploads the SBOMs, scanner report, and SHA-256 manifest as release assets. BuildKit's OCI provenance and SBOM attestations remain enabled as an additional registry-native record.

## Secrets scanning

CI installs the official [gitleaks](https://github.com/gitleaks/gitleaks) CLI, verifies the pinned release archive against a repository-controlled SHA-256 digest, and scans the checkout with [`.gitleaks.toml`](../../.gitleaks.toml). The marketplace `gitleaks-action` is not used because organization repositories require a paid `GITLEAKS_LICENSE`. Local optional run (if installed):

```bash
gitleaks detect --source . --config .gitleaks.toml --verbose --redact
```

## Quality severity

| Level | Meaning | CI |
| --- | --- | --- |
| Must | Security, contract compatibility, constitution boundaries, no secrets in git | Fail the build |
| Should | Lint, tests, doc links, gen drift, commit format, doc-sync | Fail the build |
| May | Non-essential style nits beyond configured linters | Review only |

## Related decisions

- [2026-07-30 Unified verification entry](decisions/2026-07-30-unified-verification-entry.md)
- [2026-07-30 Limited Dependabot PRs](decisions/2026-07-30-dependabot-limited-prs.md)
- [2026-07-30 Dual-track contribution](decisions/2026-07-30-dual-track-contribution.md)
