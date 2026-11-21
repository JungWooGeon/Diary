// A feature = one screen group: UI + ViewModel + its navigation entry points. Features never depend on each other;
// the app module wires navigation between them.
plugins {
    id("od.android.compose")
    id("od.hilt")
    id("od.kotlin.serialization")
}

dependencies {
    "implementation"(project(":core:model"))
    "implementation"(project(":core:data"))
    "implementation"(project(":core:light"))
    "implementation"(project(":core:designsystem"))
    "implementation"(lib("androidx-activity-compose"))
    "implementation"(lib("androidx-navigation-compose"))
    "implementation"(lib("androidx-hilt-navigation-compose"))
    "implementation"(lib("androidx-lifecycle-runtime-compose"))
    "implementation"(lib("androidx-lifecycle-viewmodel-compose"))
    "implementation"(lib("kotlinx-serialization-json"))
    "testImplementation"(lib("kotlinx-coroutines-test"))
}
