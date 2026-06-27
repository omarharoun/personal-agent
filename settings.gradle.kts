pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            // Scope Google's Maven to the artifacts it actually hosts. Without
            // this filter, non-Google deps (e.g. io.ktor) are first sought on
            // dl.google.com; a miss/unreachable host there aborts resolution
            // before Maven Central is tried.
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "PersonalAgent"

include(":shared")
include(":androidApp")
// NOTE: iosApp is an Xcode project, not a Gradle module. It consumes the
// :shared module's framework and is built with Xcode on macOS (see README).
