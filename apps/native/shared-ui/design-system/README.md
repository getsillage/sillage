# Native design system

Buildable Compose Multiplatform theme and design primitives shared by Android,
iOS, Windows, and macOS hosts.

`SillageDesignTheme` owns semantic light/dark color schemes, typography, shapes,
and the common `MaterialTheme` boundary. Platform hosts wrap it only when system
capabilities require extra work, such as Android status/navigation bar icon
appearance. Platform resources, lifecycle, windows, and packaging remain in the
host applications.

`SillageNavigationBar` owns the primary-navigation container, divider, system
insets, content height, and selectable-group semantics. `SillageNavigationItem`
owns each item's layout, selected/disabled semantic colors, indicator animation,
and Tab accessibility role. Hosts supply localized labels, icons, destination
selection, and callbacks.

`SillageSettingsSectionCard` owns settings section title hierarchy, spacing,
shape, semantic colors, and border treatment. Hosts supply localized titles and
section content.

`SillageSettingsInfoRow`, `SillageSettingsActionRow`,
`SillageSettingsSwitchRow`, and `SillageSettingsEmptyCard` own common settings
layout, selection and disabled colors, divider treatment, value selection, and
Switch semantics. Hosts supply localized text, icons, values, state, and
callbacks.

`SillageErrorCard` owns reusable error-container layout and semantic error
colors. Hosts supply localized messages, action labels, icons, and callbacks.

`applySillageHeadingSemantics` and `applySillageStatusSemantics` centralize
cross-platform heading and concise status-announcement semantics. Hosts supply
localized status descriptions.
