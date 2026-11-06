plugins {
    alias(libs.plugins.audiogarage.android.application)
    alias(libs.plugins.audiogarage.android.compose)
}

android {
    namespace = "dev.audiogarage"

    defaultConfig {
        applicationId = "dev.audiogarage"
        versionCode = 1
        versionName = "0.1.0-poc"
    }

    signingConfigs {
        // Shared, non-secret debug key committed in the repo: every container build
        // produces the same signature, so `adb install -r` keeps upgrading in place.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":feature:downloader"))
    implementation(project(":feature:player"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.junit)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test.junit4)
}
