import org.gradle.api.GradleException
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":kmp-core:local-data"))
    implementation(project(":kmp-core:network"))
    implementation(project(":shared-ui:application"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

val desktopPackageName = "Sillage"
val desktopPackageVersion = "1.0.0"

compose.desktop {
    application {
        mainClass = "app.sillage.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = desktopPackageName
            // jpackage requires a positive major version even for preview artifacts.
            packageVersion = desktopPackageVersion
            description = "A private, device-local Sillage records workspace"
            copyright = "Copyright (c) Sillage contributors"
            vendor = "Sillage"

            macOS {
                bundleID = "app.sillage.desktop"
                iconFile.set(project.file("src/main/resources/Sillage.icns"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/Sillage.ico"))
                menuGroup = "Sillage"
                shortcut = true
                upgradeUuid = "976ca25d-f1a3-4b9f-b20f-3ac8ee5a71b6"
            }
        }
    }
}

data class DesktopPackageSpec(
    val taskName: String,
    val relativePath: String,
)

val desktopPackageSpec = when {
    System.getProperty("os.name").contains("mac", ignoreCase = true) -> DesktopPackageSpec(
        taskName = "packageDmg",
        relativePath = "compose/binaries/main/dmg/$desktopPackageName-$desktopPackageVersion.dmg",
    )

    System.getProperty("os.name").contains("win", ignoreCase = true) -> DesktopPackageSpec(
        taskName = "packageMsi",
        relativePath = "compose/binaries/main/msi/$desktopPackageName-$desktopPackageVersion.msi",
    )

    else -> null
}

tasks.register("checkNativeDistribution") {
    group = "verification"
    description = "Builds and verifies the native desktop package for the current host OS."

    if (desktopPackageSpec == null) {
        doLast {
            throw GradleException("Native desktop packaging is supported only on macOS and Windows hosts.")
        }
    } else {
        dependsOn(desktopPackageSpec.taskName)
        val artifact = layout.buildDirectory.file(desktopPackageSpec.relativePath)
        inputs.file(artifact)

        doLast {
            val packageFile = artifact.get().asFile
            check(packageFile.isFile) {
                "Expected native desktop package was not created: ${packageFile.absolutePath}"
            }
            check(packageFile.length() > 1_000_000L) {
                "Native desktop package is unexpectedly small: ${packageFile.absolutePath}"
            }
        }
    }
}
