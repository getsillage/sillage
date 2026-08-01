# Deployment Guide

Sillage is designed to run with Docker on a single machine. The service itself provides HTTP only; access from public networks requires a separately operated HTTPS entry point. Public ingress, TLS, DNS, tunneling, CDNs, and other edge-network services are outside the Sillage product and repository. This guide defines only Sillage's listening and forwarded-header contract and does not provide third-party connectors or vendor-specific deployment steps.

## Docker

The simplest path uses the published image and binds only to the local machine:

```bash
docker run --rm \
  -p 127.0.0.1:5231:5231 \
  -v "$HOME/.sillage:/var/opt/sillage" \
  ghcr.io/getsillage/sillage:latest
```

Open `http://localhost:5231` and create the instance's only account on the first visit. New passwords must contain at least 8 characters. Docker pulls the image automatically when needed.

`latest` always points at the newest non-prerelease image from CI. To pin a specific release later, replace `latest` with a tag from [GitHub Releases](https://github.com/getsillage/sillage/releases) (for example the tag shown on that page). Images are listed under [GHCR](https://github.com/getsillage/sillage/pkgs/container/sillage).

Do not use `-p 5231:5231` on a host without a firewall and HTTPS. It publishes the port on the host's available interfaces.

## Verify a release image

Use the digest printed in the release notes instead of trusting a mutable tag. The release also provides SPDX and CycloneDX SBOMs, a Grype report, and a SHA-256 manifest:

```bash
TAG=vX.Y.Z
IMAGE=ghcr.io/getsillage/sillage
DIGEST=sha256:<digest-from-the-release-notes>

docker pull "${IMAGE}@${DIGEST}"
mkdir -p "sillage-${TAG}-evidence"
gh release download "$TAG" \
  --repo getsillage/sillage \
  --pattern "Sillage-${TAG}.*.json" \
  --pattern "Sillage-${TAG}.supply-chain.sha256" \
  --dir "sillage-${TAG}-evidence"
(cd "sillage-${TAG}-evidence" && sha256sum -c "Sillage-${TAG}.supply-chain.sha256")
```

The release workflow signs the image's SLSA provenance and SPDX SBOM with GitHub's artifact-attestation service. After authenticating to GHCR, verify both subjects against this repository and the release workflow identity:

```bash
gh attestation verify "oci://${IMAGE}@${DIGEST}" \
  --repo getsillage/sillage \
  --signer-workflow getsillage/sillage/.github/workflows/release.yml
gh attestation verify "oci://${IMAGE}@${DIGEST}" \
  --repo getsillage/sillage \
  --signer-workflow getsillage/sillage/.github/workflows/release.yml \
  --predicate-type https://spdx.dev/Document
```

The image contains the project license and runtime dependency notices at `/usr/share/licenses/sillage/`. The downloadable SBOM covers the resolved image platform selected by the verifier; the image digest and registry-native attestations remain the source of truth for the complete multi-architecture manifest.

## Compose

From a checkout of this repository, start with the default image (`ghcr.io/getsillage/sillage:latest`):

```bash
cp .env.example .env
docker compose -f scripts/compose.yaml up -d
```

The checked-in `.env.example` contains only the two Compose-level overrides: the image and the host publish address. The copied `.env` is ignored by Git. To use another image, change `SILLAGE_IMAGE` before `up` (for example a release tag from GitHub Releases).

To allow trusted devices on a local network to connect directly, explicitly set `SILLAGE_HOST_PORT=5231` and configure the host firewall at the same time. Public deployments should remain bound to the loopback address, with the separately managed HTTPS entry point reaching that port through an operator-controlled network path.

Common operations:

```bash
docker compose -f scripts/compose.yaml logs -f sillage
docker compose -f scripts/compose.yaml stop sillage
docker compose -f scripts/compose.yaml start sillage
```

Compose defaults `SILLAGE_HOST_PORT` to `127.0.0.1:5231`; it controls only the host publish address. `SILLAGE_IMAGE` selects the container image. Application environment variables are declared explicitly in `scripts/compose.yaml`. Check that file before making changes, and do not assume that host variables with the same names are passed through automatically.

After changing the port binding or image, run `docker compose -f scripts/compose.yaml up -d sillage` again to recreate the existing container, then use `docker compose -f scripts/compose.yaml ps` to confirm the publish address. Changing the YAML alone does not alter a running container.

## First-Time Initialization and External Access

The account-creation endpoint does not require authentication on an uninitialized instance, and an instance accepts only its first account. Keep the default loopback port initially and create the account by opening `http://localhost:5231` from a local browser. Configure any external entry point or local-network port only after confirming the account. Never expose an uninitialized instance directly to the public internet.

Check the initialization status locally:

```bash
curl http://localhost:5231/api/v1/auth/bootstrap
```

Open an external entry point only after the response is `{"initialized":true}`. Store the password in a password manager. After signing in, you can change the password in Settings under Account (`账号`). Sillage deliberately has no unauthenticated forgot-password endpoint; an operator who controls the data directory can use the offline recovery command documented in [Data, Backup, and Recovery](data.md#offline-password-recovery).

## Configuration

The application supports both command-line flags and `SILLAGE_*` environment variables. The following commonly used variables are currently effective:

| Variable | Default | Description |
| --- | --- | --- |
| `SILLAGE_ADDR` | `127.0.0.1` | HTTP bind address; set `0.0.0.0` only inside an isolated container or when intentionally serving a trusted network |
| `SILLAGE_PORT` | `5231` | HTTP port |
| `SILLAGE_DATA` | see below | Data directory; `/var/opt/sillage` in Docker |
| `SILLAGE_DSN` | `$SILLAGE_DATA/sillage.db` | SQLite path; relative paths are resolved from the data directory |
| `SILLAGE_MAX_UPLOAD_MB` | `30` | Maximum size of one attachment, in MiB |
| `SILLAGE_TRUSTED_PROXY` | empty | Comma-separated reverse-proxy CIDRs allowed to supply forwarded headers; for example `127.0.0.1/32,::1/128` |
| `SILLAGE_LOG_FORMAT` | `json` | `json` or `text` |
| `SILLAGE_LOG_LEVEL` | `info` | `debug`, `info`, `warn`, or `error` |
| `SESSION_SECRET` | generated automatically | Session-signing secret |
| `ENCRYPTION_SECRET` | generated automatically | AI API key encryption secret |

When running directly on the host, the application uses `/var/opt/sillage` by default if that directory exists; otherwise, it falls back to the current directory. Always set `SILLAGE_DATA` explicitly in production.

`SILLAGE_DSN`, `SESSION_SECRET`, and `ENCRYPTION_SECRET` support corresponding `_FILE` variables, such as `ENCRYPTION_SECRET_FILE=/run/secrets/encryption`. A direct value and its `_FILE` variable cannot be set at the same time. To use `_FILE` in a container, mount the file and pass the variable explicitly; the host environment is not passed through automatically. External databases and secret files are outside `SILLAGE_DATA` and must be included in the same backup and restore process.

The container entrypoint also supports `SILLAGE_UID` and `SILLAGE_GID`, both defaulting to `10001`. They adjust ownership of the mounted directory and run the process as a non-root user. Compose does not pass through these two variables; to customize them, explicitly change the Compose `environment` or use `docker run -e`.

The server holds exclusive advisory locks at `runtime/instance.lock` and beside the SQLite database (`<database-path>.sillage.lock`) for its full process lifetime. A second server or an offline administrative command pointed at the same data directory or external DSN fails closed. The files remain after a normal or abnormal exit and record the last process ID; their presence alone does not mean the instance is running.

Configure the AI API protocol, endpoint, model, and API key in the application settings after signing in; they are not configured through process environment variables. Read [AI Usage and Privacy](ai.md) before configuring them.

## External HTTPS Entry Point

Configure the direct proxy address or network with `SILLAGE_TRUSTED_PROXY`, then have the operator-managed entry point terminate TLS and overwrite the following request headers supplied by the client:

```text
X-Forwarded-Proto
X-Forwarded-Host
X-Forwarded-For
```

Do not simply append untrusted forwarding headers. Sillage ignores them when the direct peer is outside the configured CIDRs; for a trusted peer it uses `X-Forwarded-Proto` to mark Cookies as Secure and `X-Forwarded-For` for sign-in rate limiting. Only the operator-managed entry point should be able to reach the backend port. Its installation, credentials, DNS, TLS certificates, and network path must be configured outside this repository.

## Probes and Upgrades

```bash
curl --fail http://localhost:5231/healthz
curl --fail http://localhost:5231/readyz
```

`healthz` checks only the process, while `readyz` also checks SQLite. Before upgrading:

1. Note the image you are running (`docker inspect` digest or the tag you started with) so you can roll back.
2. Follow [Data, Backup, and Recovery](data.md) to stop the service and back up the complete data directory.
3. Pull the new image (`docker pull ghcr.io/getsillage/sillage:latest` or a pinned tag), start it, then confirm that probes, sign-in, records, and attachments work correctly.
4. If the upgrade fails, stop the new instance, restore the corresponding data backup, and start the preserved old image.

If a startup migration fails, the service does not enter the ready state. An older binary may not be compatible with an upgraded database, so you cannot roll back only the image without restoring the matching data.

After an upgrade, open Settings → Version and compatibility, or inspect the public bootstrap metadata:

```bash
curl --fail --silent http://localhost:5231/api/v1/auth/bootstrap
```

The server and Web revisions should match for a published build. If the application reports a mismatch, hard-refresh the browser and verify that a reverse proxy or CDN is not caching the HTML entry document. Hashed assets may be cached immutably, but `index.html` and client-side routes must be revalidated. The bootstrap response also reports the API generation and minimum supported Android `versionCode`; update an older Android client before relying on it for sync.

Probes do not require authentication, and `readyz` may include diagnostic text when a dependency fails. A public reverse proxy should allow only monitoring sources to access `/healthz` and `/readyz`; do not expose them as a public status page.

## Appendix: Build from source

Use a release checkout from [GitHub Releases](https://github.com/getsillage/sillage/releases) when you need a custom build. Published GHCR images remain the recommended path.

```bash
git clone https://github.com/getsillage/sillage.git
cd sillage
# Check out a release tag from the Releases page, then:
VERSION="$(git describe --tags --exact-match 2>/dev/null || echo dev)"
REVISION="$(git rev-parse HEAD)"
docker build \
  --build-arg VERSION="$VERSION" \
  --build-arg REVISION="$REVISION" \
  -t "sillage:local" \
  -f scripts/Dockerfile .
docker run --rm \
  -p 127.0.0.1:5231:5231 \
  -v "$HOME/.sillage:/var/opt/sillage" \
  sillage:local
```

Compose with a local build:

```bash
export SILLAGE_VERSION=local
export SILLAGE_REVISION="$(git rev-parse HEAD)"
export SILLAGE_IMAGE="sillage:local"
docker compose -f scripts/compose.yaml -f scripts/compose.build.yaml up -d --build
```

### Run a local binary

Go 1.25 is required. The ignored Web output is generated locally rather than stored in Git. Before a production-style run, generate the embedded Web assets and include the version and commit in the release build:

```bash
pnpm --dir apps/web install
pnpm --dir apps/web build
VERSION="$(git describe --tags --exact-match 2>/dev/null || echo dev)"
REVISION="$(git rev-parse HEAD)"
go build -ldflags "-X main.version=$VERSION -X main.revision=$REVISION" -o sillage ./cmd/sillage
SILLAGE_ADDR=127.0.0.1 SILLAGE_DATA="$HOME/.sillage" ./sillage
```

See the [Contributing Guide](../../CONTRIBUTING.md) for the development environment.

Identify a running binary with `./sillage --version`. Container images also include the OCI `version` and `revision` labels.

The Dockerfile pins the base-image digest, pnpm lockfile, and Go module checksums. Alpine system packages are still resolved from the repositories configured by the base image. When updating the base image or system packages, rebuild the image, document the reason, and update the pinned values in the same change.
