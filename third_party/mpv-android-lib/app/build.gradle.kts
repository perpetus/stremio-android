plugins {
    id("com.android.library")
}

android {
    namespace = "is.xyz.mpv"
    compileSdk = 37
    ndkVersion = "29.0.13846066"

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
