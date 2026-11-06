package dev.audiogarage.core.model

/** Build identity, shown on the POC screen and later attached to benchmark reports. */
data class BuildInfo(
    val versionName: String,
    val versionCode: Int,
    val debug: Boolean,
)
