# Multiplatform Client Architecture

This document defines the stable client-technology and dependency boundaries
for Web, Android, iOS, Windows, and macOS. Product behavior remains governed by
[Product Guidance](product-guidance.md); wire behavior remains governed by the
[REST API Guide](api/README.md) and authored definitions under `contracts/`.

## Platform Strategy

| Platform | Primary UI | Shared runtime | Current state |
| --- | --- | --- | --- |
| Web | React and TypeScript | Web feature and infrastructure modules | Implemented |
| Android | Compose Multiplatform | Kotlin Multiplatform | Existing Compose app; shared-module extraction pending |
| iOS | Compose Multiplatform | Kotlin Multiplatform | Application and native-UI boundaries reserved |
| Windows | Compose Multiplatform | Kotlin Multiplatform | Desktop packaging and integration boundaries reserved |
| macOS | Compose Multiplatform | Kotlin Multiplatform | Desktop packaging and integration boundaries reserved |

React components are not shared with Compose. The clients share public wire
contracts, language-neutral conformance fixtures, terminology, behavior rules,
and semantic design tokens. Native clients additionally share Kotlin domain,
application, persistence, synchronization, feature state, and reusable Compose
UI modules.

## Native Dependency Direction

```text
Android / iOS / Desktop application hosts
                  |
        shared Compose UI and features
                  |
          application use cases
                  |
             domain model
                  ^
                  |
 network / database / security platform adapters
```

Dependencies point inward. Domain code does not depend on Compose, HTTP,
SQLite, generated DTOs, or operating-system APIs. Generated transport types are
mapped at the network boundary. Platform applications compose modules and
provide adapters; shared modules never depend on an application host.

## Compose and Native UI Boundary

Compose Multiplatform is the default for native feature screens, navigation,
design-system components, and application structure. A platform-native surface
may use SwiftUI, UIKit, AppKit, WinUI, or Android framework UI when at least one
of these conditions applies:

- the surface is an operating-system integration with no adequate Compose API;
- the native control provides materially better accessibility or input behavior;
- platform-standard window, menu, file, share, authentication, or lifecycle
  behavior would otherwise be degraded;
- measured performance cannot be met by the shared implementation.

A native surface consumes the same feature state, events, and use cases as its
Compose equivalent. It does not implement its own synchronization algorithm,
database rules, API mapping, conflict policy, or domain validation. Duplicating
an entire feature implementation requires an ADR with ownership and conformance
tests.

## Shared Native Modules

`packages/kmp-core/` owns domain, application, network, database,
synchronization, and security foundations. Its buildable `domain` and
`application` modules produce Android, desktop JVM, and Apple targets from
common source. `application` declares inward-facing use cases and repository
ports; platform adapters implement those ports. `packages/kmp-features/` owns
feature-scoped state for authentication, records, Ask, settings, and manual
synchronization. `apps/native/shared-ui/` owns reusable Compose presentation and
the shared application shell.

The buildable `kmp-features:records` module is the first shared feature slice.
It depends only on `kmp-core:domain` and owns list/filter/calendar query policy;
Android remains its first host and adapter provider. Feature state holders move
into the module incrementally without moving transport or persistence details
with them.

Every shared module applies the repository `sillage.kmp-library` convention.
The convention owns target and compiler configuration; module files own only
their dependencies and optional capability plugins. `checkShared` discovers
all convention modules, runs their desktop host tests, and enforces the native
dependency direction as an executable architecture rule. Platform hosts do not
apply this convention because they own platform lifecycle and packaging.

There is no global feature ViewModel. Each feature owns its state holder and
exposes an explicit contract. Application-wide state is limited to the active
workspace, authenticated session, locale, theme, navigation root, and a summary
of synchronization status.

The first extracted records holder governs load-more state. Its immutable
transitions capture and validate source, client context, filter, cache
generation, cursor, and request identity so late responses cannot cross query
boundaries. The holder is available to every native host; Android retains only
transitional accessors while the rest of records state is migrated.

The records application boundary has separate ports for a consistent local
snapshot and a server-backed page. Shared callers use semantic scope and cursor
values; platform adapters own storage transactions, HTTP parameter mapping, and
transport DTO conversion.

Records refresh is the second extracted state slice. Its shared holder owns
loading/failure status and request identity independently from pagination, while
validating the same query context before replacing the visible snapshot.

## Platform Hosts

- `apps/native/androidApp/` is the Android application and current migration
  source.
- `apps/native/iosApp/` owns the Xcode host, Apple lifecycle, signing, Keychain,
  file and share integration, and optional SwiftUI/UIKit adapters.
- `apps/native/desktopApp/` owns the shared desktop executable plus Windows and
  macOS packaging, signing, secure-storage, window, menu, shortcut, and optional
  native-UI adapters.

Platform hosts may depend on shared modules. They must not depend directly on
another platform host.

## Local Data and Synchronization

The native clients converge on one shared local-first model: local writes and
outbox mutations commit atomically; pulled resources and cursor advancement
commit atomically; conflicts are durable and explicitly resolved; attachment
bytes use a staged upload path outside structured sync payloads; and data is
partitioned by Sillage workspace or instance.

The current Android implementation remains the behavioral reference while it
is extracted. Its application-wide ViewModel, handwritten transport client, and
Android-specific persistence classes are migration sources, not target module
boundaries. New cross-platform behavior belongs in the shared modules rather
than expanding those containers.

Extraction proceeds in vertical slices that remain consumed by Android. The
first slice moves the record entity and its active-lifecycle policy into
`packages/kmp-core/domain`; Android REST, local persistence, feature state, and
Compose UI all use that shared type. Platform or transport models must not
reintroduce another record entity.

## Verification

Shared domain and synchronization modules require common unit tests and
language-neutral fixtures under `contracts/fixtures/` and `tests/conformance/`.
Each platform retains packaging, signing, accessibility, lifecycle, and device
tests. Platform-native UI replacements must pass the same feature contract and
acceptance scenarios as the shared Compose surface.
