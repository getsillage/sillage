# Contracts

`contracts/proto/` contains Sillage's public Protobuf API definitions and their
committed Go, Connect, Gateway, and OpenAPI projections.

Proto defines the structured Connect API. The complete REST contract also
includes multipart uploads, authenticated file downloads, bootstrap metadata,
and Ask SSE, documented in the [REST API Guide](../docs/development/api/README.md)
and implemented in `server/*_routes.go`.

Contract changes must update the Web client, server adapters, generated
artifacts, behavior tests, and documentation together.
