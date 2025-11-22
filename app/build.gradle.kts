// app/build.gradle.kts (Module-level build file)
import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use {
        localProperties.load(it)
    }
}


plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinParcelize) // Using alias now
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    kotlin("kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("androidx.room") version "2.7.2"
}

room {
    schemaDirectory("$projectDir/schemas")
}
android {
    namespace = "com.example.holodex"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.holodex"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val discordClientId = localProperties.getProperty("DISCORD_CLIENT_ID") ?: ""
        val discordRedirectUri = localProperties.getProperty("DISCORD_REDIRECT_URI") ?: ""

        buildConfigField("String", "DISCORD_CLIENT_ID", "$discordClientId")
        buildConfigField("String", "DISCORD_REDIRECT_URI", "$discordRedirectUri")
        manifestPlaceholders += mapOf(
            "appAuthRedirectScheme" to "holodexmusic"
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "DEBUG", "false")
        }
        debug {
            buildConfigField("boolean", "DEBUG", "true")
        }
    }

    compileOptions {
        // Enable Java 17 language features
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = false // Keep if used, though Compose apps often don't.
        compose = true
        buildConfig = true
    }

    buildToolsVersion = "35.0.0"
}
hilt {
    enableAggregatingTask = false
}
dependencies {
    // Android Core & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.palette.ktx)
    // Jetpack Compose (using BOM)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.media3.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.collections.immutable)

    // Lifecycle KTX (using bundle)
    implementation(libs.bundles.lifecycle.ktx)

    // Coroutines (using bundle)
    implementation(libs.bundles.coroutines)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.store5)
    // Networking (using bundle)
    implementation(libs.bundles.retrofit)
    implementation(libs.gson)
    implementation(libs.newpipe.extractor)
    // Image Loading for Compose
    implementation(libs.coil.compose)
    implementation(libs.bundles.orbit)
    // Desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    // Media3 (using bundle)
    implementation(libs.bundles.media3.all)
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.reorderable)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.work.runtime)

    // Room Database
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    // Splash Screen
    // Splash Screen
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.appauth)
    implementation(libs.security.crypto)
    // Accompanist
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.swiperefresh)
    implementation(libs.bundles.voyager.core)
    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.work)
    kapt(libs.hilt.compiler)
    kapt(libs.androidx.hilt.compiler)
    // Optional for Jetpack Compose support
    implementation(libs.hilt.navigation.compose)
    implementation(libs.guava)
    // Timber
    implementation(libs.timber)
    implementation(libs.material3)
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.github.adonai:jaudiotagger-android:2.3.15")
}
kapt {
    correctErrorTypes = true
}