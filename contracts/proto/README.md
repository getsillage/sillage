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

`make check-proto` preserves Buf `FILE` breaking checks for maintained APIs.
The Web-only retirement has one bounded baseline normalization: the removed
bulk record-transfer service, bootstrap field 5, and record-create field 1 are
omitted from a temporary legacy comparison tree only when the authored current
schema reserves both removed field numbers. `scripts/prepare-proto-baseline.mjs`
defines this exception and requires all three historical locations to match
the reviewed original baseline SHA-256 fingerprints. Current field reservations
are verified from compiled Buf descriptors. Changed historical declarations or
future reuse of a path or field number are not exempt; other service removals,
field removals, and type changes still fail compatibility checks. Authored sources and historical Git revisions
are never rewritten by the checker.
