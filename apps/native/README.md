# Native applications

This directory is the Kotlin Multiplatform workspace for native Sillage
clients. Android is the first implemented application. The other application
directories deliberately contain only boundary documentation until their
implementation phases begin.

Shared domain, persistence, synchronization, and security code belongs under
`packages/kmp-core/`. Shared feature logic belongs under
`packages/kmp-features/`. Platform applications should contain composition,
platform adapters, packaging, and release configuration only.

Compose Multiplatform is the default UI technology for Android, iOS, Windows,
and macOS. Platform-native UI remains allowed when it provides materially
better system integration, accessibility, performance, or platform-standard
interaction. Native UI must consume shared feature state and use cases instead
of duplicating domain, persistence, synchronization, or protocol behavior.
