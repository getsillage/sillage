# macOS packaging integration

Compose Desktop declares the `app.sillage.desktop` bundle identifier and
produces `Sillage-1.0.0.dmg` on a macOS host.

Run `make check-desktop-package` on macOS. Main CI runs the same gate on a
macOS runner and uploads the unsigned DMG as a short-lived engineering
artifact.

Release signing, notarization, Keychain-backed online credentials, update
behavior, and optional AppKit integration remain release-readiness work.
