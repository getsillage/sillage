# iOS application

The iOS host embeds the static `SillageShared` Kotlin Multiplatform framework
in a small SwiftUI application. The framework exposes the shared Compose
device-local records workspace and supplies Apple adapters for an atomic
Application Support snapshot file, one-time `NSUserDefaults` migration,
Foundation timestamps and UUIDs, public bootstrap and authentication requests
through `NSURLSession`, plus system JSON document pickers for portable backup
export and restore. Requests disable automatic cookie handling so refresh
cookies remain inside the shared repository boundary. Access tokens stay only
in memory; the host persists only the refresh credential as a
Security.framework Generic Password item using
`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`.

The Xcode target compiles the branded `Assets.xcassets/AppIcon.appiconset` and
reports its `MARKETING_VERSION` through the shared Settings surface. The icon
catalog is generated from `../branding/sillage-app-icon-ios.svg`; its App Store
image is an opaque 1024 px PNG and all phone/tablet renditions are committed for
deterministic builds.

On first launch after this storage upgrade, the host copies the legacy defaults
value into `Library/Application Support/Sillage/client-v1.json` and clears the
legacy key only after the atomic file write succeeds.

Build the framework and unsigned simulator host from the repository root:

```bash
make check-ios
```

Open `Sillage.xcodeproj` for interactive development. Its shared `Sillage`
scheme invokes `:iosApp:prepareAppleFrameworkForXcode`, which selects and copies
the static framework matching Xcode's Apple architecture and build type.

Records remain local-first in this slice. Settings can validate and remember a
Sillage HTTPS server, initialize the single account, sign in, refresh an expired
access token, and sign out. An authenticated manual sync pushes pending record
changes before pulling all current server records and presents explicit
device/server conflict resolution. On launch, the host checks the remembered
server publicly and restores a session from a device-bound Keychain refresh
credential when one exists; invalid or expired credentials return to sign-in.
The credential does not synchronize to other devices and is excluded from the
record snapshot and portable backup. Automatic sync, Ask, attachments,
accessibility/device journeys, signing, and App Store packaging remain outside
the implemented host. A locally built app is an engineering artifact, not an
official release asset.
official release asset.
