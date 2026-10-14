plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.veyra.tv.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.veyra.tv.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.register<Copy>("copyReleaseApkToDist") {
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    doLast {
        val distDir = rootProject.layout.projectDirectory.dir("dist").asFile
        val src = File(distDir, "app-release.apk")
        if (src.exists()) {
            src.copyTo(File(distDir, "streamsurf-tv-release.apk"), overwrite = true)
            src.copyTo(File(distDir, "veyra-tv-release.apk"), overwrite = true)
        }
    }
}

tasks.register<Copy>("copyDebugApkToDist") {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    doLast {
        val distDir = rootProject.layout.projectDirectory.dir("dist").asFile
        val src = File(distDir, "app-debug.apk")
        if (src.exists()) {
            src.copyTo(File(distDir, "streamsurf-tv-debug.apk"), overwrite = true)
            src.copyTo(File(distDir, "veyra-tv-debug.apk"), overwrite = true)
        }
    }
}

afterEvaluate {
    tasks.findByName("assembleRelease")?.finalizedBy("copyReleaseApkToDist")
    tasks.findByName("assembleDebug")?.finalizedBy("copyDebugApkToDist")
}



