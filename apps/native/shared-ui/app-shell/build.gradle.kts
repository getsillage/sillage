plugins {
    id("sillage.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":kmp-core:application"))
        }
    }
}
