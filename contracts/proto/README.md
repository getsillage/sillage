# Protobuf contract

`api/v1/` contains the authored Protobuf sources for the current Connect API.
`gen/` contains committed Go, Connect, Gateway, and OpenAPI projections produced
by `buf generate` and must not be edited manually.

The generated OpenAPI document describes only RPC methods with HTTP annotations.
It is not yet the complete REST contract: multipart uploads, authenticated file
downloads, SSE events, bootstrap metadata, and handwritten REST extensions are
tracked by the adjacent contract directories and the REST API guide.

Run the repository-level `make check-proto` target after changing sources or
generation configuration. Contract changes must update affected server
adapters, client mappings, tests, and compatibility documentation together.
