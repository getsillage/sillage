# Shared settings UI

Buildable Compose Multiplatform settings UI shared by Android, iOS, Windows,
and macOS hosts.

`SillageAIProfileSummaryCard` consumes `SettingsFeatureStateHolder` directly for
AI profile drafts and diagnostics feedback. It owns profile-name, provider,
model, API-key, default-profile, selection-color, and action-enabled presentation.

Hosts supply localized strings, selection state, and callbacks. Protocol clients,
encrypted key persistence, file pickers, native dialogs, navigation, and platform
lifecycle remain outside this module.
