# Packages

`packages/` contains reusable modules that do not own an executable process or
application lifecycle. Dependencies flow from applications to packages, never
from packages back to applications.

Generated transport models are not domain models. Packages must map generated
API types into stable domain types before exposing them to feature code.
