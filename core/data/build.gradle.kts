plugins {
    alias(libs.plugins.audiogarage.android.library)
}

android {
    namespace = "dev.audiogarage.core.data"
}

dependencies {
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(project(":core:model"))
}
