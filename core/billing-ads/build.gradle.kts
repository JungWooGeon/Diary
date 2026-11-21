import java.util.Properties

// AdMob (banner / native / rewarded) + UMP consent + Play Billing one-time `premium` (ARCHITECTURE §7).
plugins {
    id("od.android.compose")
    id("od.hilt")
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
// Google's official AdMob test unit IDs (debug builds). Release reads 온이 일기's own units from local.properties.
val testAdIds = mapOf(
    "banner_ad_unit_id" to "ca-app-pub-3940256099942544/6300978111",
    "native_ad_unit_id" to "ca-app-pub-3940256099942544/2247696110",
    "reward_ad_unit_id" to "ca-app-pub-3940256099942544/5224354917",
)

android {
    namespace = "com.pass.diary.core.billing"
    buildFeatures { buildConfig = true }
    buildTypes {
        debug { testAdIds.forEach { (key, id) -> buildConfigField("String", key.uppercase(), "\"$id\"") } }
        release {
            testAdIds.keys.forEach { key -> buildConfigField("String", key.uppercase(), "\"${localProps.getProperty(key, "")}\"") }
        }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

// Never ship a release with missing ad IDs (admob_app_id goes into the app manifest, see app/build.gradle.kts).
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        val missing = (testAdIds.keys + "admob_app_id").filter { localProps.getProperty(it).isNullOrBlank() }
        require(missing.isEmpty()) { "local.properties에 AdMob ID가 없습니다: $missing (온이 일기용 광고 단위를 새로 만들어 넣으세요)" }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    api(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
