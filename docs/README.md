# Documentation Hub

Choose an entry point based on your task:

| Task | Document |
| --- | --- |
| Run and configure an instance | [Deployment Guide](user/deployment.md) |
| Back up, restore, or migrate data | [Data, Backup, and Recovery](user/data.md) |
| Configure AI and understand external data sharing | [AI Usage and Privacy](user/ai.md) |
| Get setup or usage help | [Support Guide](../SUPPORT.md) |
| Set up a development environment and validate changes | [Contributing Guide](../CONTRIBUTING.md) |
| Engineering standards, dual track, and verification registry | [Engineering Governance](development/governance.md) |
| Validate a stable release candidate and published artifacts | [Release Readiness](development/release-readiness.md) |
| Product and engineering red lines | [Constitution](development/constitution.md) |
| Understand module, data, and contract boundaries | [Architecture Guide](development/architecture.md) |
| Change native client structure or UI technology | [Multiplatform Client Architecture](development/multiplatform.md) |
| Change product semantics | [Product Guidance](development/product-guidance.md) |
| Change authentication, attachments, secrets, or external requests | [Security Development Boundaries](development/security.md) |
| Change synchronization behavior | [Sync API](development/api/sync.md) |
| Change REST routes or clients | [REST API Guide](development/api/README.md) |
| Change the Web interface | [Web Design Guidelines](development/design/README.md) |
| Build the Android app | [Sillage Android Guide](../apps/native/androidApp/README.md) |
| Download releases and review changes | [GitHub Releases](https://github.com/getsillage/sillage/releases) |
| Report a security issue | [Security Policy](../SECURITY.md) |

## Maintenance Principles

- Keep one canonical document for each type of information. Other locations should provide only a summary and a link.
- Document stable boundaries and procedures instead of mirroring implementation details that change frequently. If they conflict, use the linked source code as the source of truth.
- Update the relevant documentation in the same commit as any change to features, configuration, commands, contracts, or architecture.
- Do not keep completed implementation plans in the working tree indefinitely. Use Git history when they need to be revisited.
