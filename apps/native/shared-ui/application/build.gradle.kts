plugins {
    id("sillage.kmp-library")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-core:application"))
            api(project(":shared-ui:app-shell"))
            implementation(project(":kmp-features:records"))
            implementation(project(":shared-ui:design-system"))
            implementation(project(":shared-ui:records"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
