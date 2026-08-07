import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Exec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.math.roundToInt

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
        ignoredDependencies.add("org.jetbrains.compose.desktop:desktop-jvm-linux-x64")
        ignoredDependencies.add("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64")
        ignoredDependencies.add("org.jetbrains.compose.desktop:desktop-jvm-windows-x64")
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

val checkNativeIdentity = tasks.register("checkNativeIdentity") {
    group = "verification"
    description = "Checks native application identity resources and visible product versions."

    val iosIconDirectory = file("iosApp/Sillage/Assets.xcassets/AppIcon.appiconset")
    val iosContents = iosIconDirectory.resolve("Contents.json")
    val iosProject = file("iosApp/Sillage.xcodeproj/project.pbxproj")
    val iosThirdPartyNotices = file("iosApp/Sillage/ThirdPartyNotices.txt")
    val desktopIcns = file("desktopApp/src/main/resources/Sillage.icns")
    val desktopIco = file("desktopApp/src/main/resources/Sillage.ico")
    val desktopBuild = file("desktopApp/build.gradle.kts")
    val desktopMain = file("desktopApp/src/main/kotlin/app/sillage/desktop/Main.kt")
    val androidBuild = file("androidApp/build.gradle.kts")

    inputs.files(
        file("branding/sillage-app-icon-ios.svg"),
        file("branding/sillage-app-icon-desktop.svg"),
        file("branding/generate-icons.sh"),
        file("branding/write-ico.swift"),
        iosContents,
        fileTree(iosIconDirectory) { include("*.png") },
        iosProject,
        iosThirdPartyNotices,
        desktopIcns,
        desktopIco,
        desktopBuild,
        desktopMain,
        androidBuild,
    )

    doLast {
        val contents = iosContents.readText()
        val entryPattern = Regex(
            """(?s)\{[^{}]*"filename"\s*:\s*"([^"]+)"[^{}]*"scale"\s*:\s*"([123])x"[^{}]*"size"\s*:\s*"([0-9.]+)x[0-9.]+"[^{}]*\}""",
        )
        val entries = entryPattern.findAll(contents).map { match ->
            Triple(
                match.groupValues[1],
                match.groupValues[2].toInt(),
                match.groupValues[3].toDouble(),
            )
        }.toList()
        check(entries.isNotEmpty()) { "The iOS AppIcon catalog has no image entries." }

        val referencedFiles = entries.map { it.first }.toSet()
        val actualFiles = iosIconDirectory.listFiles()
            .orEmpty()
            .filter { it.extension.equals("png", ignoreCase = true) }
            .map { it.name }
            .toSet()
        check(actualFiles == referencedFiles) {
            "The iOS AppIcon PNG set does not match Contents.json. " +
                "Missing=${referencedFiles - actualFiles}, extra=${actualFiles - referencedFiles}"
        }

        entries.forEach { (filename, scale, pointSize) ->
            val image = checkNotNull(ImageIO.read(iosIconDirectory.resolve(filename))) {
                "The iOS AppIcon image $filename could not be decoded."
            }
            val expectedPixels = (pointSize * scale).roundToInt()
            check(image.width == expectedPixels && image.height == expectedPixels) {
                "$filename must be ${expectedPixels}x$expectedPixels, " +
                    "was ${image.width}x${image.height}."
            }
            check(!image.colorModel.hasAlpha()) {
                "$filename must not contain an alpha channel."
            }
        }

        val icnsBytes = desktopIcns.readBytes()
        check(icnsBytes.size > 8 && String(icnsBytes, 0, 4, Charsets.US_ASCII) == "icns") {
            "The macOS application icon is not a valid ICNS container."
        }
        val declaredIcnsSize = ByteBuffer.wrap(icnsBytes, 4, 4)
            .order(ByteOrder.BIG_ENDIAN)
            .int
        check(declaredIcnsSize == icnsBytes.size) {
            "The macOS ICNS length header does not match the file size."
        }

        val icoBytes = desktopIco.readBytes()
        check(icoBytes.size >= 22) { "The Windows application icon is too small." }
        val icoHeader = ByteBuffer.wrap(icoBytes).order(ByteOrder.LITTLE_ENDIAN)
        val reserved = icoHeader.short.toInt() and 0xffff
        val type = icoHeader.short.toInt() and 0xffff
        val count = icoHeader.short.toInt() and 0xffff
        val sizes = buildSet {
            repeat(count) { index ->
                val offset = 6 + index * 16
                check(offset + 16 <= icoBytes.size) { "The Windows ICO directory is truncated." }
                val width = (icoBytes[offset].toInt() and 0xff).takeIf { it != 0 } ?: 256
                val height = (icoBytes[offset + 1].toInt() and 0xff).takeIf { it != 0 } ?: 256
                check(width == height) { "The Windows ICO contains a non-square image." }
                add(width)
            }
        }
        val requiredWindowsSizes = setOf(16, 24, 32, 48, 64, 128, 256)
        check(reserved == 0 && type == 1 && sizes.containsAll(requiredWindowsSizes)) {
            "The Windows application icon must contain $requiredWindowsSizes; found $sizes."
        }

        val iosProjectText = iosProject.readText()
        check(iosProjectText.split("ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;").size - 1 == 2) {
            "Both iOS target configurations must select the AppIcon catalog."
        }
        check("Assets.xcassets in Resources" in iosProjectText) {
            "The iOS asset catalog must be included in the Resources build phase."
        }
        check("ThirdPartyNotices.txt in Resources" in iosProjectText) {
            "The iOS third-party notices must be included in the Resources build phase."
        }
        check(
            iosThirdPartyNotices.readText()
                .startsWith("Sillage iOS - Open-source software notices"),
        ) {
            "The iOS third-party notices resource is missing or invalid."
        }

        val desktopBuildText = desktopBuild.readText()
        check("Sillage.icns" in desktopBuildText && "Sillage.ico" in desktopBuildText) {
            "Desktop native distributions must reference both branded icons."
        }

        fun requiredMatch(fileText: String, pattern: Regex, label: String): String {
            return checkNotNull(pattern.find(fileText)?.groupValues?.get(1)) {
                "Could not read $label product version."
            }
        }

        val androidVersion = requiredMatch(
            androidBuild.readText(),
            Regex("""versionName\s*=\s*"([^"]+)"""),
            "Android",
        )
        val desktopVersion = requiredMatch(
            desktopMain.readText(),
            Regex("""DesktopVersion\s*=\s*"([^"]+)"""),
            "desktop",
        )
        val iosVersions = Regex("""MARKETING_VERSION\s*=\s*([^;]+);""")
            .findAll(iosProjectText)
            .map { it.groupValues[1].trim() }
            .toSet()
        check(iosVersions.size == 1) { "iOS target configurations must use one product version." }
        val iosVersion = iosVersions.single()
        check(setOf(androidVersion, desktopVersion, iosVersion).size == 1) {
            "Visible native product versions differ: Android=$androidVersion, " +
                "desktop=$desktopVersion, iOS=$iosVersion."
        }
    }
}

val repositoryRoot = rootDir.parentFile.parentFile
val checkNativeThirdPartyNoticesGenerator =
    tasks.register<Exec>("checkNativeThirdPartyNoticesGenerator") {
        group = "verification"
        description = "Tests native third-party notice generation policy."
        workingDir(repositoryRoot)
        commandLine("node", "--test", "scripts/generate-native-third-party-notices.test.mjs")
    }

val checkNativeThirdPartyNotices = tasks.register<Exec>("checkNativeThirdPartyNotices") {
    group = "verification"
    description = "Checks desktop and iOS packaged notices against host lockfiles."
    dependsOn(checkNativeThirdPartyNoticesGenerator)
    workingDir(repositoryRoot)
    commandLine("node", "scripts/generate-native-third-party-notices.mjs")
}

tasks.register<Exec>("generateNativeThirdPartyNotices") {
    group = "build setup"
    description = "Regenerates desktop and iOS packaged third-party notices."
    workingDir(repositoryRoot)
    commandLine("node", "scripts/generate-native-third-party-notices.mjs", "--write")
}

val checkShared = tasks.register("checkShared") {
    group = "verification"
    description = "Runs shared native architecture checks and host tests."
    dependsOn(checkNativeArchitecture)
    dependsOn(checkNativeIdentity)
}

tasks.register("checkDesktop") {
    group = "verification"
    description = "Runs the desktop host tests and production compilation."
    dependsOn(checkShared)
    dependsOn(checkNativeThirdPartyNotices)
    dependsOn(":desktopApp:check")
    dependsOn(":desktopApp:jar")
}

tasks.register("checkDesktopPackage") {
    group = "verification"
    description = "Builds and verifies the host-native DMG or MSI package."
    dependsOn(checkNativeIdentity)
    dependsOn(checkNativeThirdPartyNotices)
    dependsOn(":desktopApp:checkNativeDistribution")
}

tasks.register("checkIos") {
    group = "verification"
    description = "Links the shared iOS frameworks for device and simulator targets."
    dependsOn(checkShared)
    dependsOn(checkNativeThirdPartyNotices)
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
