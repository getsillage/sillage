# Shared device-local workspace and native hosts

## Context

The iOS and desktop paths were reserved application boundaries, so the native
architecture had no small end-to-end host outside Android. Reusing Android's
current persistence and root ViewModel directly would couple new clients to
Android framework APIs and preserve duplicate lifecycle behavior during the
shared-module migration.

Windows and macOS also need an installable prototype before server transport,
credential storage, and synchronization adapters are ready. That prototype
must remain honest about its device-local behavior and must not establish a
second record model or an incompatible persistence policy.

## Decision

`kmp-core:local-data` owns a versioned JSON client snapshot, record lifecycle
operations, optimistic version checks, and device-local appearance
preferences. It exposes platform-neutral ports for atomic string storage,
timestamps, and identifiers. The snapshot is private client persistence, not
the sync protocol or a user backup format.

User-initiated data transfer uses a separate versioned, credential-free backup
envelope. Restoration fully decodes and validates the envelope before replacing
the private snapshot, then rehydrates shared application state. Unsupported or
invalid backups leave current local data untouched. Desktop supplies only the
native save/open dialogs and file I/O for this shared policy.

`shared-ui:application` is the shared Compose composition root for the first
native host slice. It owns the responsive records list, detail, editor,
search, lifecycle actions, settings, localization, and unsaved-draft policy.
Platform hosts provide only lifecycle and storage adapters. Features that
require a server, including Ask and synchronization, remain unavailable until
their existing shared application ports have host implementations.

`iosApp` exports a static KMP framework consumed by a SwiftUI/UIKit lifecycle
host. It stores the same JSON snapshot as an atomic Application Support file and
uses Foundation for timestamps and record identifiers. Existing installs migrate
the legacy `NSUserDefaults` value only after the file adapter accepts the full
snapshot, so a failed write leaves the recovery source intact. The Xcode project
selects a device or simulator framework from Xcode's build environment rather
than duplicating application behavior in Swift.

`desktopApp` is the Windows/macOS host and may generate local DMG or MSI
packages. These packages are engineering verification artifacts, not official
release assets. Matching CI hosts verify package generation, while the release
workflow remains unchanged until code signing, macOS notarization, updates, and
release-candidate coverage are defined and verified.

Desktop native menus and operating-system close requests reuse the shared dirty
editor policy and localized discard confirmation rather than bypassing it.

## Consequences

Desktop and iOS can consume the same record behavior and Compose surface
without depending on Android code. A host cannot fork snapshot decoding,
record lifecycle rules, or conflict checks. Corrupt snapshots fail closed and
remain untouched so the user can recover or inspect them.

The first hosts are intentionally incomplete compared with Android: they do
not imply server compatibility, remote synchronization, Ask, attachment,
secure-credential, or production-distribution readiness. Adding those
capabilities requires adapters at the existing shared boundaries and the
corresponding platform and release verification.
