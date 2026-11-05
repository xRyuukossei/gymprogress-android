package br.com.ryuukossei.gymprogress

import br.com.ryuukossei.gymprogress.ui.common.*
import org.junit.Assert.*
import org.junit.Test

class FormattersTest {
    @Test fun completedTrainingRequiresThirtyMinutesAndAnEnd() {
        assertFalse(isCompletedSession(0, null))
        assertFalse(isCompletedSession(0, COMPLETED_MIN_DURATION_MS - 1))
        assertTrue(isCompletedSession(0, COMPLETED_MIN_DURATION_MS))
    }
    @Test fun searchIgnoresAccentsCaseAndSurroundingSpaces() {
        assertEquals("gluteos", normalizeForSearch("  GLÚTEOS  "))
    }
    @Test fun durationNeverDisplaysNegativeMinutes() {
        assertEquals("0min", formatDuration(60000, 0))
        assertEquals("1h 5min", formatDuration(0, 65 * 60000L))
    }
}
