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
        // JitPack only serves NewPipe artifacts: nothing else may resolve from it.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.TeamNewPipe") }
        }
    }
}

rootProject.name = "audio-garage"

include(
    ":app",
    ":core:model",
    ":core:data",
    ":core:source",
    ":source:newpipe",
    ":tools:probe",
    ":feature:downloader",
    ":feature:player",
)
