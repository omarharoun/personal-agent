# R8 / ProGuard keep rules for the release build.
#
# The app links several native (JNI) and reflective libraries that R8 would
# otherwise strip or rename, breaking them at runtime. Keep the minimum needed.
# Re-verify after any dependency bump (a release smoke test on a device is the
# real check — see docs/PLAY_RELEASE.md).

# ---------------------------------------------------------------------------
# General: JNI native methods + the classes that declare them.
# ---------------------------------------------------------------------------
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, Exceptions

# ---------------------------------------------------------------------------
# ONNX Runtime (Step 2 embeddings) — ai.onnxruntime.*, native + reflective.
# ---------------------------------------------------------------------------
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ---------------------------------------------------------------------------
# MediaPipe tasks-genai (Step 3 on-device LLM) — native + protobuf/reflection.
# ---------------------------------------------------------------------------
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn com.google.auto.value.**
-dontwarn javax.lang.model.**

# ---------------------------------------------------------------------------
# Ktor (Step 4 cloud transport) + OkHttp engine. Engines are discovered via
# ServiceLoader (META-INF/services), which R8 full-mode can drop — keep them.
# ---------------------------------------------------------------------------
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class io.ktor.client.engine.okhttp.** { *; }
-keepclassmembers class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-dontwarn io.ktor.**
# OkHttp / Okio (ship their own consumer rules, but be defensive).
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# kotlinx.serialization — generated $serializer classes are accessed reflectively.
# ---------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.**
# Keep the synthetic serializer companions + the Companion that exposes serializer().
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers, allowshrinking class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
# Our own @Serializable models (Note/Reminder/PlanItem/MemoryEntry/TrustedContact/
# cache + crypto blobs) live under the shared module's package.
-keep @kotlinx.serialization.Serializable class com.personalagent.shared.** { *; }
-keepclassmembers class com.personalagent.shared.** {
    *** Companion;
}

# ---------------------------------------------------------------------------
# Kotlin metadata / coroutines internals that reflection touches.
# ---------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---------------------------------------------------------------------------
# Jetpack Compose / AndroidX ship their own consumer rules; nothing extra needed.
# Keep enum valueOf/values (used reflectively in a few spots).
# ---------------------------------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
