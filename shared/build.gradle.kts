import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    // --- Android target (consumed by androidApp via Jetpack Compose) ---
    // AGP 9 KMP library DSL: declares the `android` target inline.
    android {
        namespace = "com.personalagent.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    // --- JVM target: runs the shared business logic + unit tests on any
    //     desktop/CI machine (including this Linux sandbox) without a device. ---
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // --- iOS targets: the framework is consumed by the SwiftUI app (macOS/Xcode). ---
    val xcfName = "Shared"
    iosX64 { binaries.framework { baseName = xcfName } }
    iosArm64 { binaries.framework { baseName = xcfName } }
    iosSimulatorArm64 { binaries.framework { baseName = xcfName } }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // Step 4 cloud transport: portable Ktor client + JSON content negotiation.
            // The concrete engine is supplied per platform (below) or injected in tests.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            // MockEngine: exercises HttpCloudClient with no real network.
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            // Android cloud engine.
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            // iOS cloud engine (NSURLSession-backed).
            implementation(libs.ktor.client.darwin)
        }
    }
}
