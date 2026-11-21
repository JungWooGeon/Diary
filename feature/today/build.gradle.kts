plugins { id("od.android.feature") }
android {
    namespace = "com.pass.diary.feature.today"
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(libs.coil.compose)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.runtime) // in-memory DiaryDatabase
}
