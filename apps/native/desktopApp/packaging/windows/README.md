# Windows packaging integration

Compose Desktop declares a stable upgrade UUID and produces
`Sillage-1.0.0.msi` with Start Menu and desktop shortcut integration on a
Windows host.

The executable and installer use the committed 256 px `Sillage.ico` generated
from the shared native brand source.

Run `cd apps/native; .\gradlew.bat checkDesktopPackage` from PowerShell on
Windows. Main CI runs the same Gradle gate on a Windows runner and uploads the
unsigned MSI as a short-lived engineering artifact.

The Windows host persists only the refresh credential as a
`CRED_TYPE_GENERIC`, `CRED_PERSIST_LOCAL_MACHINE` item in the current user's
Credential Manager. It calls `CredReadW`, `CredWriteW`, `CredDeleteW`, and
`CredFree` directly through JNA; it does not invoke `cmdkey`, PowerShell, or
another command-line credential bridge. A conditional Windows test exercises
real round-trip, rotation, deletion, and invalid-value behavior.

Release signing, update behavior, and optional WinUI integration remain
release-readiness work.
