plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(org.gradle.api.artifacts.dsl.LockMode.STRICT)
        ignoredDependencies.add("org.jetbrains.compose.desktop:desktop-jvm-linux-x64")
        ignoredDependencies.add("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64")
        ignoredDependencies.add("org.jetbrains.compose.desktop:desktop-jvm-windows-x64")
        ignoredDependencies.add("org.jetbrains.skiko:skiko-awt-runtime-linux-x64")
        ignoredDependencies.add("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64")
        ignoredDependencies.add("org.jetbrains.skiko:skiko-awt-runtime-windows-x64")
    }
}
