# Kotlin Multiplatform local data

`local-data` owns the portable native-client snapshot codec and local record
repository. Platform hosts provide atomic string storage plus time and identity
values; this module owns schema validation, domain mapping, optimistic version
checks, record lifecycle rules, and client preference persistence.

It intentionally does not own a filesystem path, `NSUserDefaults`, Keychain,
Windows Credential Manager, or Android storage APIs.
