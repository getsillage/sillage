# Product monorepo and client layout

## Context

At the time of this decision, Sillage kept the Go server at the repository root
and had separate top-level Web and Android clients. Adding iOS, Windows, and
macOS directly to that layout would multiply handwritten protocol mappings,
platform-specific
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
converge on a Kotlin Multiplatform core, with Compose Multiplatform as their
primary UI technology. Platform-native UI and framework interop remain allowed
when they provide materially better system integration, accessibility,
performance, or platform-standard interaction. Native surfaces consume shared
feature state and use cases and do not duplicate domain, persistence,
synchronization, or protocol behavior.

Platform applications own lifecycle, packaging, signing, secure-storage
adapters, file integration, native UI adapters, and operating-system behavior.
Shared modules own domain rules, local persistence, synchronization, transport
mapping, feature-scoped state, and reusable Compose UI.

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
tests. Shared UI reduces duplication but does not force platform applications
into lowest-common-denominator interaction. A full feature fork or duplicated
business implementation requires a separate ADR. The migration must update
path-aware CI, release, documentation, and supply-chain tooling whenever a
buildable application or contract moves.
