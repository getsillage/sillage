# Limited Dependabot pull requests

**Date:** 2026-07-30

## Context

Dependabot ecosystems were configured for Go, npm, Gradle, Docker, and GitHub Actions, but `open-pull-requests-limit` was set to `0`, so the dependency policy existed only on paper. Fully opening unlimited PRs would flood a small maintainer queue.

## Decision

Enable Dependabot with a **low open PR limit (2 per ecosystem)** and keep a weekly schedule. All dependency PRs must pass the same CI gates as human changes. Grouping remains optional and can be tightened later if noise remains high.

## Consequences

- Dependency and security updates surface as normal PRs again.
- Maintainers must budget light weekly review time.
- If noise becomes unacceptable, reduce ecosystems or group updates rather than setting the limit back to zero without an ADR update.
