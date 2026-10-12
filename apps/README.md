# Applications

`apps/web/` contains the responsive React browser client, its unit and browser
journey tests, and build configuration. It is the only application client.

[Go server boundaries](server/README.md) documents the backend implemented by
`cmd/`, `internal/`, `server/`, and `store/` at the repository root.

The Web client calls the server API. Production builds embed the Web assets in
the Go binary and ship both in the same container image.
