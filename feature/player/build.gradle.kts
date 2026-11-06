plugins {
    alias(libs.plugins.audiogarage.android.library)
    alias(libs.plugins.audiogarage.android.compose)
}

android {
    namespace = "dev.audiogarage.feature.player"
}

dependencies {
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(project(":core:model"))
    implementation(project(":core:data"))
}
