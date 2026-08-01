# Cross-application tests

This directory contains tests whose scope crosses one application or language
boundary:

- `contract/` verifies generated clients against the server contract;
- `conformance/` runs language-neutral synchronization and conflict fixtures;
- `integration/` verifies application-to-application behavior;
- `e2e/` contains release-shaped user journeys.

Package-local unit tests remain beside their source code.
