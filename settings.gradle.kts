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
        google()
        mavenCentral()
    }
}

rootProject.name = "PersonalAgent"

include(":shared")
include(":androidApp")
// NOTE: iosApp is an Xcode project, not a Gradle module. It consumes the
// :shared module's framework and is built with Xcode on macOS (see README).
