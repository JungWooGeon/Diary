// Drive appDataFolder backup/sync (workstream B owns src/). Need another dependency? Write it in DEPS.md.
plugins {
    id("od.android.library")
    id("od.hilt")
    id("od.kotlin.serialization")
}
android {
    namespace = "com.pass.diary.core.sync"
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:light")) // diary.html light colors (내보내기)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
