# Windows packaging integration

Compose Desktop declares a stable upgrade UUID and produces
`Sillage-1.0.0.msi` with Start Menu and desktop shortcut integration on a
Windows host.

Run `cd apps/native; .\gradlew.bat checkDesktopPackage` from PowerShell on
Windows. Main CI runs the same Gradle gate on a Windows runner and uploads the
unsigned MSI as a short-lived engineering artifact.

Release signing, Credential Manager-backed online credentials, update behavior,
and optional WinUI integration remain release-readiness work.
