import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    // AGP 9 ships built-in Kotlin support, so no separate kotlin.android plugin.
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.personalagent.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.personalagent.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Keep the ONNX model uncompressed in the APK so ONNX Runtime can read /
        // memory-map it efficiently at runtime instead of inflating ~90 MB.
        noCompress += "onnx"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // On-device embeddings (Step 2): all-MiniLM-L6-v2 via ONNX Runtime Mobile.
    implementation(libs.onnxruntime.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Instrumentation test for the on-device embedder (skips if model absent).
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// --- On-device embedding model provisioning ---------------------------------
//
// The all-MiniLM-L6-v2 weights (~90 MB) are intentionally NOT committed to git
// (see .gitignore). Run this task once to fetch the model + vocab into the
// app's assets so the APK can load them offline at runtime:
//
//     ./gradlew :androidApp:downloadEmbeddingModel
//
// CI / fresh clones without the asset still build fine — the embedder simply
// reports the model as not installed (EmbedderFactory.isModelInstalled).
val embeddingModelDir =
    layout.projectDirectory.dir("src/main/assets/models/all-MiniLM-L6-v2").asFile

tasks.register("downloadEmbeddingModel") {
    group = "ml"
    description = "Downloads all-MiniLM-L6-v2 (ONNX model + vocab) into app assets."
    val base = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main"
    val files = mapOf(
        "model.onnx" to "$base/onnx/model.onnx",
        "vocab.txt" to "$base/vocab.txt",
    )
    val outDir = embeddingModelDir
    doLast {
        outDir.mkdirs()
        files.forEach { (name, url) ->
            val dest = File(outDir, name)
            if (dest.exists() && dest.length() > 0L) {
                logger.lifecycle("✔ $name already present (${dest.length()} bytes)")
                return@forEach
            }
            logger.lifecycle("↓ downloading $name from $url")
            URI(url).toURL().openStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            logger.lifecycle("✔ saved ${dest.length()} bytes → $dest")
        }
    }
}
