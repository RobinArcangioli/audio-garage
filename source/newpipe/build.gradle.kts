// Option A: NewPipeExtractor resolves the stream, OkHttp downloads it (app-controlled).
plugins {
    alias(libs.plugins.audiogarage.jvm.library)
}

dependencies {
    api(project(":core:source"))
    api(libs.okhttp)
    implementation(libs.newpipe.extractor)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
