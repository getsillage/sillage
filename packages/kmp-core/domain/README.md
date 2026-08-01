# Domain

Platform-independent entities, value objects, domain policies, and domain
errors live here. This module is part of the native Gradle workspace and
publishes Android, desktop JVM, and Apple artifacts from `commonMain`.

The module must not depend on transport, storage, Compose, or operating-system
APIs. Put common behavior tests in `src/commonTest`; platform-specific tests are
only for target adapter behavior.

The first extracted aggregate is `records.Memo`. Its lifecycle policy excludes
archived, recoverably deleted, and purged records from active-domain behavior.
Android transport and persistence mappings construct this shared type rather
than maintaining an application-local copy.
