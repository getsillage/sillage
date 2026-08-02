# iOS application

The iOS host embeds the static `SillageShared` Kotlin Multiplatform framework
in a small SwiftUI application. The framework exposes the shared Compose
device-local records workspace and supplies Apple adapters for `NSUserDefaults`,
Foundation timestamps, and UUIDs.

Build the framework and unsigned simulator host from the repository root:

```bash
make check-ios
```

Open `Sillage.xcodeproj` for interactive development. Its shared `Sillage`
scheme invokes `:iosApp:prepareAppleFrameworkForXcode`, which selects and copies
the static framework matching Xcode's Apple architecture and build type.

This first slice is device-local. Server authentication, synchronization, Ask,
attachments, Keychain credentials, sharing, accessibility/device journeys,
signing, and App Store packaging remain outside the implemented host. The
locally built app is an engineering artifact, not an official release asset.
