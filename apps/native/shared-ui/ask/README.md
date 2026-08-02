# Shared Ask UI

Buildable Compose Multiplatform presentation for the Ask feature.

`SillageAskOptionsSheet` and `SillageAskOptions` consume
`AskFeatureStateHolder` directly and own retrieval-range/source selection,
selected chip presentation, sheet layout, and heading semantics. Platform hosts
provide localized strings and route option changes through feature callbacks.

`SillageAskConversationSheet` and `SillageAskConversationList` consume the same
aggregate and own refresh/selection enablement, empty and selected-row
presentation, title fallback, and select-then-dismiss flow. Hosts provide
localized strings and route refresh/selection callbacks.

Streaming transports, Markdown rendering, navigation, persistence, and native
system integration remain platform-adapter responsibilities.
