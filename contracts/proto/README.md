# Protobuf contract

`api/v1/` contains the authored Protobuf sources for the current Connect API.
`gen/` contains committed Go, Connect, Gateway, and OpenAPI projections produced
by `buf generate` and must not be edited manually.

The generated OpenAPI document describes only RPC methods with HTTP annotations.
It is not yet the complete REST contract: multipart uploads, authenticated file
downloads, SSE events, bootstrap metadata, and handwritten REST extensions are
defined by the [REST API Guide](../../docs/development/api/README.md) and
`server/*_routes.go`.

Run the repository-level `make check-proto` target after changing sources or
generation configuration. Contract changes must update affected server
adapters, client mappings, tests, and compatibility documentation together.

## Compatibility checks

`make check-proto` runs Buf `FILE` breaking checks, lint, generation, and
generated-artifact drift checks. The current structured server contract remains
unchanged while the client and toolchain cleanup is completed. Legacy bulk
record-transfer operations and compatibility fields remain server-side until a
separate contract cleanup; the Web client does not use them.
