import java.util.Properties
import java.io.FileInputStream

// Make sure to set the package name in your Android Studio project
// This file assumes you've already set namespace to "com.mnivesh.callyn"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // google services & crashlytics plugins
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

android {
    namespace = "com.mnivesh.callyn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mnivesh.callyn"
        minSdk = 27 // InCallService requires API 26 (Android 8.0)
        targetSdk = 36
        versionCode = 6
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val keystorePropertiesFile = rootProject.file("key.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = keystoreProperties.getProperty("storeFile")?.let { file(it) }
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
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
    implementation(libs.androidx.security.crypto)
    // Jetpack Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)
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
    // firebase bom manages versions for all firebase libs
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))

    // analytics & crashlytics (no version needed due to bom)
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
}