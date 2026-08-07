# Release Readiness

This document defines the evidence required before Sillage publishes a stable GitHub Release. It is a durable acceptance contract, not a release plan or a substitute for the automated gates in the root `Makefile`.

## Release Scope

Sillage's official distribution channels are:

- multi-architecture container images from `ghcr.io/getsillage/sillage` for `linux/amd64` and `linux/arm64`;
- a signed Android APK attached to GitHub Releases when Android publishing is enabled;
- the static product website at `https://getsillage.github.io/`, deployed from the separate `getsillage/getsillage.github.io` repository.

Sillage does not currently publish through Google Play, an Apple app store, or an official hosted service. Store listing policy, hosted-service SLA, billing, central telemetry, and platform-operated data processing are therefore outside the current release scope. Adding one of those channels changes the product's operational and legal responsibilities and requires a separate decision and acceptance contract before implementation.

The repository can build branded iOS, macOS, and Windows engineering artifacts,
but those clients are not official release surfaces yet. App icons and package
metadata prove application identity only; signing, notarization, secure online
credentials, updater behavior, platform device journeys, and release-candidate
coverage remain required before the supported-environment table can add those
platforms.

## Supported Environments

| Surface | Supported environment | Required release evidence |
| --- | --- | --- |
| Container | OCI runtime on Linux `amd64` or `arm64` | Multi-architecture image build, digest inspection, readiness probe, fresh install, upgrade, and restore-backed rollback |
| Web | Current stable Chromium, Firefox, and WebKit/Safari-compatible engines | The complete fresh-instance Playwright journey passes in all three engines; manual keyboard, responsive, theme, and locale review on the release candidate |
| Android | Android 8.0 / API 26 and later | Host unit/lint/build gate, instrumented journey on the oldest supported API and release target API, plus one physical-device release-candidate smoke test |
| Server data | Default SQLite data directory and documented external-DSN/secret-file variants | Automated recovery drill plus a release-candidate upgrade from the latest stable version using a protected backup |

Browser or Android versions outside this matrix may work but are not release-tested. Release notes must call out a temporary exception instead of silently reducing the matrix.

## Long-Term Personal-Use Scale

Sillage is a single-user personal record system, not a multi-tenant service. Stable releases must nevertheless demonstrate that normal long-term use does not degrade core retrieval or synchronization behavior.

The automated scale gate uses a deterministic synthetic instance with at least:

- 10,000 active records distributed across dates and searchable terms;
- 2,000 recoverable or synchronization tombstones;
- enough pages to exercise list and sync cursor traversal rather than a first-page-only path.

It verifies database integrity, first-page listing, selective full-text search, complete paginated traversal, and bounded sync pull. Each interactive read must finish within two seconds on the hosted CI runner, and the complete scale gate must finish within sixty seconds. These budgets are regression tripwires rather than an SLA; a budget change requires measured evidence and an update to this document in the same commit.

Attachment byte capacity depends on operator storage and is not represented by generating gigabytes in CI. The recovery drill remains authoritative for attachment metadata, bytes, secrets, and full-directory backup completeness.

## Automated Release Gates

The release commit must have a successful CI run containing every required job checked by `.github/workflows/release.yml`. At minimum this includes:

- commit policy and secret scanning;
- Go tests, vet, build, and vulnerability scan;
- Proto lint, breaking-change check, generation, and drift detection;
- Web lint, typecheck, unit tests, production build, bundle budgets, and three-engine E2E;
- shared KMP common tests and native architecture dependency checks, plus Android unit tests, lint, APK builds, dependency verification, vulnerability scan, and independently required API 26/API 35 device journeys;
- documentation and action-pin checks;
- container build, supply-chain inventory, SBOM generation, and vulnerability scan;
- long-term scale acceptance, isolated backup/restore recovery, and a real latest-stable-to-candidate upgrade plus backup-backed rollback.

The Android application host explicitly packages the shared app shell, design
system, authentication, records, Ask, settings, and sync UI modules. A release
candidate must therefore validate the assembled APK rather than treating green
shared-module tests as sufficient evidence: dependency verification, lint, unit
tests, both supported-boundary device journeys, bundled notices, and APK build
must all run against the same commit.

Do not release from a locally green commit that lacks the matching successful remote CI run. Local environment failures may be diagnosed independently, but the release workflow must never waive a required job.

The same commit must contain `.github/release-notes/vX.Y.Z.md`. Candidate notes may explicitly identify unfinished manual evidence while work continues, but Release preflight validates the version metadata, required sections, comparison link, upgrade/rollback language, supported Android boundaries, and absence of pending markers before any image or GitHub Release is published.

## Release-Candidate Manual Acceptance

Before creating the signed tag, verify the exact release commit and record the result in the release notes or maintainer release record:

1. Start a fresh digest-pinned container and initialize the only account.
2. Create, edit, search, archive, attach, summarize, ask, delete, restore, and permanently delete representative records.
3. Review the automated cross-version drill for the exact release commit, then repeat the upgrade with a copy of a representative operator instance after taking a complete backup when one is available.
4. Confirm sign-in, records, attachments, search, AI settings, sync metadata, and `/readyz` after the upgrade.
5. Exercise the documented restore-backed rollback rather than attempting a database-only binary downgrade.
6. Run the Web journey in Chromium, Firefox, and WebKit, then check narrow and desktop layouts, keyboard focus, English, Simplified Chinese, light theme, dark theme, console errors, and CSP failures.
7. From the protected `main` release commit, run the `Android Release Candidate` workflow with the exact successful-CI revision. Download its short-lived signed artifact, verify its checksum and certificate, install it over the latest stable APK, and perform online, offline, attachment, conflict, Recently Deleted, and manual-sync smoke tests on a physical device. Candidate artifacts are acceptance evidence only and must never be attached to a GitHub Release.
8. Verify the APK package, version code, checksum, signing certificate, bundled notices, and HTTPS-only release manifest.

Any failed item blocks a stable release. A prerelease may document an explicit limitation, but it must not overwrite or weaken the stable acceptance contract.

## Published Artifact Verification

After the workflow completes:

1. Inspect the immutable version tag and record the registry digest.
2. Confirm anonymous pull access to the digest-pinned image.
3. Verify GitHub build provenance against this repository and `release.yml`.
4. Verify the SPDX, CycloneDX, Grype, and SHA-256 manifest assets.
5. Download the APK and checksum from an unauthenticated environment, verify the checksum and signing certificate, and install that downloaded artifact.
6. Confirm release notes contain the exact published digest, compatibility impact, upgrade/rollback instructions, known limitations, and test evidence.
7. Confirm `https://getsillage.github.io/` and its fallback release tag point to the new stable release after publication.

Published tags, image version tags, APK assets, checksums, and attestations are immutable. If evidence is wrong, publish a correction notice; never replace an artifact under the same version.

## Remote Repository Controls

Before a stable release, repository settings must enforce the same policy described in code:

- every CI job required by the Release workflow is a required `main` status check;
- force pushes and branch deletion remain disabled and administrator enforcement remains enabled;
- private vulnerability reporting, Dependabot security updates, secret scanning, and push protection are enabled;
- GitHub Pages publishes `getsillage/getsillage.github.io` at `https://getsillage.github.io/` with HTTPS, and the website deployment gate passes before publication.

Repository settings are external state and cannot be proven by source review alone. Maintainers must run `make check-repository-settings` with an authenticated `gh` CLI session as part of release acceptance.
