# Desktop application

Reserved for the Compose Desktop application targeting Windows and macOS.
Platform-specific packaging, signing, window integration, secure credential
storage, menus, and update behavior belong here.

Compose Multiplatform is the default UI. WinUI, SwiftUI, or AppKit interop is
allowed for platform-specific system surfaces when it does not fork shared
business or synchronization behavior.

No desktop product code has been implemented yet. Both desktop targets must use
the shared Kotlin Multiplatform domain and synchronization modules.
