plugins { id("od.android.compose") }
android { namespace = "com.pass.diary.core.designsystem" }
dependencies {
    api(project(":core:model"))
    api(project(":core:light"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons)
}
