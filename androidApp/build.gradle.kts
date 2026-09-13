import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.lumareader.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.lumareader"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
}

val targetApk = layout.buildDirectory.file("outputs/apk/debug/androidApp-debug.apk")
val destinationApk = File(rootDir, "LumaReader-debug.apk")

tasks.register("copyDebugApkToRoot") {
    notCompatibleWithConfigurationCache("Copies output APK to project root directory")
    doLast {
        val src = targetApk.get().asFile
        if (src.exists()) {
            src.copyTo(destinationApk, overwrite = true)
        }
    }
}

afterEvaluate {
    tasks.findByName("assembleDebug")?.finalizedBy("copyDebugApkToRoot")
}

