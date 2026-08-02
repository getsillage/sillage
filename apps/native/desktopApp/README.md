# Desktop application

Compose Desktop host for Windows and macOS. It supplies atomic filesystem
storage, platform time and record identifiers, data-folder integration, window
lifecycle, and native installer configuration to the shared native application.

Run the development application:

```bash
./gradlew :desktopApp:run
```

Build the installer supported by the current host:

```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
```

The current product slice is a functional device-local records workspace. It
does not yet connect to a Sillage server, synchronize, store credentials, open
attachments, or run Ask. Those integrations must reuse KMP application ports
instead of introducing desktop-only business behavior.
