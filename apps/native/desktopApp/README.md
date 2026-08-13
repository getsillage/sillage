# Desktop application

The Compose Desktop host runs on Windows and macOS. It supplies atomic
filesystem storage, a data-directory instance lock, platform time and record
identifiers, data-folder integration, native JSON backup export and validated
restore, window lifecycle, native menus and shortcuts, guarded window exit,
public bootstrap and authentication HTTP transport, and native installer
configuration to the shared native application.

Native distributions use the committed branded `Sillage.icns` on macOS and
`Sillage.ico` on Windows. The UI reports product version `0.3.1`, matching the
Android and iOS clients. The engineering installer filename retains jpackage's
independent `1.0.0` package version because that tool requires a positive major
version; it is not a claim that the desktop client is a stable 1.0 release.

Before opening the repository, the host locks `client-v1.json.lock`. A concurrent
second instance shows a startup error and exits instead of becoming another
writer. The empty marker file is intentionally retained; the operating-system
lock is released when the application closes or its process terminates.

The Data settings section uses operating-system save/open dialogs. Restore asks
for confirmation before replacing records and appearance, and invalid backups
leave the current local snapshot untouched.

The About section opens the generated desktop runtime dependency inventory from
`src/main/resources/third_party_notices.txt`. The same resource is packaged in
the application JAR and native DMG/MSI image; desktop checks reject notice drift
against `gradle.lockfile`.

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

The current product slice is a functional local-first records workspace. The
Settings screen can validate and remember a Sillage server through its public
bootstrap endpoint, initialize the single account, sign in, and sign out. An
authenticated manual sync pushes pending record changes before pulling all
current server records and presents explicit device/server conflict resolution.
The JDK transport performs cancellable JSON requests and preserves only the
explicit refresh cookie needed by the shared repository. On macOS, the host
stores the refresh credential in a non-synchronizing Keychain Generic Password
item. On Windows, it stores the same value as a non-roaming Generic Credential
in the current user's Credential Manager. Both are keyed by normalized server
URL and can restore the session after public bootstrap on a later launch. Access
tokens and passwords remain memory-only. Successful sign-in, restored
authentication, every later foreground entry, and a confirmed recovery of a
local non-loopback network interface trigger a push-then-pull sync. The host
polls only local interface state and does not probe the configured server.
Routine completion stays silent, while conflicts and failures remain visible.
Authenticated users can open Ask to load and switch conversations, choose record
context, stream or stop answers, navigate answer variants and source records,
regenerate an answer, and save it as a local record. The same JDK-backed remote
repository factory used for authentication creates the Ask client; the shared
controller rejects results from obsolete sessions or destinations. Attachment
integration remains follow-up work and must reuse KMP application ports instead
of introducing desktop-only business behavior.
