plugins {
    id("sillage.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":kmp-core:application"))
            implementation(project(":kmp-features:ask"))
            implementation(project(":kmp-features:records"))
            implementation(project(":kmp-features:settings"))
        }
    }
}
