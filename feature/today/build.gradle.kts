plugins { id("od.android.feature") }
android {
    namespace = "com.pass.diary.feature.today"
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(libs.coil.compose)
    implementation(project(":core:sync")) // 백업 경고 띠
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.runtime) // in-memory DiaryDatabase
}
