plugins {
    id("od.jvm.library")
    id("od.kotlin.serialization")
}
dependencies { api(libs.kotlinx.serialization.json) }
