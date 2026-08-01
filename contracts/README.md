# Contracts

`contracts/` is the repository boundary for public protocol definitions,
generated projections, compatibility policy, and language-neutral conformance
fixtures.

The existing Protobuf contract will move under this directory during the
contract migration phase. Until an ADR explicitly replaces it, Protobuf remains
the structured API source of truth. HTTP-only surfaces such as uploads,
authenticated files, and SSE are documented under `http/` and `events/` so they
can be consolidated into a complete contract without inventing missing APIs.
