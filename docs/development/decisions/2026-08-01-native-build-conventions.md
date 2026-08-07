# Native shared-module build conventions

## Context

The first Kotlin Multiplatform domain module declared its Android, desktop JVM,
Apple, compiler, SDK, and test configuration locally. Repeating that block in
application, data, synchronization, feature, and shared UI modules would allow
the supported target matrix and toolchain settings to drift. The documented
native dependency direction also had no executable repository check.

## Decision

The native Gradle workspace uses an included `build-logic` build. Shared Kotlin
modules apply one `sillage.kmp-library` convention, while a version catalog owns
the Android Gradle Plugin and Kotlin versions. The convention derives stable
namespaces from module paths and configures Android, desktop JVM, iOS device,
and iOS simulator targets plus common host tests.

The root `checkShared` task discovers convention modules and runs their desktop
tests. It also rejects project dependencies that point against the architecture:
core modules depend only on core, feature modules depend only on core, and
shared UI depends only on core, features, or shared UI. Platform applications
remain unrestricted composition roots.

The same root gate runs `checkNativeIdentity`. Native app icons originate from
the existing product mark under `apps/native/branding/`; generated iOS PNG,
macOS ICNS, and Windows ICO artifacts are committed because Windows and Linux
verification hosts cannot run Apple's conversion tools. The gate validates the
asset containers, iOS dimensions and alpha policy, platform wiring, and the
visible product version shared by Android, iOS, and desktop.

## Consequences

Shared module build files remain small and cannot silently select different SDK
or compiler targets. A target-matrix change has repository-wide impact and must
update the convention, dependency locks, verification metadata, documentation,
and native gate together. Platform packaging and framework publication remain
host responsibilities rather than convention behavior.
