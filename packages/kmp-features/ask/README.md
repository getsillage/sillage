# Ask feature

Ask conversation, streaming, branching, regeneration, archive, and
answer-to-record feature-scoped state.

`AskFeatureStateHolder` composes the holders below and owns coordinated
multi-holder transitions for workspace teardown, Ask-screen entry, blank
composition starts, conversation selection/load completion, variant head
application, stream begin/delta/finish, composer updates,
memo-save/source-navigation ownership, active snapshot replacement, and catalog
clearing. Individual holders remain the unit of request identity. Android stores
one aggregate on root UI state, keeps transitional slice getters only for
remaining conversation call sites, and routes single-holder
writes through `withAsk` / aggregate transitions. Android
provides thin composer and source-navigation wrappers so platform callbacks do
not replace nested Ask slices directly.

`AskConversationStateHolder` owns the
conversation collection, current conversation, selected branch head, and loaded
messages. Its transitions reject cross-conversation messages and stale snapshots.

`AskPathEntry`, active-path derivation, branch-leaf selection, and latest
assistant lookup also live in this module so hosts do not rebuild message-tree
policy in platform data layers.

`AskVariantStateHolder` owns single-flight branch-selection request identity.
It captures screen session, conversation, source mode, and client generation so
a late response cannot cross navigation or workspace boundaries. Android
navigation gates, request orchestration, and tests consume the holder through the
Ask aggregate directly rather than root variant compatibility getters.

`AskMemoSaveStateHolder` owns the answer-to-record request identity and validates
the captured answer content, conversation, branch head, screen session, source
mode, and client generation. Android request gates, orchestration, and tests
consume the holder through the Ask aggregate directly, without root memo-save
compatibility getters. The actual record write remains behind the shared records
application use case.

`AskSourceNavigationStateHolder` owns source-record navigation requests without
depending on a platform navigation enum. It captures stable destination/history
keys plus conversation and client context. Android request orchestration and tests
consume the holder through the Ask aggregate directly, while each host maps the
stable keys to its navigation model.

`AskStreamStateHolder` owns answer-generation request identity, live user/answer
presentation, regeneration identity, and completion events. Android retains the
SSE client and device-local model execution; callbacks update shared state only
while the captured conversation and client context still match. Stream-job
cleanup, Compose completion observation, and Android tests consume this state
through the aggregate directly rather than host-root compatibility getters.
Remote streaming crosses the application `AskAnswerStreamer` contract as ordered
start, delta, and failure events; SSE parsing and HTTP stay in the platform
adapter.
Offline generation and turn persistence cross the independent application
`AskAnswerGenerator` and `AskTurnStore` capabilities. The Android adapters own
the local model client and storage transaction.

`AskLoadStateHolder` owns conversation/message loading and its durable retry
message, with explicit begin, complete, fail, and cancel transitions. Android
request gates and tests consume this state through the Ask aggregate directly;
the host root no longer exposes load compatibility getters.

`AskComposerStateHolder` owns the draft question and retrieval scope/source
options. Request execution captures these values but does not own or mutate the
composer draft implicitly.

`AskSessionStateHolder` provides the monotonic screen generation used by all Ask
request holders to reject callbacks after navigation or client-context changes.
Android request orchestration and tests consume its generation through the Ask
aggregate directly, without root session compatibility getters.

The buildable `shared-ui:ask` module consumes `AskFeatureStateHolder` directly.
Its first slice owns retrieval-range/source option selection and bottom-sheet
presentation while hosts provide localized strings and route aggregate updates.
The shared conversation sheet derives refresh/selection gates from the same
aggregate and owns empty/current row presentation plus select-then-dismiss flow.
The shared composer consumes composer/load/stream/variant/source-navigation
state directly to own context labels, character count, IME send gating, and
send/stop presentation while hosts route aggregate updates and execution.
Shared empty/live cards consume domain messages and stream text to own prompt,
waiting fallback, card presentation, and message accessibility semantics while
hosts provide localized copy and speaker formatting.
Shared message actions consume domain messages and branch variants, then own
neighbor selection, regenerate/save gates, saving progress, and variant-position
semantics while hosts provide localized resources, icons, and callbacks.
Shared source references consume domain source values and own expansion, the
five-row display limit, row layout, and action gates while hosts provide
localized count/date formatting, icons, and navigation callbacks.
The shared message card consumes domain messages and stream presentation, then
owns displayed-content selection, bubble layout, semantics, and source/action
slots while hosts retain final Markdown and protected-attachment rendering.
The shared message list combines this aggregate with `AskPathEntry` projections
to own initial loading, error/empty/message/live ordering, lazy layout, and
per-message action gates while hosts fill localized content slots.
Shared Ask auto-follow consumes the same aggregate and path projections to own
drag suspension, near-bottom reactivation, new-turn/stream growth scrolling, and
rendered-item counts without moving platform announcement APIs into the feature.
Shared Ask top-bar title/actions consume the aggregate to own context
presentation, saving progress semantics, button layout, and request gates while
hosts retain the app-bar shell and map localized resources/icons.
