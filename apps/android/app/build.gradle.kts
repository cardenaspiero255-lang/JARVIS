import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
