// v1 -> v2 migration (workstream B owns src/). Need another dependency? Write it in DEPS.md.
plugins {
    id("od.android.library")
    id("od.hilt")
    id("od.kotlin.serialization")
}
android {
    namespace = "com.pass.diary.core.legacy"
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
