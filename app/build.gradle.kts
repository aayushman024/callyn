// Make sure to set the package name in your Android Studio project
// This file assumes you've already set namespace to "com.mnivesh.callyn"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mnivesh.callyn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mnivesh.callyn"
        minSdk = 27 // InCallService requires API 26 (Android 8.0)
        targetSdk = 36
        versionCode = 1
        versionName = "1.3.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true // Enable Jetpack Compose
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android & Kotlin
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation(libs.sqlcipher)
    // Jetpack Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.navigation.compose)

    // --- THIS IS THE FIX ---
    implementation(libs.androidx.browser) // Use alias, not hardcoded version
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.core)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.ui.text)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.animation.core)
    implementation(libs.androidx.ui.graphics)
    ksp(libs.androidx.room.compiler)
    // -----------------------

    // For observing state from the CallManager (e.g., callState.collectAsState())
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Icons
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.lottie.compose)
    // --- THIS IS THE FIX (Incorrect aliases) ---
    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)  // Was libs.converter.gson
    implementation(libs.okhttp.logging.interceptor) // Was libs.logging.interceptor
    // implementation(libs.okhttp) // This alias doesn't exist and is not needed
    // -------------------------------------------

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// NOTE: Your `gradle/libs.versions.toml` file will define what 'libs.androidx.core.ktx'
// maps to (e.g., androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.13.1" })