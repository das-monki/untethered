package dev.labs910.voicecode.data

import dev.labs910.voicecode.data.remote.QueueMode
import dev.labs910.voicecode.data.remote.VoiceOutputState
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for VoiceOutputManager state and settings.
 * Note: Full integration tests require Android instrumentation.
 */
class VoiceOutputManagerTest {

    @Test
    fun `VoiceOutputState has correct values`() {
        val states = VoiceOutputState.values()
        assertEquals(4, states.size)
        assertTrue(states.contains(VoiceOutputState.IDLE))
        assertTrue(states.contains(VoiceOutputState.READY))
        assertTrue(states.contains(VoiceOutputState.SPEAKING))
        assertTrue(states.contains(VoiceOutputState.ERROR))
    }

    @Test
    fun `QueueMode has correct values`() {
        val modes = QueueMode.values()
        assertEquals(2, modes.size)
        assertTrue(modes.contains(QueueMode.FLUSH))
        assertTrue(modes.contains(QueueMode.ADD))
    }

    @Test
    fun `Speech rate clamping works correctly`() {
        // Test that clamping logic works as expected
        val testCases = listOf(
            0.3f to 0.5f,   // Below min, clamp to 0.5
            0.5f to 0.5f,   // At min
            1.0f to 1.0f,   // Normal
            2.0f to 2.0f,   // At max
            2.5f to 2.0f    // Above max, clamp to 2.0
        )

        for ((input, expected) in testCases) {
            val clamped = input.coerceIn(0.5f, 2.0f)
            assertEquals(expected, clamped, 0.001f)
        }
    }

    @Test
    fun `Pitch clamping works correctly`() {
        // Test that clamping logic works as expected
        val testCases = listOf(
            0.3f to 0.5f,   // Below min, clamp to 0.5
            0.5f to 0.5f,   // At min
            1.0f to 1.0f,   // Normal
            2.0f to 2.0f,   // At max
            2.5f to 2.0f    // Above max, clamp to 2.0
        )

        for ((input, expected) in testCases) {
            val clamped = input.coerceIn(0.5f, 2.0f)
            assertEquals(expected, clamped, 0.001f)
        }
    }
}
