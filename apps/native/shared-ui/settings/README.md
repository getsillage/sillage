# Shared settings UI

Buildable Compose Multiplatform settings UI shared by Android, iOS, Windows,
and macOS hosts.

`SillageAIProfileSummaryCard` consumes `SettingsFeatureStateHolder` directly for
AI profile drafts and diagnostics feedback. It owns profile-name, provider,
model, API-key, default-profile, selection-color, and action-enabled presentation.
`SillageAIProfileDetailCard` consumes the same aggregate for editable fields,
model discovery, connection-test feedback, provider selection, and deletion
confirmation while exposing host callbacks for mutations and operations.
`SillageAIProfilesHeaderCard` reads the aggregate save lifecycle and owns the
new/save action layout, progress feedback, and operation-gate presentation.
`SillageSettingsLanguageRow` owns settings language-selection layout and option
presentation while hosts supply supported language identifiers and persistence.
`SillageSettingsAppearanceSection` composes that row with theme selection and
shared enable-state presentation.
`SillageSettingsServiceSyncSection` owns online/offline selection and conditional
server/sync action composition while hosts execute client-context operations.
`SillageSettingsDataSection` owns import/export action layout and shared blocking;
hosts retain native file pickers and serialization callbacks.
`SillageSettingsOverviewCard` owns the status-card layout while hosts map
cross-feature state into localized mode, theme, record-count, and AI values.

`SillageAIProfilesEditor` emits the lazy-list header, empty state, profile cards,
and detail editors while its remembered state owns expanded-profile selection.
`SillageAIAutoSummarySection` consumes the settings aggregate preference and save
lifecycle directly, combining them with a host-wide operation gate.

Hosts supply localized strings, icons, and callbacks. Protocol clients,
encrypted key persistence, file pickers, native dialogs, navigation, and platform
lifecycle remain outside this module.
