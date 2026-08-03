# iOS application

The iOS host embeds the static `SillageShared` Kotlin Multiplatform framework
in a small SwiftUI application. The framework exposes the shared Compose
device-local records workspace and supplies Apple adapters for an atomic
Application Support snapshot file, one-time `NSUserDefaults` migration,
Foundation timestamps and UUIDs, public server bootstrap requests through
`NSURLSession`, plus system JSON document pickers for portable backup export
and restore.

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

This first slice remains device-local for records. Settings can validate and
remember a Sillage HTTPS server through its public bootstrap endpoint without
sending records or credentials. Server authentication, synchronization, Ask,
attachments, Keychain credentials, accessibility/device journeys, signing,
and App Store packaging remain outside the implemented host. The locally built
app is an engineering artifact, not an official release asset.
