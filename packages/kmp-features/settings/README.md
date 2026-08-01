# Settings feature

Settings profile editing, persistence request state, validation feedback, and
provider capability presentation.

`AIProfileDraft` owns cross-platform editor values, transient raw numeric input,
and unsaved presentation identity. Secret input is feature state only: platform
adapters explicitly map it to encrypted storage or transport commands, and
neither `draftKey` nor API-key material enters shared domain metadata.
`AIProfilesMutationStateHolder` composes those drafts and owns optimistic save,
rollback, single-flight request identity, and client-context validation. Hosts
may keep editing presentation and load state outside this holder, but profile
save callbacks cannot cross workspace or mode changes.

`AIAutoSummaryStateHolder` owns the independently saved automatic-summary
preference, optimistic mutation, rollback, request identity, and client-context
validation. Storage and REST remain platform adapters behind
`kmp-core:application`.
