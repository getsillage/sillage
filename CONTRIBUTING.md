# Contributing Guide

This document is the single entry point for this repository's development environment, generated artifacts, and quality gates. Project-wide standards, the dual contribution track, and the enforcement registry live in [Engineering Governance](docs/development/governance.md). Product boundaries are in the [Constitution](docs/development/constitution.md) and [Product Guidance](docs/development/product-guidance.md). Module responsibilities are in the [Architecture Guide](docs/development/architecture.md).

## Environment

| Area | Requirement |
| --- | --- |
| Go | Go 1.25 |
| Web | Node.js 24, pnpm 11.9 |
| Proto | Buf CLI 1.71 |
| Android | JDK 17, Android SDK 35 |
| Desktop packaging | JDK 17 with `jpackage`; WiX Toolset 3 on Windows |
| Containers | Docker; Compose is optional |
| Verification | GNU Make (for `make check*` targets) |

## Repository Layout

- `apps/` contains buildable applications and reserved platform hosts.
- `packages/` contains reusable Web and Kotlin Multiplatform modules.
- `contracts/` contains authored wire definitions, generated projections,
  compatibility policy, and conformance fixtures.
- `tests/` contains verification that crosses an application or language
  boundary.
- `tooling/` is the target boundary for repository code generation, CI, and
  release tooling; existing runtime and migration scripts remain under
  `scripts/` until moved by a dedicated phase.

The Web client remains React. Android, iOS, Windows, and macOS use Compose
Multiplatform as their primary UI technology and may use platform-native UI for
system integration or materially better platform behavior. See
[Multiplatform Client Architecture](docs/development/multiplatform.md). Reserved
platform and package directories are not buildable modules until their build
configuration and CI gates are introduced together.

## Local Development

Install the Web dependencies:

```bash
pnpm --dir apps/web install
```

Start the backend. `.data-dev/` is ignored by Git:

```bash
export SILLAGE_ADDR=127.0.0.1
export SILLAGE_DATA="$PWD/.data-dev"
export SILLAGE_LOG_FORMAT=text
go run ./cmd/sillage
```

In another terminal, start the Web development server:

```bash
pnpm --dir apps/web dev
```

Open `http://localhost:5173`. Vite listens only on `127.0.0.1` and proxies API, attachment, and Connect requests to `http://localhost:5231`. Use `pnpm --dir apps/web dev:lan` only for debugging on a trusted LAN; it indirectly exposes the local backend to other devices on the LAN and must not be used with an uninitialized instance.

## Contribution Workflow

1. Search existing Issues and pull requests before starting. Use the repository's Bug Report or Feature Request form for new work.
2. Open a Feature Request before implementing a substantial change to product scope, public contracts, data formats, authentication, or security boundaries. If the discussion could reveal a vulnerability or sensitive security detail, use the private process in the [Security Policy](SECURITY.md) instead.
3. Fork the repository, create a focused branch from `main`, and keep the change limited to one purpose.
4. Add the relevant tests, documentation, and tracked generated artifacts as part of the same change.
5. Run the verification targets for every affected area (see below).
6. Open a pull request and complete the pull request template with the rationale, related Issue, and exact validation performed.

All participation is governed by the [Code of Conduct](CODE_OF_CONDUCT.md). Never report a vulnerability in a public Issue; follow the private process in the [Security Policy](SECURITY.md).

Maintainer and coding-agent workflows may push `main` under [CLAUDE.md](CLAUDE.md), but they use the **same quality gates** as pull requests. See [Governance — dual tracks](docs/development/governance.md#dual-contribution-tracks).

## Change Rules

- Preserve Sillage's boundary as a private, single-user space for records. Do not introduce multi-user features, public sharing, social features, tags, tasks, or knowledge base functionality.
- Keep public ingress, TLS, DNS, tunneling, CDNs, and edge-network services outside this repository. Do not add third-party network connectors, credentials, editor tooling, or vendor-specific deployment paths.
- Keep AI services associated with edge-network platforms behind operator-configured compatible endpoints. Do not add named provider presets, adapters, or defaults for them.
- Modify only the files needed to complete the current task. Update documentation alongside any feature, contract, configuration, or architecture change.
- The backend, database, Proto, and API use `memo`; English user-facing documentation and copy use `record`; the Simplified Chinese UI uses `记录`.
- Do not edit `contracts/proto/gen/` or `server/router/frontend/dist/` directly.
- Do not commit real secrets, databases, attachments, APK/AAB files, keystores, `local.properties`, or device caches.
- Significant cross-module technical choices need an ADR under `docs/development/decisions/` (Context / Decision / Consequences only).

### API Contracts

1. Modify `contracts/proto/api/v1/`.
2. Run `make check-proto` (or `buf lint`, `buf breaking` with a base ref, and `buf generate`), then commit the generated output in `contracts/proto/gen/`.
3. Update the affected handwritten REST routes, the [REST API Guide](docs/development/api/README.md), `apps/web/src/lib/api.ts`, and Android's `SillageApi.kt`.
4. Cover the behavior with both REST and Connect tests.

`contracts/proto/gen/openapi/openapi.yaml` is a generated projection of the Proto HTTP annotations, not the complete Echo REST contract, and cannot be used directly for REST SDK code generation. Extensions such as uploads and SSE are defined by the REST API Guide and `server/*_routes.go`.

### Database Schema

The schema for new databases lives in `store/migration/sqlite/LATEST.sql`. Compatible upgrades for existing databases run in the order defined in `store/migrator.go`. Compatibility steps must be idempotent and reentrant, and they must advance `schema_version` to the current version only after succeeding. New binaries reject databases below the minimum supported version or above the current version. Schema changes must update the current and minimum schema versions, both schema definitions, and the tests that upgrade from the minimum supported version. Do not execute `LATEST.sql` as an incremental migration. Downgrading always requires restoring a complete data backup taken before the upgrade.

### Web Artifacts

`pnpm --dir apps/web build` overwrites the ignored `server/router/frontend/dist/` directory. Do not commit its contents. The tracked `server/router/frontend/dist_placeholder.txt` keeps ordinary Go builds valid and lets them serve a fallback page when the Web assets are absent. Generate the Web assets before building a production Go binary. `make check-web` also verifies route-level chunks and raw/gzip bundle budgets; update a budget only with measured output and a documented product reason.

## Verification

Use the root **Makefile** so local runs match CI. Details and the path→gate matrix are in [Governance](docs/development/governance.md).

```bash
make check              # all CI-equivalent code, secret, artifact, and E2E gates
make check-fast         # go + proto + web + docs
make check-affected     # gates implied by current changes (set BASE_SHA for PR ranges)
make print-affected     # show gates without running them
```

| Gate | Make target | What it runs |
| --- | --- | --- |
| Go | `make check-go` | `go mod tidy -diff`, tests, vet, govulncheck, build |
| Proto | `make check-proto` | Buf lint/breaking/generate + `contracts/proto/gen` drift |
| Web | `make check-web` | lint, typecheck, unit tests, production build, route-split/size budgets, embed policy |
| Android | `make check-android` | shared native common tests, Android unit tests, lint, debug/test APKs, strict dependency integrity, notices, OSV release-runtime scan, release manifest policy, min/target device-matrix consistency |
| Desktop | `make check-desktop` | shared native common tests, desktop host tests, desktop JVM production compilation |
| Desktop package | `make check-desktop-package` | host-native DMG on macOS or MSI on Windows, expected artifact name and size verification |
| iOS | `make check-ios` | shared native common tests, device/simulator framework links, Swift bridge typecheck, unsigned simulator host build; CI allows 45 minutes for a clean four-framework and Xcode build |
| Android device | `make check-android-device` | Keystore/SQLite migration and critical Compose journeys on a connected device or emulator |
| Scale | `make check-scale` | 10,000 active records, 2,000 recoverable deletions, HTTP list/search/sync pagination, and SQLite integrity budgets |
| Upgrade | `make check-upgrade` | latest stable binary/data creation, candidate migration, representative data checks, old-binary schema rejection, and complete-backup rollback |
| Docs | `make check-docs` | Docker context, Markdown links, terminology, whitespace, doc-sync, immutable Action refs |
| Container | `make check-container` | Docker build + Compose config |
| Supply Chain | `make check-supply-chain` | pnpm high-severity audit, runtime license/NOTICE drift, SPDX + CycloneDX SBOM, Grype final-image scan (high blocks) |
| E2E | `make check-e2e` | complete fresh-instance journeys in Chromium, Firefox, and WebKit |
| Commits | `make check-commits` | Conventional Commits for `BASE_SHA..HEAD` |
| Actions | `make check-actions` | full commit-SHA pins for every workflow action |
| Remote settings | `make check-repository-settings` | authenticated branch-protection, security-feature, vulnerability-reporting, and Pages HTTPS audit |

The full `make check` requires Docker, gitleaks, Playwright system dependencies, complete Git tags, and `tar`. CI also validates pull request titles as Conventional Commits subjects.

Pull request CI resolves the affected gates from `scripts/change-matrix.yml`; pushes to `main` always run the complete gate set. Superseded runs for the same pull request are cancelled automatically. Required check names remain visible when a gate is skipped; unaffected API 26/API 35 matrix entries complete as fast no-op checks so their names remain concrete. CI uses KVM-backed Android emulators, runs Chromium E2E for ordinary affected pull requests, and reserves the full Chromium/Firefox/WebKit and three-language CodeQL sweep for relevant CI changes and protected `main`.

For a PR-shaped range:

```bash
export BASE_SHA=origin/main   # or the PR base commit
make check-affected
make check-commits
```

When contract-sensitive paths change, `check-doc-sync` expects a matching documentation path in the same range. To skip deliberately, include `Docs-skip: <reason>` in a commit message body.

CI enforces this range-sensitive rule on the pull request before merge. Pushes to protected `main` rerun the remaining Docs checks but do not re-evaluate `Docs-skip`, because squash merging may replace the reviewed commit bodies.

Dependabot-owned pull requests do not need an empty `Docs-skip` commit when all changed files are recognized dependency surfaces such as manifests, lockfiles, Action pins, verification metadata, container base images, and generated license inventories. Any source or unrelated configuration change disables that exemption.

Web E2E (`make check-e2e`) starts a separate disposable server for Chromium, Firefox, and WebKit so the mutable single-account journey is isolated in every engine. Install Playwright browsers with `pnpm --dir apps/web exec playwright install` on first use; CI installs all three engines with their OS dependencies. The long-term personal-use budget is enforced separately by `make check-scale`; its dataset and thresholds are normative in [Release Readiness](docs/development/release-readiness.md).

Changes that affect the UI must also follow the [Web Design Guidelines](docs/development/design/README.md) for manual checks in light and dark themes on desktop and mobile. Android changes involving storage, editing, attachments, or network state must pass `make check-android-device` on an appropriate emulator or physical device. Also check system Back navigation, the soft keyboard, cancellation on slow networks, and the external file viewer when the change touches those interactions. CI provisions clean API 26 and API 35 x86_64 emulators so the automated device suite covers both the oldest supported Android release and the current release target. A stable release candidate still requires a physical-device smoke test under [Release Readiness](docs/development/release-readiness.md).

Dependabot opens a limited number of weekly dependency PRs; they must pass the same gates. See [ADR: limited Dependabot PRs](docs/development/decisions/2026-07-30-dependabot-limited-prs.md).

The CI workflow uploads CodeQL analysis for Go, JavaScript/TypeScript, and Java/Kotlin. Before a stable release, maintainers must also run `make check-repository-settings` with an authenticated `gh` CLI session; source checks cannot prove external GitHub controls.

### Optional local hooks

[lefthook](https://github.com/evilmartians/lefthook) can run a subset of checks before each commit. CI remains authoritative.

```bash
brew install lefthook   # or see lefthook install docs
lefthook install
```

Hooks are defined in `lefthook.yml` (`commit-msg` Conventional Commits; `pre-commit` terminology + whitespace). Install is optional for contributors; do not treat a local skip as permission to land failing CI.

## Releases

GitHub Releases are the canonical user-visible release notes; the repository does not maintain a separate `CHANGELOG.md`. Each release commit contains a machine-checked input at `.github/release-notes/vX.Y.Z.md`; the Release workflow adds the exact image digest and verification evidence before publishing it. Release container images and optional APK assets must be produced by the [Release workflow](.github/workflows/release.yml). Do not attach hand-built server binaries, Docker images, or unsigned APKs to a Release.

The supported environment matrix, scale budgets, release-candidate journeys, published-artifact checks, and required remote repository controls are defined in [Release Readiness](docs/development/release-readiness.md). A stable release requires both the automated workflow and that manual acceptance evidence; neither substitutes for the other.

1. Merge release preparation to `main` after CI is green. Add `.github/release-notes/vX.Y.Z.md` with the main changes, compatibility impact, known limitations, upgrade/rollback requirements, and automated/manual evidence. Candidate notes may state evidence that is still pending, but the Release preflight rejects `待完成`, `PENDING`, `TODO`, or `TBD`; update the notes in a new green release commit before creating the signed tag. Update deployment and data documentation when special upgrade steps are required.
2. For an Android APK release, update `apps/native/androidApp/build.gradle.kts` by incrementing `versionCode`, and keep `versionName` consistent with the `vX.Y.Z` tag. To publish the APK from CI, set repository variable `RELEASE_ANDROID_APK=true` and configure secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.
3. Before tagging an Android release, run the `Android Release Candidate` workflow from protected `main` with the exact 40-character release-commit SHA. It accepts only a commit with the complete successful CI job set, signs the APK with the configured release secrets, and retains the APK, checksum, certificate report, and package metadata for seven days. Download that artifact for the physical-device acceptance in [Release Readiness](docs/development/release-readiness.md); do not publish or redistribute it as a final release asset.
4. Create a GitHub-verified signed annotated tag on the release commit: `git tag -s -a vX.Y.Z -m "Sillage vX.Y.Z"` and push it with `git push origin vX.Y.Z`. The workflow verifies the tag signature, exact commit, Android version, and all required CI jobs before publishing.
5. The Release workflow builds multi-arch images (`linux/amd64`, `linux/arm64`), pushes immutable `ghcr.io/getsillage/sillage:vX.Y.Z` (and stable aliases), creates a new GitHub Release, and optionally uploads a signed APK. Image `VERSION` and `REVISION` labels must match the tag and commit.
6. The same workflow scans the published digest with the pinned Grype image, signs GitHub provenance and SPDX attestations, and uploads SPDX, CycloneDX, Grype, and SHA-256 evidence assets. A high-severity image finding blocks the release job.
7. Published release tags, image tags, and APK assets are immutable and are never overwritten. If an APK needs to be added later, use `workflow_dispatch` with `mode=android-only`; this mode requires the existing GitHub Release and refuses an existing APK asset. Do not commit keystores, signing configuration, or build artifacts.
8. The workflow composes the checked release-note input with a digest-pinned install command and signed evidence. Do not edit published notes to describe artifacts that differ from the immutable tag. Prerelease tags do not move `latest` or the `major.minor` stable alias.
9. After the **first** successful image publish, open [GitHub Packages](https://github.com/orgs/getsillage/packages) for `sillage`, set package visibility to **Public**, and link it to this repository if needed. Anonymous pulls of `ghcr.io/getsillage/sillage:latest` require a public package; `GITHUB_TOKEN` often cannot change org package visibility via the API.

`main` requires green CI status checks before merge. Force-pushes to `main` are blocked.

## Commits

Commit messages follow Conventional Commits:

```text
<type>(scope): <subject>
```

Common `type` values are `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`, `style`, `perf`, and `build`. Each commit should have a single purpose and include the corresponding tests, generated artifacts, and documentation. CI validates subjects on pull requests and pushes to `main`.
