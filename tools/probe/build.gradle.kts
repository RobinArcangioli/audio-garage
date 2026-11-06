// Developer tool (never packaged in the APK): exercises an AudioSource from the JVM,
// against real YouTube, and records timings. Run through `make probe`.
plugins {
    alias(libs.plugins.audiogarage.jvm.library)
    application
}

application {
    mainClass.set("dev.audiogarage.tools.probe.ProbeKt")
}

dependencies {
    implementation(project(":source:newpipe"))
    implementation(libs.newpipe.extractor)
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
