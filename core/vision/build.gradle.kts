// ML Kit behind two interfaces (Vision, Handwriting). Everything runs on the phone; features depend on the interfaces only.
plugins {
    id("od.android.library")
    id("od.hilt")
}
android { namespace = "com.pass.diary.core.vision" }
dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.exifinterface)
    implementation(libs.play.services.base)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.text.korean)
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.ink)
}
