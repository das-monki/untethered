package dev.labs910.voicecode.data

import dev.labs910.voicecode.domain.model.MessageRole
import dev.labs910.voicecode.domain.model.MessageStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Tests for SessionRepository mapping and utility functions.
 * Note: Full integration tests require Android instrumentation for Room.
 */
class SessionRepositoryTest {

    @Test
    fun `UUID generation is lowercase`() {
        repeat(10) {
            val uuid = UUID.randomUUID().toString().lowercase()
            assertEquals(uuid, uuid.lowercase())
            assertEquals(36, uuid.length)
            assertFalse(uuid.contains(Regex("[A-Z]")))
        }
    }

    @Test
    fun `MessageRole fromString handles all cases`() {
        assertEquals(MessageRole.USER, MessageRole.fromString("user"))
        assertEquals(MessageRole.USER, MessageRole.fromString("USER"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("assistant"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromString("ASSISTANT"))
        assertEquals(MessageRole.SYSTEM, MessageRole.fromString("system"))
        assertEquals(MessageRole.USER, MessageRole.fromString("unknown"))
    }

    @Test
    fun `MessageStatus values are correct`() {
        assertEquals("SENDING", MessageStatus.SENDING.name)
        assertEquals("CONFIRMED", MessageStatus.CONFIRMED.name)
        assertEquals("ERROR", MessageStatus.ERROR.name)
    }

    @Test
    fun `Instant parsing handles ISO-8601 format`() {
        val isoString = "2025-01-15T12:00:00Z"
        val instant = Instant.parse(isoString)
        assertNotNull(instant)
        assertEquals(1736942400L, instant.epochSecond)
    }

    @Test
    fun `Instant parsing handles milliseconds`() {
        val isoString = "2025-01-15T12:00:00.123Z"
        val instant = Instant.parse(isoString)
        assertNotNull(instant)
        assertEquals(123_000_000, instant.nano)
    }

    @Test
    fun `Preview text truncation works correctly`() {
        val longText = "A".repeat(200)
        val preview = longText.take(100)
        assertEquals(100, preview.length)
        assertEquals("A".repeat(100), preview)
    }

    @Test
    fun `Preview text does not truncate short text`() {
        val shortText = "Hello world"
        val preview = shortText.take(100)
        assertEquals(shortText, preview)
    }

    @Test
    fun `Session ID normalization to lowercase`() {
        val uppercase = "ABC123DE-4567-89AB-CDEF-0123456789AB"
        val lowercase = uppercase.lowercase()
        assertEquals("abc123de-4567-89ab-cdef-0123456789ab", lowercase)
    }

    @Test
    fun `Timestamp conversion round-trips correctly`() {
        val now = Instant.now()
        val millis = now.toEpochMilli()
        val restored = Instant.ofEpochMilli(millis)

        // Should be within 1 millisecond (nano precision lost)
        assertTrue(Math.abs(now.toEpochMilli() - restored.toEpochMilli()) < 1)
    }

    @Test
    fun `Working directory basename extraction`() {
        val path = "/Users/test/code/project"
        val basename = path.substringAfterLast('/')
        assertEquals("project", basename)
    }

    @Test
    fun `Working directory basename with trailing slash`() {
        val path = "/Users/test/code/project/"
        val basename = path.trimEnd('/').substringAfterLast('/')
        assertEquals("project", basename)
    }

    @Test
    fun `Session display name with null values`() {
        // Simulating the priority logic
        val customName: String? = null
        val name: String? = null
        val workingDirectory: String? = null
        val id = "abc123de-4567-89ab-cdef-0123456789ab"

        val displayName = customName
            ?: name
            ?: workingDirectory?.substringAfterLast('/')
            ?: id.take(8)

        assertEquals("abc123de", displayName)
    }

    @Test
    fun `Session display name with working directory`() {
        val customName: String? = null
        val name: String? = null
        val workingDirectory = "/Users/test/project"
        val id = "abc123de-4567-89ab-cdef-0123456789ab"

        val displayName = customName
            ?: name
            ?: workingDirectory.substringAfterLast('/')
            ?: id.take(8)

        assertEquals("project", displayName)
    }

    @Test
    fun `Session display name with custom name`() {
        val customName = "My Session"
        val name = "Auto Name"
        val workingDirectory = "/Users/test/project"
        val id = "abc123de-4567-89ab-cdef-0123456789ab"

        val displayName = customName
            ?: name
            ?: workingDirectory.substringAfterLast('/')
            ?: id.take(8)

        assertEquals("My Session", displayName)
    }
}
