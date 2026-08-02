# Desktop application

Compose Desktop host for Windows and macOS. It supplies atomic filesystem
storage, platform time and record identifiers, data-folder integration, window
lifecycle, and native installer configuration to the shared native application.

Run the development application:

```bash
./gradlew :desktopApp:run
```

Run shared tests and desktop production compilation:

```bash
make check-desktop
```

Build and verify the installer on macOS:

```bash
make check-desktop-package
```

On Windows PowerShell, invoke the same Gradle gate directly:

```powershell
cd apps/native
.\gradlew.bat checkDesktopPackage
```

The packaging gate expects `Sillage-1.0.0.dmg` on macOS or
`Sillage-1.0.0.msi` on Windows and rejects missing or unexpectedly small
artifacts. CI runs it on both host operating systems and retains the unsigned
packages temporarily for engineering inspection. They are not official
release assets.

The current product slice is a functional device-local records workspace. It
does not yet connect to a Sillage server, synchronize, store credentials, open
attachments, or run Ask. Those integrations must reuse KMP application ports
instead of introducing desktop-only business behavior.
