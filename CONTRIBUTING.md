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
4. Add the relevant tests, documentation, and generated artifacts as part of the same change.
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

`pnpm --dir web build` overwrites `server/router/frontend/dist/`. Commit the Web source and the latest embedded artifacts together. Generate the Web artifacts before building the Go binary.

## Verification

Run at least the commands appropriate for the affected area. CI runs Go test/vet/build, Buf lint/breaking/generate, Web lint/typecheck/test/build, Android test/lint/build, fresh-instance E2E, Docker build, and Compose parsing. It also checks dependency metadata, the Docker context policy, Proto/Web generated artifacts, Markdown links, and whitespace in the commit range. Dependabot checks Go, Web, Android, Docker, and GitHub Actions dependencies weekly; security updates must still pass the same gates. Before a Docker build, check the context policy to ensure that Git-ignored local data, secrets, and build artifacts are not sent to the builder.

| Area | Commands |
| --- | --- |
| Go | `go mod tidy -diff`, `go test -count=1 ./...`, `go vet ./...`, `go build ./cmd/sillage` |
| Web | `pnpm --dir web lint`, `pnpm --dir web typecheck`, `pnpm --dir web test`, `pnpm --dir web build` |
| Proto | `buf lint`, `buf breaking --against '.git#branch=main'`, `buf generate`, then inspect the generated diff |
| Android | `cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` |
| Documentation and final checks | `node scripts/check-markdown-links.mjs`, `git diff --check` |
| Deployment | `node scripts/check-docker-context.mjs`, `docker build --build-arg VERSION=dev --build-arg REVISION="$(git rev-parse HEAD)" -t sillage:dev -f scripts/Dockerfile .`, `docker compose -f scripts/compose.yaml config` |

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

GitHub Releases are the only source of user-visible release notes; the repository does not maintain a separate `CHANGELOG.md`. Releases are created from `main` commits that have passed CI. The README and documentation index must link to Releases.

1. Compile the user-visible changes and clearly document any compatibility impact on the database, configuration, synchronization, or data formats. Update the deployment and data documentation when special upgrade steps are required.
2. For an Android APK release, update `android/app/build.gradle.kts` by incrementing `versionCode`, and keep `versionName` consistent with the `vX.Y.Z` tag.
3. Run the appropriate gates from the Verification section and the Docker build. For an Android release, also follow the [Sillage Android Guide](android/README.md) to complete signing and the `apksigner` and `zipalign` checks.
4. After committing the release preparation, create an annotated `vX.Y.Z` tag, then create a GitHub Release from that tag. Release builds use `VERSION=vX.Y.Z` and `REVISION=$(git rev-parse HEAD)`; the binary's `--version` output and the image's OCI labels must be traceable to the tag and commit.
5. Release notes must include the main changes, known limitations, and upgrade or rollback requirements. Include checksums when downloadable artifacts are available. Do not commit keystores, signing configuration, or build artifacts.

## Commits

Commit messages follow Conventional Commits:

```text
<type>(<scope>): <subject>
```

Common `type` values are `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, and `ci`. Each commit should have a single purpose and include the corresponding tests, generated artifacts, and documentation.
