# Applications

`apps/` contains independently buildable and releasable Sillage applications.

- `server/` is the target location for the Go modular monolith. The current Go
  packages remain at the repository root until the backend module migration is
  performed as a dedicated, behavior-preserving phase.
- `web/` contains the browser client.
- `native/` is the Kotlin Multiplatform application workspace for Android,
  iOS, Windows, and macOS.

Applications may depend on public modules under `packages/` and generated
clients under `contracts/`, but applications must not import implementation
details from one another.
