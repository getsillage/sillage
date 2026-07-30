# Dual-track contribution model

**Date:** 2026-07-30

## Context

Open-source contribution docs describe fork/PR workflow and protected `main`. Local maintainer agent rules (`CLAUDE.md`) allow direct commits to `main` for the primary maintainer workflow. Without an explicit model, agents and docs appeared to contradict each other.

## Decision

Document two tracks under [Governance](../governance.md):

1. **External track:** pull requests; must satisfy templates and CI.
2. **Maintainer/agent track:** may push `main` when agent rules say so, but **must still satisfy the same quality gates** (Make/CI equivalent, no secrets, docs with contracts).

No third informal track is recognized.

## Consequences

- `CONTRIBUTING.md` remains the external source of truth for process.
- `CLAUDE.md` remains a thin session layer for agents and must not invent product law.
- Branch protection and CI stay mandatory for PR merges; maintainer pushes remain a trust privilege, not a license to skip tests.
