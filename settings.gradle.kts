// settings.gradle.kts

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        // FIXED: Use direct ID and version string from libs.versions.toml.
        // The libs accessor is NOT reliably available here for versions.
        id("com.android.application") version "8.10.1" // Must match androidGradlePlugin in TOML
        id("org.jetbrains.kotlin.android") version "2.0.21" // Must match kotlin in TOML
        id("org.jetbrains.kotlin.plugin.parcelize") version "2.0.21" // Must match kotlin in TOML
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" // Must match kotlin in TOML
        id("com.google.devtools.ksp") version "2.0.21-1.0.27" // Must match ksp in TOML
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false // Or your Kotlin version

    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // For NewPipeExtractor
    }
    // Gradle will automatically load gradle/libs.versions.toml if present.
    // No explicit `versionCatalogs { create("libs") ... }` block is needed here,
    // especially as it was causing "called 'from' more than once" error.
}

rootProject.name = "Holodex"
include(":app")