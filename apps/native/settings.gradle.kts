pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SillageNative"
include(":androidApp")
include(":kmp-core:application")
include(":kmp-core:domain")
include(":kmp-core:sync")
include(":kmp-features:ask")
include(":kmp-features:auth")
include(":kmp-features:records")
include(":kmp-features:settings")
include(":kmp-features:sync")
include(":shared-ui:app-shell")
include(":shared-ui:design-system")

project(":kmp-core:application").projectDir = file("../../packages/kmp-core/application")
project(":kmp-core:domain").projectDir = file("../../packages/kmp-core/domain")
project(":kmp-core:sync").projectDir = file("../../packages/kmp-core/sync")
project(":kmp-features:ask").projectDir = file("../../packages/kmp-features/ask")
project(":kmp-features:auth").projectDir = file("../../packages/kmp-features/auth")
project(":kmp-features:records").projectDir = file("../../packages/kmp-features/records")
project(":kmp-features:settings").projectDir = file("../../packages/kmp-features/settings")
project(":kmp-features:sync").projectDir = file("../../packages/kmp-features/sync")
project(":shared-ui:app-shell").projectDir = file("shared-ui/app-shell")
project(":shared-ui:design-system").projectDir = file("shared-ui/design-system")
