# iOS native UI adapters

Reserved for SwiftUI or UIKit views and controllers used when a platform-native
surface provides materially better system integration, accessibility,
performance, or interaction behavior than the shared Compose implementation.

Native views consume shared feature state and use cases; they must not duplicate
domain, persistence, synchronization, or protocol logic.
