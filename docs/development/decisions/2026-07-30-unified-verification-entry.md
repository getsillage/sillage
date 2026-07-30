# Unified verification entry

**Date:** 2026-07-30

## Context

Verification commands lived only as tables in `CONTRIBUTING.md` and as duplicated steps in `.github/workflows/ci.yml`. Contributors and coding agents reimplemented ad-hoc subsets; drift between local and CI was likely as gates grew (Proto, Docker context, E2E, docs).

## Decision

1. The repository root `Makefile` is the single human and agent entry for verification targets (`check`, `check-go`, `check-web`, …).
2. Non-trivial logic lives under `scripts/` (shell or Node) and is invoked by Make and CI.
3. Path-to-gate mapping lives in `scripts/change-matrix.yml`, resolved by `scripts/affected.mjs`.
4. CI workflows call the same Make targets or scripts instead of inlining long command lists where practical.

## Consequences

- Adding a gate means updating the script/Make target once, then wiring CI to call it.
- Contributors learn `make check` / `make check-affected` instead of a long command menu.
- Agents should default to affected gates and escalate to full `make check` for release-shaped work.
- Make becomes a required local tool (universally available on CI images and developer machines).
