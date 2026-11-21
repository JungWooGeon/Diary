import java.util.Properties

plugins { id("od.android.application") }

val localProps = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }

android {
    namespace = "com.pass.diary"
    defaultConfig {
        applicationId = "com.pass.diary"
        versionCode = 200
        versionName = "2.0.0"
        // ONNX Runtime + LiteRT-LM natives: 64-bit only (LiteRT-LM ships no 32-bit; 32-bit phones fail the 8GB gate anyway).
        // x86_64 keeps Intel emulators working.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        // AdMob app id: Google's sample id for debug; 온이 일기's own from local.properties for release
        // (core:billing-ads fails the release build when it is missing).
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    }
    buildTypes {
        release {
            manifestPlaceholders["admobAppId"] = localProps.getProperty("admob_app_id", "")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release-like (R8 + resource shrinking) but debug-signed with Google's test AdMob ids, so it builds without
        // 온이 일기's real ids: for checking what minification changes (e.g. the ad allowlist's route names).
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".bench" // installs next to the debug app instead of over its data
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            matchingFallbacks += listOf("debug") // libraries' debug variants carry the test ad unit ids
        }
    }
    androidResources { localeFilters += listOf("ko") }
    // Debug-only LLM benchmark (LlmBenchActivity) reads the 30 eval diaries.
    sourceSets["debug"].assets.srcDirs(rootProject.file("tools/model-eval/data"))
    buildFeatures { buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true } // android.util.Log in JVM tests
}

dependencies {
    listOf(
        ":core:model", ":core:data", ":core:light", ":core:designsystem", ":core:security", ":core:legacy", ":core:sync", ":core:billing-ads", ":core:ai",
        ":feature:today", ":feature:write", ":feature:calendar", ":feature:report", ":feature:my",
    ).forEach { implementation(project(it)) }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)
    implementation(libs.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    debugImplementation(libs.androidx.room.runtime) // DebugSeed clears the DB
    testImplementation(libs.junit)
}
