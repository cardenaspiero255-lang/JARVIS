import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest
import java.io.File
import java.io.FileOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cardenaspiero255.jarvis"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cardenaspiero255.jarvis"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Isolated debug install: coexists with Qwen PR #53 preview and normal
        // JARVIS. This experimental hands-free package has separate private data.
        debug {
            applicationIdSuffix = ".handsfreepreview"
            versionNameSuffix = "-jarvis-os-v3-preview"
            resValue("string", "jarvis_application_label", "JARVIS MANOS LIBRES TEST")
        }
        release {
            isMinifyEnabled = false
            resValue("string", "jarvis_application_label", "JARVIS OMEGA")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Permission inspection only; privileged execution requires separate reviewed adapters.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("io.github.iamr0s:Dhizuku-API:2.5.4")
    // Apache-2.0 offline Spanish STT; never depends on Android's speech service.
    implementation("com.alphacephei:vosk-android:0.3.47")
    // Official sherpa-onnx AAR (Kotlin/JNI). Pinned SHA-256 below. The KWS
    // engine is disabled if its independent model assets are not verified.
    implementation("com.k2fsa.sherpa.release:sherpa-onnx:1.13.8@aar")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

/**
 * Bundles the small Vosk Spanish language model INSIDE debug/release APKs.
 * Pin SHA256 so the app cannot silently ship substituted remote model bytes.
 * Build machines download this official asset; phones need NO INTERNET access.
 */
val offlineSpeechAssets = layout.buildDirectory.dir("generated/offlineSpanishAssets")
val offlineSpeechArchive = offlineSpeechAssets.map { it.file("vosk-model-small-es-0.42.zip") }

val bundleOfflineSpanish by tasks.registering {
    group = "build"
    description = "Verify and include offline Spanish Vosk model (Apache 2.0)"
    outputs.file(offlineSpeechArchive)
    doLast {
        val target = offlineSpeechArchive.get().asFile
        val expected = "09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f"
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        if (target.isFile && sha256(target) == expected) return@doLast
        target.parentFile.mkdirs()
        val sources = listOf(
            "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
            "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-es-0.42.zip"
        )
        var success = false
        val destination = File(target.parentFile, target.name + ".download")
        for (source in sources) {
            try {
                destination.delete()
                val conn = URI.create(source).toURL().openConnection()
                conn.connectTimeout = 25_000
                conn.readTimeout = 35_000
                conn.getInputStream().buffered().use { stream ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= 55_000_000L) {
                                "Model download exceeded 55MB safety limit"
                            }
                            output.write(buffer, 0, n)
                        }
                    }
                }
                require(sha256(destination) == expected) {
                    "Offline Spanish model SHA-256 mismatch"
                }
                require(destination.renameTo(target)) { "Failed to install verified asset" }
                success = true
                break
            } catch (e: Exception) {
                logger.warn("Offline Spanish model download failed from verified source: ${e.message}")
                destination.delete()
            }
        }
        check(success) {
            "Could not acquire verified Vosk Spanish model; refusing an incomplete APK"
        }
    }
}

android.sourceSets.getByName("main").assets.srcDir(offlineSpeechAssets)
tasks.named("preBuild") { dependsOn(bundleOfflineSpanish) }

/**
 * Verify bytes of the exact Android JNI/Kotlin AAR published upstream
 * (GitHub release v1.13.8), not merely a Maven coordinate. Fail the build
 * on a missing/substituted binary; do not silently ship a different native lib.
 */
val verifySherpaAar by tasks.registering {
    group = "verification"
    description = "Verify official sherpa-onnx 1.13.8 Android AAR SHA-256"
    doLast {
        val artifacts = configurations.getByName("debugRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts
        val artifact = artifacts.firstOrNull {
            it.moduleVersion.id.group == "com.k2fsa.sherpa.release" &&
                it.name == "sherpa-onnx" &&
                it.moduleVersion.id.version == "1.13.8"
        } ?: error("Official sherpa-onnx Android AAR dependency is absent")
        val digest = MessageDigest.getInstance("SHA-256")
        artifact.file.inputStream().buffered().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(bytes)
                if (n < 0) break
                digest.update(bytes, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96") {
            "Sherpa native AAR SHA-256 mismatch: refusing APK"
        }
    }
}
tasks.named("preBuild") { dependsOn(verifySherpaAar) }
