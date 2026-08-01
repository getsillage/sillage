# Product monorepo and client layout

## Context

Sillage currently keeps the Go server at the repository root and has separate
top-level Web and Android clients. Adding iOS, Windows, and macOS directly to
that layout would multiply handwritten protocol mappings, platform-specific
synchronization code, release coupling, and application-wide state containers.
The repository needs stable application, reusable-package, contract, test, and
tooling boundaries before more clients are implemented.

## Decision

The product remains a monorepo. Independently buildable applications live under
`apps/`; reusable modules live under `packages/`; public wire definitions and
compatibility fixtures live under `contracts/`; cross-application verification
lives under `tests/`; and repository development tooling moves toward
`tooling/`.

The Web client remains React and TypeScript. Android, iOS, Windows, and macOS
converge on a Kotlin Multiplatform core and Compose Multiplatform UI. Platform
applications own lifecycle, packaging, signing, secure-storage adapters, file
integration, and operating-system behavior. Shared modules own domain rules,
local persistence, synchronization, transport mapping, and feature-scoped state.

The Go server will become a modular monolith organized by business capability.
Its current root packages move only after module boundaries and dependency
rules exist; a path-only move is not considered architectural migration.

Protocol definitions have one declared source of truth at a time. Existing
Protobuf definitions remain authoritative during the initial migration and move
under `contracts/`. HTTP-only surfaces and streaming events receive explicit
contract definitions before any later decision to adopt a complete OpenAPI
contract. Compatibility is expressed by protocol revision and capabilities,
not platform application versions.

## Consequences

Initial commits create some documented placeholder directories for clients and
shared packages that are not implemented yet. Buildable applications remain
independently releasable even though they share a repository. Cross-platform
features require contract and conformance tests in addition to package-local
tests. The migration must update path-aware CI, release, documentation, and
supply-chain tooling whenever a buildable application or contract moves.
