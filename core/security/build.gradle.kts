plugins {
    id("od.android.compose")
    id("od.hilt")
}
android {
    namespace = "com.pass.diary.core.security"
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(project(":core:designsystem"))
    api(libs.androidx.biometric)
    api(libs.androidx.fragment)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
}
