import org.gradle.api.artifacts.ProjectDependency

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
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

subprojects {
    pluginManager.withPlugin("sillage.kmp-library") {
        checkShared.configure {
            dependsOn(tasks.named("desktopTest"))
        }
    }
}
