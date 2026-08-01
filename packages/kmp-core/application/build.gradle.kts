plugins {
    id("sillage.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmp-core:domain"))
        }
    }
}
