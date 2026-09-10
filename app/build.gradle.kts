import java.io.FileInputStream
import java.util.Properties

// AGP 9's built-in Kotlin compilation is used instead of the standalone
// org.jetbrains.kotlin.android plugin (KGP explicitly refuses to apply itself under AGP 9+ —
// "no longer required since AGP 9.0"). The Kotlin/KSP versions built-in Kotlin actually uses
// are raised via a buildscript classpath override in the ROOT build.gradle.kts (AGP 9's
// documented mechanism — see https://kotl.in/gradle/agp-built-in-kotlin), matching the
// Kotlin catalog version so kotlin-stdlib metadata (pulled in transitively by the Compose BOM)
// doesn't outrun what the bundled compiler can read.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Local secrets — see local.properties.example. Never committed.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
fun secret(key: String): String = (localProperties.getProperty(key) ?: System.getenv(key) ?: "").let {
    "\"" + it.replace("\"", "\\\"") + "\""
}

android {
    namespace = "dev.plattnericus.pokyh"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.plattnericus.pokyh"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "BACKEND_API_KEY", secret("POKYH_API_KEY"))
        buildConfigField("String", "BACKEND_SERVER_KEY", secret("POKYH_SERVER_KEY"))
        buildConfigField("String", "BACKEND_BASE_URL", "\"https://api.pokyh.com\"")
        buildConfigField("String", "UNTIS_BASE_URL", "\"https://lbs-brixen.webuntis.com/WebUntis\"")
        buildConfigField("String", "UNTIS_SCHOOL", "\"lbs-brixen\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Debug builds use the auto-generated debug keystore (fine for the emulator).
        // A dedicated release keystore is created separately before any Play Store upload.
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.runtime)

    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.process)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.biometric)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
}
