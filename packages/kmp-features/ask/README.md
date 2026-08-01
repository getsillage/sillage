# Ask feature

Ask conversation, streaming, branching, regeneration, archive, and
answer-to-record feature-scoped state.

The first buildable slice is `AskConversationStateHolder`. It owns the
conversation collection, current conversation, selected branch head, and loaded
messages. Its transitions reject cross-conversation messages and stale snapshots.
Streaming and request lifecycle state move in later slices without moving
persistence or SSE transport into the feature module.

`AskVariantStateHolder` owns the single-flight branch-selection request identity.
It captures screen session, conversation, source mode, and client generation so a
late response cannot cross a navigation or workspace boundary.

`AskMemoSaveStateHolder` owns the answer-to-record request identity and validates
the captured answer content, conversation, branch head, screen session, source
mode, and client generation. The actual record write remains behind the shared
records application use case.

`AskSourceNavigationStateHolder` owns source-record navigation requests without
depending on a platform navigation enum. It captures stable destination/history
keys plus conversation and client context, while each host maps those keys to its
navigation model.

`AskStreamStateHolder` owns answer-generation request identity, live user/answer
presentation, regeneration identity, and completion events. Android retains the
SSE client and device-local model execution; callbacks update shared state only
while the captured conversation and client context still match.

`AskLoadStateHolder` owns conversation/message loading and its durable retry
message, with explicit begin, complete, fail, and cancel transitions.

`AskComposerStateHolder` owns the draft question and retrieval scope/source
options. Request execution captures these values but does not own or mutate the
composer draft implicitly.

`AskSessionStateHolder` provides the monotonic screen generation used by all Ask
request holders to reject callbacks after navigation or client-context changes.
