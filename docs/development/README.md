# Development Documentation

See the root [Contributing Guide](../../CONTRIBUTING.md) for development setup, change workflows, generated artifacts, and validation commands. This directory contains only engineering decisions that require long-term maintenance:

- [Engineering Governance](governance.md): standards registry, dual contribution tracks, Make/CI verification, commits, Dependabot, and severity levels.
- [Release Readiness](release-readiness.md): supported environments, scale budgets, release-candidate acceptance, artifact verification, and remote controls.
- [Constitution](constitution.md): product identity and non-negotiable red lines.
- [Architecture Guide](architecture.md): module responsibilities, request paths, data boundaries, and sources of truth.
- [Product Guidance](product-guidance.md): product scope, terminology, and AI behavior boundaries.
- [Security Development Boundaries](security.md): authentication, attachments, secrets, external requests, and sensitive-data constraints.
- [Sync API](api/sync.md): offline sync, idempotency, and conflict semantics.
- [REST API Guide](api/README.md): REST authentication, error model, route boundaries, and versioning rules.
- [Web Design Guidelines](design/README.md): interface direction, component constraints, and acceptance requirements.
- [Architecture Decision Records](decisions/): significant cross-module decisions (Context / Decision / Consequences only).

Deployment, data maintenance, and external AI data handling belong in the user documentation. See the [Deployment Guide](../user/deployment.md), [Data, Backup, and Recovery](../user/data.md), and [AI Usage and Privacy](../user/ai.md).

Path-to-gate mapping for automation lives in [`scripts/change-matrix.yml`](../../scripts/change-matrix.yml). Unified verification entry: root `Makefile`.

When implementing a significant, cross-module, hard-to-reverse technical choice, add `docs/development/decisions/YYYY-MM-DD-<topic>.md` in the same commit. Record only Context, Decision, and Consequences, and link any superseded decision. Do not use ADRs for task plans, routine implementation choices, or TODOs; use Issues for pending work and Git history for completed work.
