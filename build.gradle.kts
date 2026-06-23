import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

plugins {
    id("com.android.application") version "9.2.0" apply false
    id("com.android.library") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}

@DisableCachingByDefault(because = "Installs an APK on a connected Android device.")
abstract class InstallReleaseApk @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val androidSerial: Property<String>

    @get:Input
    @get:Optional
    abstract val androidSdkRoot: Property<String>

    @TaskAction
    fun install() {
        val apk = currentReleaseApk()
        if (apk.name.contains("unsigned", ignoreCase = true)) {
            throw GradleException(
                "Release APK is unsigned and cannot be installed. Configure release signing with " +
                    "ANDROID_KEYSTORE_FILE, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD, " +
                    "or run :app:installDebug for a debug install.",
            )
        }

        val adb = adbExecutable()
        logger.lifecycle("Installing ${apk.name} with $adb")
        execOperations.exec {
            executable = adb
            val serial = androidSerial.orNull
            if (!serial.isNullOrBlank()) {
                args("-s", serial)
            }
            args("install", "-r", apk.absolutePath)
        }
    }

    private fun currentReleaseApk(): File {
        val dir = apkDirectory.get().asFile
        val metadataFile = dir.resolve("output-metadata.json")
        if (metadataFile.isFile) {
            val outputFileName = Regex("\"outputFile\"\\s*:\\s*\"([^\"]+\\.apk)\"")
                .findAll(metadataFile.readText())
                .map { it.groupValues[1] }
                .sortedWith(compareBy<String> { !it.contains("universal", ignoreCase = true) }.thenBy { it })
                .firstOrNull()
            if (!outputFileName.isNullOrBlank()) {
                return dir.resolve(outputFileName).takeIf { it.isFile }
                    ?: throw GradleException("Release APK listed in ${metadataFile.absolutePath} was not found: $outputFileName")
            }
        }

        return dir.listFiles { file -> file.isFile && file.extension.equals("apk", ignoreCase = true) }
            ?.sortedWith(
                compareBy<File> { !it.name.contains("universal", ignoreCase = true) }
                    .thenBy { it.name.contains("unsigned", ignoreCase = true) }
                    .thenBy { it.name }
            )
            ?.firstOrNull()
            ?: throw GradleException("No release APK found in ${dir.absolutePath}.")
    }

    private fun adbExecutable(): String {
        val sdkRoot = androidSdkRoot.orNull
        if (!sdkRoot.isNullOrBlank()) {
            val adbName = if (System.getProperty("os.name").contains("windows", ignoreCase = true)) "adb.exe" else "adb"
            val sdkAdb = File(File(sdkRoot, "platform-tools"), adbName)
            if (sdkAdb.isFile) {
                return sdkAdb.absolutePath
            }
        }
        return "adb"
    }
}

tasks.register<Exec>("compileCoreJniLibs") {
    workingDir = file("streamio-core-kotlin/stremio-core-kotlin")
    if (org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS)) {
        commandLine("cmd", "/c", "..\\..\\gradlew.bat copyJniLibs")
    } else {
        commandLine("../../gradlew", "copyJniLibs")
    }
}

tasks.register("compileNativeLibs") {
    group = "build"
    description = "Compiles all native JNI libraries (stream-server and stremio-core)"
    dependsOn("compileCoreJniLibs", ":app:copyStreamServerJniLibs")
}

fun signingPropertyOrEnv(name: String): String? =
    providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

val hasReleaseSigning = listOf(
    signingPropertyOrEnv("ANDROID_KEYSTORE_FILE"),
    signingPropertyOrEnv("ANDROID_KEYSTORE_PASSWORD"),
    signingPropertyOrEnv("ANDROID_KEY_ALIAS"),
    signingPropertyOrEnv("ANDROID_KEY_PASSWORD"),
).all { it != null }

if (!hasReleaseSigning) {
    tasks.register<InstallReleaseApk>("installRelease") {
        group = "install"
        description = "Assembles and installs the signed release APK on a connected Android device."
        dependsOn(":app:assembleRelease")
        apkDirectory.set(layout.projectDirectory.dir("app/build/outputs/apk/release"))
        androidSerial.set(providers.gradleProperty("ANDROID_SERIAL").orElse(providers.environmentVariable("ANDROID_SERIAL")))
        androidSdkRoot.set(
            providers.gradleProperty("ANDROID_SDK_ROOT")
                .orElse(providers.environmentVariable("ANDROID_HOME"))
                .orElse(providers.environmentVariable("ANDROID_SDK_ROOT")),
        )
    }
}
