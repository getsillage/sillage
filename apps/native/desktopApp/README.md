# Desktop application

The Compose Desktop host runs on Windows and macOS. It supplies atomic
filesystem storage, a data-directory instance lock, platform time and record
identifiers, data-folder integration, native JSON backup export and validated
restore, window lifecycle, native menus and shortcuts, guarded window exit, and
native installer configuration to the shared native application.

Before opening the repository, the host locks `client-v1.json.lock`. A concurrent
second instance shows a startup error and exits instead of becoming another
writer. The empty marker file is intentionally retained; the operating-system
lock is released when the application closes or its process terminates.

The Data settings section uses operating-system save/open dialogs. Restore asks
for confirmation before replacing records and appearance, and invalid backups
leave the current local snapshot untouched.

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
