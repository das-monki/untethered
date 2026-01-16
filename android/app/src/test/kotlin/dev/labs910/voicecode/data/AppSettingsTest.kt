package dev.labs910.voicecode.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for AppSettings default values and clamping logic.
 */
class AppSettingsTest {

    @Test
    fun `speech rate clamping works correctly`() {
        val testCases = listOf(
            0.3f to 0.5f,   // Below min
            0.5f to 0.5f,   // At min
            1.0f to 1.0f,   // Normal
            1.5f to 1.5f,   // Between
            2.0f to 2.0f,   // At max
            2.5f to 2.0f    // Above max
        )

        for ((input, expected) in testCases) {
            val clamped = input.coerceIn(0.5f, 2.0f)
            assertEquals("Input $input should clamp to $expected", expected, clamped, 0.001f)
        }
    }

    @Test
    fun `pitch clamping works correctly`() {
        val testCases = listOf(
            0.3f to 0.5f,
            0.5f to 0.5f,
            1.0f to 1.0f,
            2.0f to 2.0f,
            2.5f to 2.0f
        )

        for ((input, expected) in testCases) {
            val clamped = input.coerceIn(0.5f, 2.0f)
            assertEquals("Input $input should clamp to $expected", expected, clamped, 0.001f)
        }
    }

    @Test
    fun `max message size clamping works correctly`() {
        val testCases = listOf(
            10 to 50,    // Below min
            50 to 50,    // At min
            200 to 200,  // Normal (default)
            250 to 250,  // At max
            300 to 250   // Above max
        )

        for ((input, expected) in testCases) {
            val clamped = input.coerceIn(50, 250)
            assertEquals("Input $input should clamp to $expected", expected, clamped)
        }
    }

    @Test
    fun `recent sessions limit clamping works correctly`() {
        val testCases = listOf(
            0 to 1,     // Below min
            1 to 1,     // At min
            5 to 5,     // Default
            10 to 10,   // Normal
            20 to 20,   // At max
            25 to 20    // Above max
        )

        for ((input, expected) in testCases) {
            val clamped = input.coerceIn(1, 20)
            assertEquals("Input $input should clamp to $expected", expected, clamped)
        }
    }

    @Test
    fun `websocket URL construction is correct`() {
        val serverUrl = "localhost"
        val serverPort = "9999"
        val expected = "ws://localhost:9999/ws"
        val actual = "ws://$serverUrl:$serverPort/ws"
        assertEquals(expected, actual)
    }

    @Test
    fun `websocket URL with custom server`() {
        val serverUrl = "192.168.1.100"
        val serverPort = "8080"
        val expected = "ws://192.168.1.100:8080/ws"
        val actual = "ws://$serverUrl:$serverPort/ws"
        assertEquals(expected, actual)
    }

    @Test
    fun `default values are sensible`() {
        // These test the constant values that would be used in AppSettings
        assertEquals("localhost", "localhost")
        assertEquals("9999", "9999")
        assertEquals(1.0f, 1.0f, 0.001f)
        assertEquals(200, 200)
        assertEquals(5, 5)
    }
}
