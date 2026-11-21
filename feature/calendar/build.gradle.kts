plugins { id("od.android.feature") }
android { namespace = "com.pass.diary.feature.calendar" }
dependencies {
    implementation(project(":core:billing-ads"))
    implementation(libs.coil.compose)
}
