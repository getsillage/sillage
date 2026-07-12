<p align="center">
  <strong>English</strong> | <a href="README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <img src="web/public/sillage-icon.svg" alt="Sillage" width="96" height="96" />
</p>

<h1 align="center">Sillage</h1>

<p align="center">
  <a href="https://github.com/getsillage/sillage/actions/workflows/ci.yml"><img src="https://github.com/getsillage/sillage/actions/workflows/ci.yml/badge.svg" alt="CI status" /></a>
  <a href="https://github.com/getsillage/sillage/releases"><img src="https://img.shields.io/github/v/release/getsillage/sillage?display_name=tag" alt="Latest release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT License" /></a>
</p>

Sillage is a self-hosted, single-user space for capturing everyday moments, revisiting your history, and using AI to generate summaries and answers grounded in your own records.

The first time an instance is opened, you create its only account. After that, authentication is required to access records, attachments, summaries, and Ask conversations.

## Features

- Write records in Markdown, upload images or files, and recover unsaved drafts.
- Revisit records through lists, a calendar, and search, then organize them with favorites and archives.
- Configure Anthropic, OpenAI, or an OpenAI-compatible service to generate record summaries and source-grounded answers.
- Use the responsive Web interface or the native Android client in English or Simplified Chinese, with online and offline writing and manual record sync.

Sillage does not provide multi-user collaboration, public profiles, social sharing, background sync, complete offline attachment synchronization, or an official hosted service. Public ingress, TLS, DNS, tunneling, CDNs, and other edge-network services are infrastructure operated separately by the deployer. They are outside the Sillage product and repository, which does not bundle third-party network connectors, credentials, or vendor-specific deployment configuration.

## Quick Start

For a stable deployment, choose a release tag from [GitHub Releases](https://github.com/getsillage/sillage/releases) instead of building `main` directly. The following example builds from a release checkout and exposes the service only on the local machine:

```bash
git clone https://github.com/getsillage/sillage.git
cd sillage
git checkout vX.Y.Z
VERSION="$(git describe --tags --exact-match)"
REVISION="$(git rev-parse HEAD)"
docker build \
  --build-arg VERSION="$VERSION" \
  --build-arg REVISION="$REVISION" \
  -t "sillage:$VERSION" \
  -f scripts/Dockerfile .
docker run --rm \
  -p 127.0.0.1:5231:5231 \
  -v "$HOME/.sillage:/var/opt/sillage" \
  "sillage:$VERSION"
```

Open `http://localhost:5231` and follow the prompts to create the instance's only account.

Health checks:

```bash
curl http://localhost:5231/healthz
curl http://localhost:5231/readyz
```

Data is stored in `$HOME/.sillage`. Stop the service before upgrading, migrating, or backing it up, and copy the entire directory rather than only `sillage.db`. See [Data, Backup, and Recovery](docs/user/data.md) for the complete procedure.

See the [Deployment Guide](docs/user/deployment.md) for Docker Compose, reverse proxy, environment variable, and public deployment instructions.

## Architecture

- Go and Echo monolithic backend
- SQLite database and local attachment storage
- React, TypeScript, and Vite Web client embedded in the Go binary
- Kotlin and Jetpack Compose Android client
- Protobuf API contract exposed through both REST v1 and Connect v1

See the [Architecture Guide](docs/development/architecture.md) for detailed boundaries and sources of truth.

## Documentation

| Goal | Resource |
| --- | --- |
| Deploy an instance | [Deployment Guide](docs/user/deployment.md) |
| Back up, restore, or migrate data | [Data, Backup, and Recovery](docs/user/data.md) |
| Configure AI and understand external data handling | [AI Usage and Privacy](docs/user/ai.md) |
| Get setup or usage help | [Support Guide](SUPPORT.md) |
| Contribute to the project | [Contributing Guide](CONTRIBUTING.md) |
| Understand system boundaries | [Architecture Guide](docs/development/architecture.md) |
| Modify authentication, attachments, or secrets | [Security Development Boundaries](docs/development/security.md) |
| Modify sync clients | [Sync API](docs/development/api/sync.md) |
| Modify the product or interface | [Product Guidance](docs/development/product-guidance.md) / [Web Design Guidelines](docs/development/design/README.md) |
| Build the Android client | [Sillage Android Guide](android/README.md) |
| Download releases and view changes | [GitHub Releases](https://github.com/getsillage/sillage/releases) |

See the [Documentation Hub](docs/README.md) for the complete index. Report security issues privately according to the [Security Policy](SECURITY.md).

## Development

The backend requires Go 1.25. Node.js 24 and pnpm 11.9 are recommended for the Web client; Android development requires JDK 17 and the Android SDK. Setup, local development, generated artifacts, and validation requirements are documented in the [Contributing Guide](CONTRIBUTING.md).

## Contributing

Before submitting a change, read the [Contributing Guide](CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md). Keep changes within Sillage's scope as a private, single-user record space, and include the relevant tests and documentation.

## Security

Sillage handles private records, attachments, login sessions, and encrypted AI API keys. Do not disclose vulnerabilities or real user data in public issues. Follow the private reporting process in the [Security Policy](SECURITY.md).

## License

Sillage is licensed under the [MIT License](LICENSE).
