# Authentication feature

This buildable Kotlin Multiplatform feature owns platform-neutral authentication
presentation state.

`AuthFeatureStateHolder` is the feature aggregate nested by Android root UI
state. `AuthenticationStateHolder` owns initialization and sign-in form drafts
plus the password-change lifecycle: validation, single-flight request identity,
client-context validation, and secret clearing after success.

`InstanceBootstrapStateHolder` owns the server-address draft, normalized public
bootstrap request identity, late-result rejection, and success/failure state.
Desktop and iOS consume it for connection diagnostics; Android retains its
existing host orchestration until that online path is migrated.

`InstanceAuthenticationStateHolder` owns desktop/iOS restoration,
initialization, sign-in, and sign-out request identity, form retention on
failure, password clearing on success, late-result rejection, and the
secret-free authenticated account shown by Settings. It also forwards the
shared password-change validation and context-bound request lifecycle, keeps
ordinary failure drafts retryable, prevents sign-out during rotation, and
updates the secret-free account only for the owned response. Missing stored credentials
return to the sign-in form without an error, while secure-vault failures remain
stable presentation state. It never owns access tokens or refresh cookies.

The module does not own tokens, secure session persistence, HTTP mapping, or
platform navigation. Those remain behind `kmp-core:application` repository ports
and host adapters. Android state orchestration, screens, and tests consume the
nested auth holders directly without root authentication compatibility getters;
mutations continue to route through holder contracts.

The buildable `shared-ui:auth` module is a direct UI consumer of
`AuthFeatureStateHolder`; platform hosts provide localized resources, root
loading state, navigation, and protocol callbacks.
