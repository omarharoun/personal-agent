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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
