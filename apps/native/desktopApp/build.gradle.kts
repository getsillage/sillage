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
    implementation(project(":shared-ui:application"))
    implementation(compose.desktop.currentOs)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "app.sillage.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "Sillage"
            // jpackage requires a positive major version even for preview artifacts.
            packageVersion = "1.0.0"
            description = "A private, device-local Sillage records workspace"
            copyright = "Copyright (c) Sillage contributors"
            vendor = "Sillage"

            macOS {
                bundleID = "app.sillage.desktop"
            }
            windows {
                menuGroup = "Sillage"
                shortcut = true
                upgradeUuid = "976ca25d-f1a3-4b9f-b20f-3ac8ee5a71b6"
            }
        }
    }
}
