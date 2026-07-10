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
    // isStatic = true links Shared into the app's main executable instead of shipping
    // a separate Shared.framework/Shared dylib under Frameworks/. A free-Apple-ID
    // sideload (AltServer-Linux) re-signs the main executable but not nested dylibs,
    // and iOS then refuses to spawn the app (posix_spawn EBADEXEC / errno 85). With no
    // nested code to leave unsigned, the re-signed main binary launches cleanly.
    val xcfName = "Shared"
    iosX64 { binaries.framework { baseName = xcfName; isStatic = true } }
    iosArm64 { binaries.framework { baseName = xcfName; isStatic = true } }
    iosSimulatorArm64 { binaries.framework { baseName = xcfName; isStatic = true } }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
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
        // JVM-only test deps: a REAL Ktor engine (CIO) so the live-Hermes
        // integration test can hit a running instance. Opt-in via env vars; the
        // test skips when they're absent, so CI stays hermetic.
        jvmTest.dependencies {
            implementation(libs.ktor.client.cio)
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
