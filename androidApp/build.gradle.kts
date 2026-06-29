import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest
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
        versionCode = 4
        versionName = "1.2.0-bundled"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Sideload build (not Play): ship ONLY arm64-v8a native libs (ONNX Runtime
        // + MediaPipe) to keep the APK as small as possible now that model weights
        // are bundled. arm64-v8a covers essentially all modern physical devices.
        ndk {
            abiFilters += "arm64-v8a"
        }
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
        // Keep the bundled model weights uncompressed in the APK: the ONNX embedder
        // and the MediaPipe `.task` LLM are both copied out to internal storage and
        // read from a file path, so compressing them in the APK would only add a
        // slow inflate step (and risks the AssetManager compressed-stream limits on
        // very large files). High-entropy quantized weights barely compress anyway.
        noCompress += "onnx"
        noCompress += "task"
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

// --- Bundled on-device model provisioning -----------------------------------
//
// The model weights are BUNDLED into the APK as assets so the app works out of
// the box (no download): the all-MiniLM-L6-v2 embedder + a small SmolLM-135M chat
// model. They are intentionally NOT committed to git (see .gitignore: *.onnx,
// *.task, **/models/). Provision them once before building:
//
//     ./gradlew :androidApp:bundleModels      # both
//     ./gradlew :androidApp:downloadEmbeddingModel
//     ./gradlew :androidApp:downloadLlmModel
//
// (or run scripts/fetch-bundled-models.sh). Each download is verified against a
// pinned sha256. CI / fresh clones without the assets still BUILD fine — the
// loaders report the model as not installed and the fail-soft paths take over.

/** Download [url] to [dest] (skip if present), then verify its sha256 == [sha256]. */
fun Task.fetchAndVerify(url: String, dest: File, sha256: String, label: String) {
    dest.parentFile.mkdirs()
    if (!(dest.exists() && dest.length() > 0L)) {
        logger.lifecycle("↓ downloading $label …")
        URI(url).toURL().openStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }
    val md = MessageDigest.getInstance("SHA-256")
    dest.inputStream().use { ins ->
        val buf = ByteArray(1 shl 16)
        while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
    }
    val got = md.digest().joinToString("") { "%02x".format(it) }
    require(got == sha256) { "$label sha256 mismatch:\n  expected $sha256\n  got      $got" }
    logger.lifecycle("✔ $label ok (${dest.length()} bytes, sha256 verified)")
}

val embeddingModelDir =
    layout.projectDirectory.dir("src/main/assets/models/all-MiniLM-L6-v2").asFile
val llmModelDir =
    layout.projectDirectory.dir("src/main/assets/models/llm").asFile

tasks.register("downloadEmbeddingModel") {
    group = "ml"
    description = "Downloads all-MiniLM-L6-v2 (INT8 ONNX + vocab) into app assets."
    doLast {
        // Xenova INT8 dynamic-quantized export (~22 MB) — Apache-2.0. I/O matches
        // AndroidEmbedder: input_ids/attention_mask/token_type_ids → last_hidden_state.
        fetchAndVerify(
            "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx",
            File(embeddingModelDir, "model.onnx"),
            "afdb6f1a0e45b715d0bb9b11772f032c399babd23bfc31fed1c170afc848bdb1",
            "all-MiniLM-L6-v2 model.onnx (int8)",
        )
        fetchAndVerify(
            "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/vocab.txt",
            File(embeddingModelDir, "vocab.txt"),
            "07eced375cec144d27c900241f3e339478dec958f92fddbc551f295c992038a3",
            "all-MiniLM-L6-v2 vocab.txt",
        )
    }
}

tasks.register("downloadLlmModel") {
    group = "ml"
    description = "Downloads SmolLM-135M-Instruct (MediaPipe .task, q8) into app assets."
    doLast {
        // Google litert-community ungated `.task` (~159 MB) — Apache-2.0. Same file
        // + sha256 as DefaultModelCatalog's smollm-135m entry; loads in MediaPipe.
        fetchAndVerify(
            "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task",
            File(llmModelDir, "SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task"),
            "6987dce5ac4f71032b070cf13412a5de0e49c04d271a053fc7d9d59a0dc104e9",
            "SmolLM-135M-Instruct .task (q8)",
        )
    }
}

tasks.register("bundleModels") {
    group = "ml"
    description = "Fetch + verify ALL bundled on-device models (embedder + chat LLM)."
    dependsOn("downloadEmbeddingModel", "downloadLlmModel")
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
