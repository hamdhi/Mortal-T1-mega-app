package lk.mortal.tv

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DvrClockTest {
    private val zone = TimeZone.getTimeZone("Asia/Colombo")
    private fun time(day: Int, hour: Int, minute: Int) = Calendar.getInstance(zone).apply { clear(); set(2026, Calendar.NOVEMBER, day, hour, minute) }.timeInMillis
    @Test fun thirtyMinutesIsManualAndDoesNotClampUnavailableHistory() {
        assertEquals(120000L, DvrClock.rewindPosition(1920000L)); assertNull(DvrClock.rewindPosition(1799999L))
    }
    @Test fun handlesNoonAndMidnight() {
        assertEquals(time(1, 12, 0), DvrClock.mostRecentTime(12, 0, true, time(1, 12, 10), zone))
        assertEquals(time(1, 0, 0), DvrClock.mostRecentTime(12, 0, false, time(1, 0, 10), zone))
    }
    @Test fun lateNightSelectionCrossesMidnight() {
        assertEquals(time(1, 23, 50), DvrClock.mostRecentTime(11, 50, true, time(2, 0, 10), zone))
    }
    @Test fun checksExactWindowBounds() {
        assertEquals(0L, DvrClock.positionAtTime(1000, 2000, 1000)); assertEquals(2000L, DvrClock.positionAtTime(1000, 2000, 3000))
        assertNull(DvrClock.positionAtTime(1000, 2000, 999)); assertNull(DvrClock.positionAtTime(1000, 2000, 3001))
    }
}
