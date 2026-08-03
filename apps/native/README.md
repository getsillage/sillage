# Native applications

This directory is the Kotlin Multiplatform workspace for native Sillage clients.
Android is the server-connected native application. Windows, macOS, and iOS
share a functional device-local records workspace plus public Sillage server
bootstrap, initialization, sign-in, refresh, and sign-out foundations while
remote records integration continues.

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

The root `checkShared` task runs every shared module's desktop host tests,
checks dependency direction, and validates native application identity assets
and visible product-version consistency. `checkDesktop` adds Compose Desktop
host tests and production compilation. `checkDesktopPackage` builds and
verifies a DMG or MSI on its matching host OS. `checkIos` links static device
and simulator frameworks; the repository `make check-ios` target additionally
typechecks the Swift bridge and builds the unsigned Xcode simulator host.

Core modules may depend only on other core modules; feature modules may depend
only on core modules; shared UI may compose core, features, and other shared UI
modules. Application hosts remain composition roots.

## Application identity

`branding/` contains deterministic iOS and desktop app-icon SVG compositions
derived from the existing Sillage product mark. On macOS,
`branding/generate-icons.sh` regenerates the committed iOS PNG catalog, macOS
ICNS, and Windows ICO using `sips` and `iconutil`. Other hosts consume the
committed outputs and do not need Apple tooling. `checkNativeIdentity` verifies
catalog dimensions and alpha rules, ICNS/ICO container headers, host wiring,
and that Android, iOS, and desktop show the same product version.

## Public server discovery

`packages/kmp-core/network` maps the unauthenticated
`GET /api/v1/auth/bootstrap` response behind a host-neutral HTTP boundary.
Desktop uses the JDK HTTP client and iOS uses Foundation `NSURLSession`; the
shared application settings surface owns address draft, request identity,
result, and failure presentation. A successful check remembers only the
normalized server address in the private client snapshot. Portable JSON
backups deliberately do not export it.

The public check sends no records, credentials, cookies, or authorization
headers. After a successful check, the same Settings surface can initialize or
sign in to the instance. Access tokens and refresh cookies stay only in memory,
automatic platform cookie storage is disabled, and closing the application
requires signing in again. Use operator-managed HTTPS except for explicit
loopback or trusted-LAN engineering development. OS-backed session persistence
and synchronized records remain follow-up slices.
