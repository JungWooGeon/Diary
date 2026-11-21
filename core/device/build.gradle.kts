// 말로 쓰기 (SpeechRecognizer) + 날씨 (coarse location → Open-Meteo): composable helpers the editor calls.
plugins {
    id("od.android.compose")
    id("od.hilt")
}
android { namespace = "com.pass.diary.core.device" }
dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
