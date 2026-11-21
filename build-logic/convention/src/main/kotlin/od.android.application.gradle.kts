plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("od.kotlin.serialization")
    id("od.hilt")
}

android {
    compileSdk = Sdk.COMPILE
    defaultConfig {
        minSdk = Sdk.MIN
        targetSdk = Sdk.TARGET
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
