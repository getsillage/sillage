# Shared authentication UI

Buildable Compose Multiplatform authentication UI shared by Android, iOS,
Windows, and macOS hosts.

`SillageLoginForm` consumes `AuthFeatureStateHolder` directly for username and
password drafts. `SillagePasswordField` owns password visibility presentation,
IME submission, and accessibility labels. `SillageAuthButtonContent` owns the
loading/icon/action-content treatment used by authentication actions.

Hosts supply localized strings, icons, root loading state, navigation, and
callbacks. Brand assets, secure session persistence, protocol clients, and
platform lifecycle remain outside this module.
