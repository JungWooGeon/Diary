plugins { id("od.android.feature") }
android { namespace = "com.pass.diary.feature.my" }
dependencies {
    implementation(project(":core:security"))
    implementation(project(":core:sync"))
    implementation(project(":core:billing-ads"))
    implementation(project(":core:reminder"))
    implementation(libs.androidx.browser) // 약관·방침을 앱 안 브라우저(Custom Tabs)로 열기
    implementation(libs.work.runtime) // SyncScheduler returns WorkManager types
}
