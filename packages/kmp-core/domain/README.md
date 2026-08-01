# Domain

Platform-independent entities, value objects, domain policies, and domain
errors live here. This module is part of the native Gradle workspace and
publishes Android, desktop JVM, and Apple artifacts from `commonMain`.

The module must not depend on transport, storage, Compose, or operating-system
APIs. Put common behavior tests in `src/commonTest`; platform-specific tests are
only for target adapter behavior.
