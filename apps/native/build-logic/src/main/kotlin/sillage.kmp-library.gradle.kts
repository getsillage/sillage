import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

val namespaceSuffix = project.path
    .removePrefix(":")
    .split(":")
    .joinToString(".") { segment ->
        when (segment) {
            "kmp-core" -> "core"
            "kmp-features" -> "features"
            "shared-ui" -> "ui"
            else -> segment.replace("-", "")
        }
    }

require(namespaceSuffix.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*"))) {
    "Shared module path '${project.path}' cannot form a stable Android namespace"
}

kotlin {
    android {
        namespace = "app.sillage.$namespaceSuffix"
        compileSdk = 35
        minSdk = 26

        withHostTestBuilder {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
