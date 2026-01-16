package dev.labs910.voicecode.data

import dev.labs910.voicecode.data.remote.VoiceInputError
import dev.labs910.voicecode.data.remote.VoiceInputState
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for VoiceInputManager state and error handling.
 * Note: Full integration tests require Android instrumentation.
 */
class VoiceInputManagerTest {

    @Test
    fun `VoiceInputState has correct values`() {
        val states = VoiceInputState.values()
        assertEquals(5, states.size)
        assertTrue(states.contains(VoiceInputState.IDLE))
        assertTrue(states.contains(VoiceInputState.STARTING))
        assertTrue(states.contains(VoiceInputState.RECORDING))
        assertTrue(states.contains(VoiceInputState.PROCESSING))
        assertTrue(states.contains(VoiceInputState.ERROR))
    }

    @Test
    fun `VoiceInputError has correct error messages`() {
        assertEquals("Audio recording error", VoiceInputError.AUDIO_ERROR.getMessage())
        assertEquals("Microphone permission denied", VoiceInputError.PERMISSION_DENIED.getMessage())
        assertEquals("Network error", VoiceInputError.NETWORK_ERROR.getMessage())
        assertEquals("No speech recognized", VoiceInputError.NO_MATCH.getMessage())
        assertEquals("Speech recognizer busy", VoiceInputError.RECOGNIZER_BUSY.getMessage())
        assertEquals("No speech detected", VoiceInputError.SPEECH_TIMEOUT.getMessage())
    }

    @Test
    fun `VoiceInputError isRetryable returns correct values`() {
        // Retryable errors
        assertTrue(VoiceInputError.NETWORK_ERROR.isRetryable())
        assertTrue(VoiceInputError.NETWORK_TIMEOUT.isRetryable())
        assertTrue(VoiceInputError.SERVER_ERROR.isRetryable())
        assertTrue(VoiceInputError.RECOGNIZER_BUSY.isRetryable())

        // Non-retryable errors
        assertFalse(VoiceInputError.AUDIO_ERROR.isRetryable())
        assertFalse(VoiceInputError.PERMISSION_DENIED.isRetryable())
        assertFalse(VoiceInputError.NO_MATCH.isRetryable())
        assertFalse(VoiceInputError.SPEECH_TIMEOUT.isRetryable())
        assertFalse(VoiceInputError.CLIENT_ERROR.isRetryable())
        assertFalse(VoiceInputError.UNKNOWN.isRetryable())
    }

    @Test
    fun `VoiceInputError all cases have messages`() {
        for (error in VoiceInputError.values()) {
            assertNotNull(error.getMessage())
            assertTrue(error.getMessage().isNotEmpty())
        }
    }
}
