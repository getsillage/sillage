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
include(":kmp-features:records")

project(":kmp-core:application").projectDir = file("../../packages/kmp-core/application")
project(":kmp-core:domain").projectDir = file("../../packages/kmp-core/domain")
project(":kmp-features:records").projectDir = file("../../packages/kmp-features/records")
