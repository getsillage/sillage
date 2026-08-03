import org.gradle.api.GradleException
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "SillageShared"
            isStatic = true
            binaryOption("bundleId", "app.sillage.ios.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kmp-core:local-data"))
            implementation(project(":kmp-core:network"))
            implementation(project(":shared-ui:application"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

val xcodeConfiguration = providers.environmentVariable("KOTLIN_FRAMEWORK_BUILD_TYPE")
    .orElse(providers.environmentVariable("CONFIGURATION"))
    .orElse("debug")
    .get()
val xcodeBuildType = if (xcodeConfiguration.contains("release", ignoreCase = true)) {
    "Release"
} else {
    "Debug"
}
val xcodeSdkName = providers.environmentVariable("SDK_NAME")
    .orElse("iphonesimulator")
    .get()
val xcodeArchitectures = providers.environmentVariable("ARCHS")
    .orElse("arm64")
    .get()
    .split(' ')
val xcodeTarget = when {
    xcodeSdkName.startsWith("iphoneos") -> "iosArm64"
    "arm64" in xcodeArchitectures -> "iosSimulatorArm64"
    else -> "iosX64"
}
val xcodeTargetTaskSuffix = xcodeTarget.replaceFirstChar(Char::uppercaseChar)
val xcodeFramework = layout.buildDirectory.dir(
    "bin/$xcodeTarget/${xcodeBuildType.lowercase()}Framework/SillageShared.framework",
)

tasks.register<Sync>("prepareAppleFrameworkForXcode") {
    group = "build"
    description = "Links and copies the selected static framework into Xcode build products."
    dependsOn("link${xcodeBuildType}Framework$xcodeTargetTaskSuffix")
    from(xcodeFramework)
    into(providers.provider {
        val targetBuildDirectory = System.getenv("TARGET_BUILD_DIR")
            ?: throw GradleException("TARGET_BUILD_DIR must be supplied by Xcode.")
        val frameworksFolder = System.getenv("FRAMEWORKS_FOLDER_PATH") ?: "Frameworks"
        file("$targetBuildDirectory/$frameworksFolder/SillageShared.framework")
    })
}
