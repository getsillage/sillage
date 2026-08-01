# Native design system

Buildable Compose Multiplatform theme and design primitives shared by Android,
iOS, Windows, and macOS hosts.

`SillageDesignTheme` owns semantic light/dark color schemes, typography, shapes,
and the common `MaterialTheme` boundary. Platform hosts wrap it only when system
capabilities require extra work, such as Android status/navigation bar icon
appearance. Platform resources, lifecycle, windows, and packaging remain in the
host applications.
