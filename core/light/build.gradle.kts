// Pure Kotlin (no Android) so the formula is easy to port to the web; shared/light-test-vectors.json pins it.
plugins {
    id("od.jvm.library")
    id("od.kotlin.serialization")
}
dependencies {
    api(project(":core:model"))
    testImplementation(libs.kotlinx.serialization.json)
}
tasks.withType<Test>().configureEach { systemProperty("shared.dir", rootProject.file("shared").absolutePath) }
