pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Diary"
// _v1/ (old presentation/domain/data) is kept for reference only and is not part of the build.
include(":app")
include(
    ":core:model", ":core:data", ":core:light", ":core:designsystem", ":core:security",
    ":core:legacy", ":core:sync", ":core:ai", ":core:billing-ads", ":core:device", ":core:reminder",
)
include(":feature:today", ":feature:write", ":feature:calendar", ":feature:report", ":feature:my")
