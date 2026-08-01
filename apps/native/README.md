# Native applications

This directory is the Kotlin Multiplatform workspace for native Sillage
clients. Android is the first implemented application. The other application
directories deliberately contain only boundary documentation until their
implementation phases begin.

Shared domain, persistence, synchronization, and security code belongs under
`packages/kmp-core/`. Shared feature logic belongs under
`packages/kmp-features/`. Platform applications should contain composition,
platform adapters, packaging, and release configuration only.
