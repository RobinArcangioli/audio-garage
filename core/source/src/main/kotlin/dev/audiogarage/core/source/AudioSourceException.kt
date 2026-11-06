package dev.audiogarage.core.source

/**
 * Failure of [AudioSource.fetch], classified so that callers (retry policy, circuit
 * breaker from step 0.9) can decide without parsing messages.
 */
class AudioSourceException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
    /** HTTP status when the failure comes from an HTTP response. */
    val httpStatus: Int? = null,
) : Exception(message, cause) {

    enum class Kind(val retryable: Boolean) {
        /** Removed, private, age or region restricted: retrying never helps. */
        UNAVAILABLE(retryable = false),

        /** YouTube asks to prove we are not a bot (captcha, sign-in, 429). Stop all calls. */
        BLOCKED(retryable = false),

        /** No stream matching the requested format (M4A). */
        NO_SUITABLE_STREAM(retryable = false),

        /** Transient network or server problem. */
        NETWORK(retryable = true),

        /** The extractor could not parse YouTube's answer (often means it needs an update). */
        EXTRACTION(retryable = false),
    }
}
