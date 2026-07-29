# Contributing Guide

This document is the single entry point for this repository's development environment, generated artifacts, and quality gates. See the [Product Guidance](docs/development/product-guidance.md) for product boundaries and the [Architecture Guide](docs/development/architecture.md) for module responsibilities.

## Environment

| Area | Requirement |
| --- | --- |
| Go | Go 1.25 |
| Web | Node.js 24, pnpm 11.9 |
| Proto | Buf CLI 1.71 |
| Android | JDK 17, Android SDK 35 |
| Containers | Docker; Compose is optional |

## Local Development

Install the Web dependencies:

```bash
pnpm --dir web install
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
pnpm --dir web dev
```

Open `http://localhost:5173`. Vite listens only on `127.0.0.1` and proxies API, attachment, and Connect requests to `http://localhost:5231`. Use `pnpm --dir web dev:lan` only for debugging on a trusted LAN; it indirectly exposes the local backend to other devices on the LAN and must not be used with an uninitialized instance.

## Contribution Workflow

1. Search existing Issues and pull requests before starting. Use the repository's Bug Report or Feature Request form for new work.
2. Open a Feature Request before implementing a substantial change to product scope, public contracts, data formats, authentication, or security boundaries. If the discussion could reveal a vulnerability or sensitive security detail, use the private process in the [Security Policy](SECURITY.md) instead.
3. Fork the repository, create a focused branch from `main`, and keep the change limited to one purpose.
4. Add the relevant tests, documentation, and tracked generated artifacts as part of the same change.
5. Run the validation commands for every affected area.
6. Open a pull request and complete the pull request template with the rationale, related Issue, and exact validation performed.

All participation is governed by the [Code of Conduct](CODE_OF_CONDUCT.md). Never report a vulnerability in a public Issue; follow the private process in the [Security Policy](SECURITY.md).

## Change Rules

- Preserve Sillage's boundary as a private, single-user space for records. Do not introduce multi-user features, public sharing, social features, tags, tasks, or knowledge base functionality.
- Keep public ingress, TLS, DNS, tunneling, CDNs, and edge-network services outside this repository. Do not add third-party network connectors, credentials, editor tooling, or vendor-specific deployment paths.
- Keep AI services associated with edge-network platforms behind operator-configured compatible endpoints. Do not add named provider presets, adapters, or defaults for them.
- Modify only the files needed to complete the current task. Update documentation alongside any feature, contract, configuration, or architecture change.
- The backend, database, Proto, and API use `memo`; English user-facing documentation and copy use `record`; the Simplified Chinese UI uses `记录`.
- Do not edit `proto/gen/` or `server/router/frontend/dist/` directly.
- Do not commit real secrets, databases, attachments, APK/AAB files, keystores, `local.properties`, or device caches.

### API Contracts

1. Modify `proto/api/v1/`.
2. Run `buf lint`, `buf breaking --against '.git#branch=main'`, and `buf generate`, then commit the generated output in `proto/gen/`.
3. Update the affected handwritten REST routes, the [REST API Guide](docs/development/api/README.md), `web/src/lib/api.ts`, and Android's `SillageApi.kt`.
4. Cover the behavior with both REST and Connect tests.

`proto/gen/openapi/openapi.yaml` is a generated projection of the Proto HTTP annotations, not the complete Echo REST contract, and cannot be used directly for REST SDK code generation. Extensions such as uploads and SSE are defined by the REST API Guide and `server/*_routes.go`.

### Database Schema

The schema for new databases lives in `store/migration/sqlite/LATEST.sql`. Compatible upgrades for existing databases run in the order defined in `store/migrator.go`. Compatibility steps must be idempotent and reentrant, and they must advance `schema_version` to the current version only after succeeding. New binaries reject databases below the minimum supported version or above the current version. Schema changes must update the current and minimum schema versions, both schema definitions, and the tests that upgrade from the minimum supported version. Do not execute `LATEST.sql` as an incremental migration. Downgrading always requires restoring a complete data backup taken before the upgrade.

### Web Artifacts

`pnpm --dir web build` overwrites the ignored `server/router/frontend/dist/` directory. Do not commit its contents. The tracked `server/router/frontend/dist_placeholder.txt` keeps ordinary Go builds valid and lets them serve a fallback page when the Web assets are absent. Generate the Web assets before building a production Go binary.

## Verification

Run at least the commands appropriate for the affected area. CI runs Go test/vet/build, Buf lint/breaking/generate, Web lint/typecheck/test/build, Android test/lint/build, fresh-instance E2E, Docker build, and Compose parsing. It also checks dependency metadata, the Docker context policy, tracked Proto artifacts, successful Web output generation, Markdown links, and whitespace in the commit range. Dependabot checks Go, Web, Android, Docker, and GitHub Actions dependencies weekly; security updates must still pass the same gates. Before a Docker build, check the context policy to ensure that Git-ignored local data, secrets, and build artifacts are not sent to the builder.

CI compares Proto compatibility with the exact pull request base or previous pushed commit, and authenticates Buf setup downloads with the workflow token to avoid anonymous GitHub API rate limits. The fresh-instance E2E job builds the Go binary before starting the readiness timeout so cold compilation cannot consume the server startup window.

| Area | Commands |
| --- | --- |
| Go | `go mod tidy -diff`, `go test -count=1 ./...`, `go vet ./...`, `go build ./cmd/sillage` |
| Web | `pnpm --dir web lint`, `pnpm --dir web typecheck`, `pnpm --dir web test`, `pnpm --dir web build` |
| Proto | `buf lint`, `buf breaking --against '.git#branch=main'`, `buf generate`, then inspect the generated diff |
| Android | `cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` |
| Documentation and final checks | `node scripts/check-markdown-links.mjs`, `git diff --check` |
| Deployment | `node scripts/check-docker-context.mjs`, `docker build --build-arg VERSION=dev --build-arg REVISION="$(git rev-parse HEAD)" -t sillage:dev -f scripts/Dockerfile .`, `docker compose -f scripts/compose.yaml config`, `docker compose -f scripts/compose.yaml -f scripts/compose.build.yaml config` |

Web E2E tests run against a live instance. First prepare the browser and embedded artifacts:

```bash
pnpm --dir web exec playwright install
pnpm --dir web build
```

Start a fresh instance in the first terminal:

```bash
SILLAGE_DATA="$(mktemp -d)" SILLAGE_ADDR=127.0.0.1 go run ./cmd/sillage
```

After the instance is ready, run the tests in a second terminal:

```bash
E2E_FRESH_INSTANCE=1 pnpm --dir web test:e2e
```

Changes that affect the UI must also follow the [Web Design Guidelines](docs/development/design/README.md) for manual checks in light and dark themes on desktop and mobile. Android changes involving editing, attachments, or network state must be checked on an emulator or physical device for system Back navigation, the soft keyboard, cancellation on slow networks, and the external file viewer.

## Releases

GitHub Releases are the only source of user-visible release notes; the repository does not maintain a separate `CHANGELOG.md`. Release container images and optional APK assets must be produced by the [Release workflow](.github/workflows/release.yml). Do not attach hand-built server binaries, Docker images, or unsigned APKs to a Release.

1. Merge release preparation to `main` after CI is green. Document user-visible changes and any compatibility impact on the database, configuration, synchronization, or data formats. Update the deployment and data documentation when special upgrade steps are required.
2. For an Android APK release, update `android/app/build.gradle.kts` by incrementing `versionCode`, and keep `versionName` consistent with the `vX.Y.Z` tag. To publish the APK from CI, set repository variable `RELEASE_ANDROID_APK=true` and configure secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.
3. Create an annotated tag on the release commit: `git tag -a vX.Y.Z -m "Sillage vX.Y.Z"` and push it with `git push origin vX.Y.Z`.
4. The Release workflow builds multi-arch images (`linux/amd64`, `linux/arm64`), pushes `ghcr.io/getsillage/sillage:vX.Y.Z` (and related tags), creates or updates the GitHub Release, and optionally uploads a signed APK. Image `VERSION` and `REVISION` labels must match the tag and commit.
5. Edit the generated Release notes so they include the main changes, known limitations, and upgrade or rollback requirements. Do not commit keystores, signing configuration, or build artifacts.
6. To republish an image for an existing tag (for example after enabling GHCR), run the Release workflow with `workflow_dispatch` and the tag name. Set `create_release` only when the GitHub Release should be created or refreshed.
7. After the **first** successful image publish, open [GitHub Packages](https://github.com/orgs/getsillage/packages) for `sillage`, set package visibility to **Public**, and link it to this repository if needed. Anonymous pulls of `ghcr.io/getsillage/sillage:latest` require a public package; `GITHUB_TOKEN` often cannot change org package visibility via the API.

`main` requires green CI status checks before merge. Force-pushes to `main` are blocked.

## Commits

Commit messages follow Conventional Commits:

```text
<type>(<scope>): <subject>
```

Common `type` values are `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, and `ci`. Each commit should have a single purpose and include the corresponding tests, generated artifacts, and documentation.
