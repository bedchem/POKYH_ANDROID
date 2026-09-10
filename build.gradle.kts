// Top-level build file — plugins are declared here (apply false) and applied per-module.

// Raises the Kotlin/KSP versions AGP 9's built-in Kotlin compiles with (default is KGP 2.2.10 —
// see https://kotl.in/gradle/agp-built-in-kotlin) to match the version catalog, so kotlin-stdlib
// metadata pulled in transitively by recent Compose/AndroidX releases doesn't outrun what the
// bundled compiler can read ("metadata version X, but the compiler version Y can read up to Z").
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
