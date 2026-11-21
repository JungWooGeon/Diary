plugins { id("od.android.feature") }
android {
    namespace = "com.pass.diary.feature.write"
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(libs.coil.compose)
    implementation(project(":core:device")) // dictation + weather
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.runtime) // in-memory DiaryDatabase
}
