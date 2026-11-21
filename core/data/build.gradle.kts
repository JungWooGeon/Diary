plugins {
    id("od.android.library")
    id("od.hilt")
    id("od.kotlin.serialization")
}
android {
    namespace = "com.pass.diary.core.data"
    // The one copy of shared/ (AI data + libraries) in the APK; core:ai and features read it as assets.
    sourceSets["main"].assets.srcDirs(rootProject.file("shared"))
    testOptions { unitTests.isReturnDefaultValues = true }
}
ksp { arg("room.schemaLocation", file("schemas").absolutePath) }
dependencies {
    api(project(":core:model"))
    implementation(project(":core:light"))
    api(libs.androidx.datastore.preferences)
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.exifinterface)
    testImplementation(libs.kotlinx.coroutines.test)
}
