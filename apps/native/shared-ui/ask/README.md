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

`SillageAskComposer` owns context-label selection, trimmed character count,
IME Send gating, question field layout, and send/stop action switching.
`SillageAskContextLabel` exposes the same context selection to host top bars.
Hosts provide localized formatting, icons, and feature callbacks.

`SillageAskEmptyPrompt`, `SillageAskLiveUserCard`, and
`SillageAskLiveAnswerCard` own empty-conversation guidance plus live user/answer
card layout, waiting fallback, colors, and message accessibility descriptions.
Hosts provide localized copy, icons, and speaker-description formatting.

`SillageAskMessageActions` owns branch-neighbor selection, variant position
semantics, regenerate/save visibility and request gating, plus saving progress
presentation. Hosts provide localized strings, Material icons, and feature
callbacks.

Streaming transports, Markdown rendering, navigation, persistence, and native
system integration remain platform-adapter responsibilities.
