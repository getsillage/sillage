import org.gradle.api.artifacts.ProjectDependency

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(org.gradle.api.artifacts.dsl.LockMode.STRICT)
    }
}

val checkNativeArchitecture = tasks.register("checkNativeArchitecture") {
    group = "verification"
    description = "Checks dependency direction between native application and shared modules."

    doLast {
        subprojects.forEach { sourceProject ->
            sourceProject.configurations.forEach { configuration ->
                configuration.dependencies
                    .withType(ProjectDependency::class.java)
                    .forEach { dependency ->
                        val source = sourceProject.path
                        val target = dependency.path
                        val allowed = when {
                            source == target -> true
                            source.startsWith(":kmp-core:") -> target.startsWith(":kmp-core:")
                            source.startsWith(":kmp-features:") -> target.startsWith(":kmp-core:")
                            source.startsWith(":shared-ui:") ->
                                target.startsWith(":kmp-core:") ||
                                    target.startsWith(":kmp-features:") ||
                                    target.startsWith(":shared-ui:")
                            else -> true
                        }
                        check(allowed) {
                            "Native dependency boundary violation: $source -> $target " +
                                "(${configuration.name})"
                        }
                    }
            }
        }
    }
}

val checkShared = tasks.register("checkShared") {
    group = "verification"
    description = "Runs shared native architecture checks and host tests."
    dependsOn(checkNativeArchitecture)
}

tasks.register("checkDesktop") {
    group = "verification"
    description = "Runs the desktop host tests and production compilation."
    dependsOn(checkShared)
    dependsOn(":desktopApp:check")
    dependsOn(":desktopApp:jar")
}

tasks.register("checkDesktopPackage") {
    group = "verification"
    description = "Builds and verifies the host-native DMG or MSI package."
    dependsOn(":desktopApp:checkNativeDistribution")
}

tasks.register("checkIos") {
    group = "verification"
    description = "Links the shared iOS frameworks for device and simulator targets."
    dependsOn(checkShared)
    dependsOn(":iosApp:linkDebugFrameworkIosArm64")
    dependsOn(":iosApp:linkDebugFrameworkIosSimulatorArm64")
    dependsOn(":iosApp:linkDebugFrameworkIosX64")
    dependsOn(":iosApp:linkReleaseFrameworkIosArm64")
}

subprojects {
    pluginManager.withPlugin("sillage.kmp-library") {
        checkShared.configure {
            dependsOn(tasks.named("desktopTest"))
        }
    }
}
