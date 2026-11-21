plugins { id("od.android.feature") }
android { namespace = "com.pass.diary.feature.my" }
dependencies {
    implementation(project(":core:security"))
    implementation(project(":core:sync"))
    implementation(project(":core:billing-ads"))
    implementation(libs.work.runtime) // SyncScheduler returns WorkManager types
}
