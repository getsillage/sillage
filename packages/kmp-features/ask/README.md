# Ask feature

Ask conversation, streaming, branching, regeneration, archive, and
answer-to-record feature-scoped state.

The first buildable slice is `AskConversationStateHolder`. It owns the
conversation collection, current conversation, selected branch head, and loaded
messages. Its transitions reject cross-conversation messages and stale snapshots.
Streaming and request lifecycle state move in later slices without moving
persistence or SSE transport into the feature module.
