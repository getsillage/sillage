# Go server boundaries

The Go monolith is implemented at the repository root:

- `cmd/sillage/` owns process configuration and lifecycle;
- `internal/` owns runtime configuration, secrets, and shared infrastructure;
- `server/` owns HTTP adapters, authentication, records, attachments, and AI;
- `store/` owns SQLite persistence and compatible schema upgrades.

The server embeds the Web application built from `apps/web/`. See the
[Architecture Guide](../../docs/development/architecture.md) for module and data
boundaries and [Contributing](../../CONTRIBUTING.md) for development commands.
