# Deployment Guide

Sillage is designed to run with Docker on a single machine. The service itself provides HTTP only; access from public networks requires a separately operated HTTPS entry point. Public ingress, TLS, DNS, tunneling, CDNs, and other edge-network services are outside the Sillage product and repository. This guide defines only Sillage's listening and forwarded-header contract and does not provide third-party connectors or vendor-specific deployment steps.

## Docker

Choose a stable tag from [GitHub Releases](https://github.com/getsillage/sillage/releases), check it out, and then build it. `main` is for development and is not a traceable production version:

```bash
git checkout vX.Y.Z
VERSION="$(git describe --tags --exact-match)"
REVISION="$(git rev-parse HEAD)"
docker build \
  --build-arg VERSION="$VERSION" \
  --build-arg REVISION="$REVISION" \
  -t "sillage:$VERSION" \
  -f scripts/Dockerfile .
```

For local access only:

```bash
docker run --rm \
  -p 127.0.0.1:5231:5231 \
  -v "$HOME/.sillage:/var/opt/sillage" \
  "sillage:$VERSION"
```

Open `http://localhost:5231` and create the instance's only account on the first visit.

Do not use `-p 5231:5231` on a host without a firewall and HTTPS. It publishes the port on the host's available interfaces.

## Compose

The repository's Compose configuration publishes only on the loopback address by default:

```bash
VERSION="$(git describe --tags --exact-match)"
REVISION="$(git rev-parse HEAD)"
export SILLAGE_IMAGE="sillage:$VERSION"
export SILLAGE_VERSION="$VERSION"
export SILLAGE_REVISION="$REVISION"
docker compose -f scripts/compose.yaml up -d --build sillage
```

To allow trusted devices on a local network to connect directly, explicitly set `SILLAGE_HOST_PORT=5231` and configure the host firewall at the same time. Public deployments should remain bound to the loopback address, with the separately managed HTTPS entry point reaching that port through an operator-controlled network path.

Common operations:

```bash
docker compose -f scripts/compose.yaml logs -f sillage
docker compose -f scripts/compose.yaml stop sillage
docker compose -f scripts/compose.yaml start sillage
```

Compose defaults `SILLAGE_HOST_PORT` to `127.0.0.1:5231`; it controls only the host publish address. `SILLAGE_IMAGE`, `SILLAGE_VERSION`, and `SILLAGE_REVISION` control only the image name and build identifiers. Application environment variables are declared explicitly in `scripts/compose.yaml`. Check that file before making changes, and do not assume that host variables with the same names are passed through automatically.

After changing the port binding, run `docker compose -f scripts/compose.yaml up -d sillage` again to recreate the existing container, then use `docker compose -f scripts/compose.yaml ps` to confirm the publish address. Changing the YAML alone does not alter a running container.

## First-Time Initialization and External Access

The account-creation endpoint does not require authentication on an uninitialized instance, and an instance accepts only its first account. Keep the default loopback port initially and create the account by opening `http://localhost:5231` from a local browser. Configure any external entry point or local-network port only after confirming the account. Never expose an uninitialized instance directly to the public internet.

Check the initialization status locally:

```bash
curl http://localhost:5231/api/v1/auth/bootstrap
```

Open an external entry point only after the response is `{"initialized":true}`. Store the password in a password manager: there is currently no built-in workflow for changing or resetting it, or for recovering access while preserving data.

## Configuration

The application supports both command-line flags and `SILLAGE_*` environment variables. The following commonly used variables are currently effective:

| Variable | Default | Description |
| --- | --- | --- |
| `SILLAGE_ADDR` | empty | HTTP bind address; an empty value listens on available interfaces, so set it to `127.0.0.1` when running directly |
| `SILLAGE_PORT` | `5231` | HTTP port |
| `SILLAGE_DATA` | see below | Data directory; `/var/opt/sillage` in Docker |
| `SILLAGE_DSN` | `$SILLAGE_DATA/sillage.db` | SQLite path; relative paths are resolved from the data directory |
| `SILLAGE_MAX_UPLOAD_MB` | `30` | Maximum size of one attachment, in MiB |
| `SILLAGE_LOG_FORMAT` | `json` | `json` or `text` |
| `SILLAGE_LOG_LEVEL` | `info` | `debug`, `info`, `warn`, or `error` |
| `SESSION_SECRET` | generated automatically | Session-signing secret |
| `ENCRYPTION_SECRET` | generated automatically | AI API key encryption secret |

When running directly on the host, the application uses `/var/opt/sillage` by default if that directory exists; otherwise, it falls back to the current directory. Always set `SILLAGE_DATA` explicitly in production.

`SILLAGE_DSN`, `SESSION_SECRET`, and `ENCRYPTION_SECRET` support corresponding `_FILE` variables, such as `ENCRYPTION_SECRET_FILE=/run/secrets/encryption`. A direct value and its `_FILE` variable cannot be set at the same time. To use `_FILE` in a container, mount the file and pass the variable explicitly; the host environment is not passed through automatically. External databases and secret files are outside `SILLAGE_DATA` and must be included in the same backup and restore process.

The container entrypoint also supports `SILLAGE_UID` and `SILLAGE_GID`, both defaulting to `10001`. They adjust ownership of the mounted directory and run the process as a non-root user. Compose does not pass through these two variables; to customize them, explicitly change the Compose `environment` or use `docker run -e`.

Configure the AI provider, model, and API key in the application settings after signing in; they are not configured through process environment variables. Read [AI Usage and Privacy](ai.md) before configuring them.

## Run Locally

Go 1.25 is required. Before a production-style run, generate the embedded Web assets and include the version and commit in the release build:

```bash
pnpm --dir web install
pnpm --dir web build
VERSION="$(git describe --tags --exact-match)"
REVISION="$(git rev-parse HEAD)"
go build -ldflags "-X main.version=$VERSION -X main.revision=$REVISION" -o sillage ./cmd/sillage
SILLAGE_ADDR=127.0.0.1 SILLAGE_DATA="$HOME/.sillage" ./sillage
```

See the [Contributing Guide](../../CONTRIBUTING.md) for the development environment.

Identify a running binary with `./sillage --version`. Container images also include the OCI `version` and `revision` labels.

The Dockerfile pins the base-image digest, pnpm lockfile, and Go module checksums. Alpine system packages are still resolved from the repositories configured by the base image. When updating the base image or system packages, rebuild the image, document the reason, and update the pinned values in the same change.

## External HTTPS Entry Point

The operator-managed entry point should terminate TLS and overwrite the following request headers supplied by the client:

```text
X-Forwarded-Proto
X-Forwarded-Host
X-Forwarded-For
```

Do not simply append untrusted forwarding headers. Sillage uses `X-Forwarded-Proto` to decide whether to mark Cookies as Secure and uses `X-Forwarded-For` for sign-in rate limiting. Only the operator-managed entry point should be able to reach the backend port. Its installation, credentials, DNS, TLS certificates, and network path must be configured outside this repository.

## Probes and Upgrades

```bash
curl --fail http://localhost:5231/healthz
curl --fail http://localhost:5231/readyz
```

`healthz` checks only the process, while `readyz` also checks SQLite. Before upgrading:

1. Preserve the current image with a versioned tag, such as `docker tag sillage:latest sillage:rollback-YYYYMMDD`.
2. Follow [Data, Backup, and Recovery](data.md) to stop the service and back up the complete data directory.
3. Build and start the new image, then confirm that probes, sign-in, records, and attachments work correctly.
4. If the upgrade fails, stop the new instance, restore the corresponding data backup, and start the preserved old image.

If a startup migration fails, the service does not enter the ready state. An older binary may not be compatible with an upgraded database, so you cannot roll back only the image without restoring the matching data.

Probes do not require authentication, and `readyz` may include diagnostic text when a dependency fails. A public reverse proxy should allow only monitoring sources to access `/healthz` and `/readyz`; do not expose them as a public status page.
