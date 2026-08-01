# Settings feature

Settings profile editing, persistence request state, validation feedback, and
provider capability presentation.

`SettingsFeatureStateHolder` composes the holders below and owns coordinated
workspace teardown plus loaded/imported editable-settings snapshot application.
Individual holders remain the unit of request identity. Android stores one
`settings` aggregate on root UI state and keeps transitional slice getters while
remaining single-holder call sites finish moving onto `withSettings` / aggregate
transitions.

`AIProfileDraft` owns cross-platform editor values, transient raw numeric input,
and unsaved presentation identity. Secret input is feature state only: platform
adapters explicitly map it to encrypted storage or transport commands, and
neither `draftKey` nor API-key material enters shared domain metadata.
`AIProfilesMutationStateHolder` composes those drafts and owns optimistic save,
rollback, single-flight request identity, and client-context validation. Hosts
may keep editing presentation and load state outside this holder, but profile
save callbacks cannot cross workspace or mode changes.
Profile-name validation and active-profile normalization are shared save policy,
so every native host submits the same enabled/default configuration.
`AISettingsLoadStateHolder` owns load and retry presentation, request identity,
and mode/client-context validation. Starting a load or profile mutation
explicitly invalidates the other lifecycle so late responses cannot replace a
newer editor snapshot.
`AIProfileDiagnosticsStateHolder` owns connection-test and model-list progress,
per-profile results, and independent request identities. Requests capture the
full draft plus its stable editor key and reject completion after edits,
removal, mode changes, or client-context replacement.

`AIAutoSummaryStateHolder` owns the independently saved automatic-summary
preference, optimistic mutation, rollback, request identity, and client-context
validation. Storage and REST remain platform adapters behind
`kmp-core:application`.
