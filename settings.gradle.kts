pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "StremioMobile"
include(":app")
include(":mpv-android-lib")
project(":mpv-android-lib").projectDir = file("third_party/mpv-android-lib/app")
includeBuild("streamio-core-kotlin/stremio-core-kotlin")
