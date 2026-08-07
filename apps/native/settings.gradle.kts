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
include(":desktopApp")
include(":iosApp")
include(":kmp-core:application")
include(":kmp-core:domain")
include(":kmp-core:local-data")
include(":kmp-core:network")
include(":kmp-core:sync")
include(":kmp-features:ask")
include(":kmp-features:auth")
include(":kmp-features:records")
include(":kmp-features:settings")
include(":kmp-features:sync")
include(":shared-ui:app-shell")
include(":shared-ui:application")
include(":shared-ui:ask")
include(":shared-ui:auth")
include(":shared-ui:design-system")
include(":shared-ui:records")
include(":shared-ui:settings")
include(":shared-ui:sync")

project(":kmp-core:application").projectDir = file("../../packages/kmp-core/application")
project(":kmp-core:domain").projectDir = file("../../packages/kmp-core/domain")
project(":kmp-core:local-data").projectDir = file("../../packages/kmp-core/local-data")
project(":kmp-core:network").projectDir = file("../../packages/kmp-core/network")
project(":kmp-core:sync").projectDir = file("../../packages/kmp-core/sync")
project(":kmp-features:ask").projectDir = file("../../packages/kmp-features/ask")
project(":kmp-features:auth").projectDir = file("../../packages/kmp-features/auth")
project(":kmp-features:records").projectDir = file("../../packages/kmp-features/records")
project(":kmp-features:settings").projectDir = file("../../packages/kmp-features/settings")
project(":kmp-features:sync").projectDir = file("../../packages/kmp-features/sync")
project(":shared-ui:app-shell").projectDir = file("shared-ui/app-shell")
project(":shared-ui:application").projectDir = file("shared-ui/application")
project(":shared-ui:ask").projectDir = file("shared-ui/ask")
project(":shared-ui:auth").projectDir = file("shared-ui/auth")
project(":shared-ui:design-system").projectDir = file("shared-ui/design-system")
project(":shared-ui:records").projectDir = file("shared-ui/records")
project(":shared-ui:settings").projectDir = file("shared-ui/settings")
project(":shared-ui:sync").projectDir = file("shared-ui/sync")
