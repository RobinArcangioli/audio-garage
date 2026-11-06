package dev.audiogarage.source.newpipe

import dev.audiogarage.core.source.AudioSourceException.Kind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.io.IOException

class ExtractorFailureMappingTest {

    private fun kindOf(e: Exception) = NewPipeAudioSource.mapExtractorFailure("vid", e).kind

    @Test
    fun `anti-bot signals stop everything`() {
        assertEquals(Kind.BLOCKED, kindOf(ReCaptchaException("captcha", "https://youtube.com")))
        assertEquals(Kind.BLOCKED, kindOf(SignInConfirmNotBotException("sign in")))
    }

    @Test
    fun `restricted content is unavailable, not retryable`() {
        listOf(
            PrivateContentException("private"),
            AgeRestrictedContentException("age"),
            GeographicRestrictionException("geo"),
        ).forEach {
            assertEquals(Kind.UNAVAILABLE, kindOf(it))
            assertEquals(false, kindOf(it).retryable)
        }
    }

    @Test
    fun `parsing failures and IO errors are told apart`() {
        assertEquals(Kind.EXTRACTION, kindOf(ParsingException("changed layout")))
        assertEquals(Kind.NETWORK, kindOf(IOException("reset")))
        assertEquals(true, kindOf(IOException("reset")).retryable)
    }
}
