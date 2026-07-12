# Sillage Android Guide

The native Android client supports Android 8.0 and later. It can connect to a self-hosted instance or save records offline on the device.

Download released APKs from [GitHub Releases](https://github.com/getsillage/sillage/releases). Before installing, verify the version, SHA-256 checksum, and signature information in the Release. The server and Android client should use the same release version or a version combination explicitly documented as compatible in the release notes.

## Connecting to an Instance

Start the Sillage service and make sure the phone can reach its address:

- From an emulator to the host: `http://10.0.2.2:5231`
- From a physical device to a host on the LAN: for example, `http://192.168.1.10:5231`
- For a public instance: use an HTTPS reverse proxy or Tunnel address

Both online and offline modes support records, calendar, search, favorites, archives, AI settings, summaries, and Ask. Online mode additionally supports initialization and sign-in, attachment uploads, and authenticated downloads. Local data can be imported and exported, and synchronization can be run manually as a pull, push, or two-way sync.

The record editor supports Markdown editing and preview. The preview supports core CommonMark syntax, strikethrough, tables, task lists, and single line breaks. Raw HTML is not executed; image syntax is displayed as an attachment or external link that can be opened safely.

The app currently provides neither automatic background sync nor push notifications, and offline attachment bytes and metadata are not fully synchronized. An Android export is not a substitute for a complete backup of the server's data directory.

"Pull" reads all syncable data from the server and merges it into the device. "Push" currently uploads only local records. "Two-way sync" pushes first and then performs a full pull. When a version conflict occurs, the app displays only the conflict count and retains the pending local changes; it does not yet provide an in-app merge interface. Do not keep retrying to force an overwrite. Preserve a local export first, verify the record on the server, and then resolve it.

## Build and Test

JDK 17 and Android SDK 35 are required:

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The debug APK is located at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device or emulator:

```bash
cd android
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Release Signing

The application ID is `app.sillage`. Signing files are not committed to the repository. Under `android/`, prepare `release.keystore` and `signing.properties`:

```properties
storeFile=release.keystore
storePassword=...
keyAlias=sillage-release
keyPassword=...
```

Build and verify the release APK:

```bash
cd android
./gradlew :app:assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
zipalign -c -v 4 app/build/outputs/apk/release/app-release.apk
```

The release build uses this keystore only when the local signing configuration exists. Do not publish artifacts with unverified signatures, and do not commit APK/AAB files, keystores, `signing.properties`, or `local.properties`.

## Security Boundaries

The app permits cleartext HTTP for LAN and emulator development; production instances should use HTTPS only. Login sessions and offline data are protected through Android Keystore, but exported JSON contains sensitive data in plaintext and should be shared and stored only in restricted locations.

Attachment links accept only standard external `http(s)` URLs or same-origin `/file/attachments/...` paths. The app downloads protected attachments to its cache with authentication, then passes them to the system viewer through a read-only FileProvider URI.

See the [Contributing Guide](../CONTRIBUTING.md) for the complete development gates, and the [Deployment Guide](../docs/user/deployment.md) and [Data, Backup, and Recovery](../docs/user/data.md) for server deployment and data security.
