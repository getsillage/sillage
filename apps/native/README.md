# Native applications

This directory is the Kotlin Multiplatform workspace for native Sillage clients.
Android is the server-connected native application. Windows, macOS, and iOS
share a functional device-local records workspace while online integration
continues.

Shared domain, persistence, synchronization, and security code belongs under
`packages/kmp-core/`. Shared feature logic belongs under
`packages/kmp-features/`. Platform applications contain composition, platform
adapters, packaging, and release configuration only.

Compose Multiplatform is the default UI technology for Android, iOS, Windows,
and macOS. Platform-native UI remains allowed when it provides materially
better system integration, accessibility, performance, or platform-standard
interaction. Native UI must consume shared feature state and use cases instead
of duplicating domain, persistence, synchronization, or protocol behavior.

## Build conventions

Shared modules apply the `sillage.kmp-library` convention from `build-logic/`.
It owns the Android, desktop JVM, and Apple target matrix, SDK levels, JVM
target, namespace derivation, and common-test dependency. Module build files
declare only module-specific dependencies and plugins.

The root `checkShared` task runs every shared module's desktop host tests and
checks dependency direction. `checkDesktop` adds Compose Desktop host tests
and production compilation. `checkIos` links static device and simulator
frameworks; the repository `make check-ios` target additionally typechecks the
Swift bridge and builds the unsigned Xcode simulator host.

Core modules may depend only on other core modules; feature modules may depend
only on core modules; shared UI may compose core, features, and other shared UI
modules. Application hosts remain composition roots.
