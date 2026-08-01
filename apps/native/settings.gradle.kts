pluginManagement {
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
include(":kmp-core:domain")

project(":kmp-core:domain").projectDir = file("../../packages/kmp-core/domain")
