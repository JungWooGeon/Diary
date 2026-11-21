plugins { id("od.android.feature") }
android {
    namespace = "com.pass.diary.feature.today"
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(libs.coil.compose)
    implementation(project(":core:sync")) // 백업 경고 띠
    implementation(project(":core:billing-ads")) // 공유 카드 · 결제를 권하는 순간
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.runtime) // in-memory DiaryDatabase
}
