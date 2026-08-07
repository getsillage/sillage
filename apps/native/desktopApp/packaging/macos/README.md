# macOS packaging integration

Compose Desktop declares the `app.sillage.desktop` bundle identifier and
produces `Sillage-1.0.0.dmg` on a macOS host.

The application bundle uses the committed multi-resolution `Sillage.icns`
generated from the shared native brand source.

Run `make check-desktop-package` on macOS. Main CI runs the same gate on a
macOS runner and uploads the unsigned DMG as a short-lived engineering
artifact.

The macOS host persists only the refresh credential as a non-synchronizing
Generic Password item. It calls modern Security.framework `SecItem*` APIs
directly through JNA; it does not invoke the `security` command-line tool or
place token material in process arguments. The Keychain integration is covered
by a real round-trip, rotation, and deletion test on macOS.

Release signing, notarization, update behavior, and optional AppKit integration
remain release-readiness work.
