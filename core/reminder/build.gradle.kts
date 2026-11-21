// 기록 알림 (PLAN §3.3) + 1년 전 오늘 알림 (§3.4): settings (DataStore), daily alarm, notifications.
plugins {
    id("od.android.library")
    id("od.hilt")
}
android { namespace = "com.pass.diary.core.reminder" }
dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
}
