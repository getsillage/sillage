# Shared authentication UI

Buildable Compose Multiplatform authentication UI shared by Android, iOS,
Windows, and macOS hosts.

`SillageLoginForm` consumes `AuthFeatureStateHolder` directly for username and
password drafts. `SillagePasswordField` owns password visibility presentation,
IME submission, and accessibility labels. `SillageAuthButtonContent` owns the
loading/icon/action-content treatment used by authentication actions.
`SillageInitializeForm` consumes the same feature aggregate for username,
display-name, and password drafts and owns the account-creation field layout.
`SillageServerForm` owns server-address entry, IME submission, loading/action
presentation, and the secondary offline action while hosts execute protocols.
`SillageModeOptionCard` owns the mode-selection card layout and semantic card
colors while hosts supply localized content, accent colors, icons, and callbacks.
`SillageAuthScaffold` owns the centered scroll/IME layout, heading semantics,
inline-error placement, and content spacing. `SillageAuthHeader` owns brand and
language-action layout while hosts supply brand resources and localized content.

Hosts supply localized strings, icons, root loading state, navigation, and
callbacks. Brand assets, secure session persistence, protocol clients, and
platform lifecycle remain outside this module.
