# iOS native UI adapters

The implemented SwiftUI lifecycle wrapper lives under `../Sillage/` and hosts
the shared Compose `UIViewController`. This directory remains the boundary for
future feature-specific SwiftUI or UIKit surfaces where native controls provide
materially better system integration, accessibility, performance, or
platform-standard interaction.

Native views must consume shared feature state and use cases. They must not
duplicate domain, persistence, synchronization, or protocol logic.
