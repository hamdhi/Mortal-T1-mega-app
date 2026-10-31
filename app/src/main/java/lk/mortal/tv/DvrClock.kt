package lk.mortal.tv

import java.util.Calendar
import java.util.TimeZone

object DvrClock {
    const val THIRTY_MINUTES_MS = 30 * 60 * 1000L
    fun rewindPosition(positionMs: Long): Long? = (positionMs - THIRTY_MINUTES_MS).takeIf { it >= 0 }
    fun positionAtTime(windowStartMs: Long, durationMs: Long, epochMs: Long): Long? {
        if (durationMs <= 0) return null
        return (epochMs - windowStartMs).takeIf { it in 0..durationMs }
    }
    fun mostRecentTime(hour12: Int, minute: Int, pm: Boolean, windowEndMs: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        require(hour12 in 1..12 && minute in 0..59)
        val chosen = Calendar.getInstance(zone).apply {
            timeInMillis = windowEndMs
            set(Calendar.HOUR_OF_DAY, hour12 % 12 + if (pm) 12 else 0)
            set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (chosen.timeInMillis > windowEndMs) chosen.add(Calendar.DAY_OF_MONTH, -1)
        return chosen.timeInMillis
    }
}
