// On-device AI (workstream D owns src/; C owns shared/ai, shared/library). Need another dependency? Write it in DEPS.md.
plugins {
    id("od.android.library")
    id("od.hilt")
    id("od.kotlin.serialization")
}
android {
    namespace = "com.pass.diary.core.ai"
    // shared/ is packaged once, by core:data (two libraries adding the same asset dir = duplicate assets in the app).
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.litert)
    testImplementation(libs.kotlinx.coroutines.test)
}
