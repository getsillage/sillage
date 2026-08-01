# Domain

Platform-independent entities, value objects, domain policies, and domain
errors live here. This module is part of the native Gradle workspace and
publishes Android, desktop JVM, and Apple artifacts from `commonMain`.

The module must not depend on transport, storage, Compose, or operating-system
APIs. Put common behavior tests in `src/commonTest`; platform-specific tests are
only for target adapter behavior.

The first extracted aggregate is `records.Memo`. Its lifecycle policy excludes
archived, recoverably deleted, and purged records from active-domain behavior.
`records.MemoAI` carries platform-neutral AI-derived record metadata. Android
transport and persistence mappings construct these shared types rather than
maintaining application-local copies.

`ask` owns conversation, message, and source-reference values. `settings` owns
secret-free AI profile and settings metadata. UI drafts, API inputs, codecs, and
secret persistence remain outside the domain module.
`auth.Account` owns secret-free account identity. Access tokens, expiry, instance
capabilities, session persistence, and authentication transport remain outside
the domain module.
