plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.stremio.mobile"
    compileSdk = 37
    ndkVersion = "29.0.13846066"

    defaultConfig {
        applicationId = "com.stremio.mobile"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters.addAll(setOf("arm64-v8a", "x86_64"))
        }
    }

    buildTypes {
        debug {
            matchingFallbacks.add("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }


}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0-rc01")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0-rc01")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")

    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.4.0")
    implementation("pro.streem.pbandk:pbandk-runtime:0.16.0")
    implementation("com.github.Stremio:stremio-core-kotlin:1.15.0")
}



tasks.register<Exec>("buildStreamServerArm64") {
    workingDir = file("../../stream-server/server")
    if (org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS)) {
        commandLine("cmd", "/c", "cargo ndk --target aarch64-linux-android --platform 24 build --release")
    } else {
        commandLine("cargo", "ndk", "--target", "aarch64-linux-android", "--platform", "24", "build", "--release")
    }
    environment("OPENSSL_DIR", "C:\\vcpkg\\installed\\arm64-android")
}

tasks.register<Exec>("buildStreamServerX86_64") {
    workingDir = file("../../stream-server/server")
    if (org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS)) {
        commandLine("cmd", "/c", "cargo ndk --target x86_64-linux-android --platform 24 build --release")
    } else {
        commandLine("cargo", "ndk", "--target", "x86_64-linux-android", "--platform", "24", "build", "--release")
    }
    environment("OPENSSL_DIR", "C:\\vcpkg\\installed\\x64-android")
}

tasks.register<Copy>("copyStreamServerJniLibs") {
    dependsOn("buildStreamServerArm64", "buildStreamServerX86_64")
    from("../../stream-server/target/aarch64-linux-android/release/libstream_server.so") {
        into("arm64-v8a")
    }
    from("../../stream-server/target/x86_64-linux-android/release/libstream_server.so") {
        into("x86_64")
    }
    into("src/main/jniLibs")
}

tasks.named("preBuild") {
    dependsOn("copyStreamServerJniLibs")
}

