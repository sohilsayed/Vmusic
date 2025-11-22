// Project-level build.gradle.kts (root of your project)

// Top-level build file.
// The `plugins {}` block here can now correctly use aliases because settings.gradle.kts
// has already made Gradle aware of these plugin IDs and their versions.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinParcelize) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ksp) apply false
}

// No subprojects {} block for repositories is needed here.
// Repositories are centrally managed in settings.gradle.kts due to FAIL_ON_PROJECT_REPOS.