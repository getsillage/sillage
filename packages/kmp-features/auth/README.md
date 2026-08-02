# Authentication feature

This buildable Kotlin Multiplatform feature owns platform-neutral authentication
presentation state.

`AuthFeatureStateHolder` is the feature aggregate nested by Android root UI
state. `AuthenticationStateHolder` owns initialization and sign-in form drafts
plus the password-change lifecycle: validation, single-flight request identity,
client-context validation, and secret clearing after success.

The module does not own tokens, secure session persistence, HTTP mapping, or
platform navigation. Those remain behind `kmp-core:application` repository ports
and host adapters. Android state orchestration, screens, and tests consume the
nested auth holders directly without root authentication compatibility getters;
mutations continue to route through holder contracts.

The buildable `shared-ui:auth` module is a direct UI consumer of
`AuthFeatureStateHolder`; platform hosts provide localized resources, root
loading state, navigation, and protocol callbacks.
