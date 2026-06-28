import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.Properties

plugins {
    // AGP 9 ships built-in Kotlin support, so no separate kotlin.android plugin.
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// 🔒 Release signing is read from a gitignored `keystore.properties` at the repo
// root (see keystore.properties.example + README "Android release build"). The
// real release keystore is NEVER committed. If the file is absent (e.g. in this
// dev sandbox / CI without the key), the release build falls back to debug
// signing so it still builds — a debug-signed AAB is NOT uploadable to Play.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystorePropertiesFile.exists()

android {
    namespace = "com.personalagent.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.personalagent.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Created only when keystore.properties is present; otherwise the release
        // build below falls back to debug signing.
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
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
            // R8: code minification + resource shrinking. Keep rules that protect
            // the native/reflective libs live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                // Dev/CI fallback so the release artifact still builds here. The
                // result is DEBUG-signed and must NOT be uploaded to Play — supply
                // keystore.properties + a real release key first (see README).
                signingConfigs.getByName("debug")
            }
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

    // Robust model download (foreground service, survives background/lock/death).
    implementation(libs.androidx.work.runtime.ktx)

    // On-device embeddings (Step 2): all-MiniLM-L6-v2 via ONNX Runtime Mobile.
    implementation(libs.onnxruntime.android)

    // On-device LLM (Step 3): small local model via MediaPipe LLM Inference API.
    implementation(libs.mediapipe.tasks.genai)

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

// --- On-device LLM model provisioning (Step 3) ------------------------------
//
// The MediaPipe `.task` LLM bundles (~0.16–1.1 GB) are NOT committed to git and
// are NOT packaged in the APK. Normally the in-app downloader fetches an ungated
// `.task` from the curated catalog (DefaultModelCatalog). This dev task lets you
// instead adb-push a local `.task` bundle to the app's external-files dir; the app
// loads whichever `*.task` is present (see LlmModelProvisioning.resolveModelFile).
//
//     ./gradlew :androidApp:pushLlmModel -PllmModel=/abs/path/SomeModel.task
//
// Clones / installs without a model still build and run — the feature is gated
// by LlmModelProvisioning.isModelInstalled() / OnDeviceLlm.isAvailable.
tasks.register<Exec>("pushLlmModel") {
    group = "ml"
    description = "adb-push a local .task LLM bundle to the app's external files dir."

    val appId = android.defaultConfig.applicationId
    val modelPath = (project.findProperty("llmModel") as String?)
    val deviceDir = "/sdcard/Android/data/$appId/files/models/llm"
    // Push under the source file's own name; the app resolves any *.task present.
    val pushedName = modelPath?.substringAfterLast('/')?.ifEmpty { "model.task" } ?: "model.task"
    val deviceFile = "$deviceDir/$pushedName"

    doFirst {
        require(modelPath != null) {
            "Provide the model path: -PllmModel=/abs/path/to/model.task"
        }
        require(File(modelPath).exists()) { "Model file not found: $modelPath" }
        logger.lifecycle("↑ pushing $modelPath → $deviceFile")
    }
    // Single shell invocation: make the dir, then push. Resolved lazily so a
    // configuration pass without -PllmModel doesn't fail; doFirst enforces it.
    commandLine(
        "sh", "-c",
        "adb shell mkdir -p \"$deviceDir\" && adb push \"${modelPath ?: ""}\" \"$deviceFile\"",
    )
}
