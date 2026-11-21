plugins { id("od.android.feature") }
android {
    namespace = "com.pass.diary.feature.write"
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(libs.coil.compose)
    implementation(project(":core:device")) // dictation + weather
    implementation(project(":core:vision")) // ML Kit: 사진 태그, 종이 일기, 스티커, 손글씨
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.runtime) // in-memory DiaryDatabase
}
