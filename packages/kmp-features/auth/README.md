# Authentication feature

This buildable Kotlin Multiplatform feature owns platform-neutral authentication
presentation state.

`AuthFeatureStateHolder` is the feature aggregate nested by Android root UI
state. `AuthenticationStateHolder` owns initialization and sign-in form drafts
plus the password-change lifecycle: validation, single-flight request identity,
client-context validation, and secret clearing after success.

The module does not own tokens, secure session persistence, HTTP mapping, or
platform navigation. Those remain behind `kmp-core:application` repository ports
and host adapters. Native hosts may expose transitional accessors while screens
move to the shared holder, but must route mutations through the holder contracts.
Android routes credential drafts and primary-credential clearing through root
`withAuth` thin wrappers while application-level loading remains outside the auth
aggregate.

The buildable `shared-ui:auth` module is a direct UI consumer of
`AuthFeatureStateHolder`; platform hosts provide localized resources, root
loading state, navigation, and protocol callbacks.
