plugins {
    id("sillage.kmp-library")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-core:application"))
            api(project(":kmp-core:sync"))
            api(project(":shared-ui:app-shell"))
            implementation(project(":kmp-features:auth"))
            implementation(project(":kmp-features:records"))
            implementation(project(":kmp-features:sync"))
            implementation(project(":shared-ui:auth"))
            implementation(project(":shared-ui:design-system"))
            implementation(project(":shared-ui:records"))
            implementation(project(":shared-ui:settings"))
            implementation(project(":shared-ui:sync"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
