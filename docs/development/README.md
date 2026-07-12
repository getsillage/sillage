# Development Documentation

See the root [Contributing Guide](../../CONTRIBUTING.md) for development setup, change workflows, generated artifacts, and validation commands. This directory contains only engineering decisions that require long-term maintenance:

- [Architecture Guide](architecture.md): module responsibilities, request paths, data boundaries, and sources of truth.
- [Product Guidance](product-guidance.md): product scope, terminology, and AI behavior boundaries.
- [Security Development Boundaries](security.md): authentication, attachments, secrets, external requests, and sensitive-data constraints.
- [Sync API](api/sync.md): offline sync, idempotency, and conflict semantics.
- [REST API Guide](api/README.md): REST authentication, error model, route boundaries, and versioning rules.
- [Web Design Guidelines](design/README.md): interface direction, component constraints, and acceptance requirements.

Deployment, data maintenance, and external AI data handling belong in the user documentation. See the [Deployment Guide](../user/deployment.md), [Data, Backup, and Recovery](../user/data.md), and [AI Usage and Privacy](../user/ai.md).

When implementing a significant, cross-module, hard-to-reverse technical choice, add `docs/development/decisions/YYYY-MM-DD-<topic>.md` in the same commit. Record only Context, Decision, and Consequences, and link any superseded decision. Do not use ADRs for task plans, routine implementation choices, or TODOs; use Issues for pending work and Git history for completed work. Do not create an empty directory or template before the first real decision exists.
