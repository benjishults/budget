package bps.kotlin

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

interface WithMockClock {

    class TickingClock(
        startTime: Instant = Instant.parse("2024-08-09T00:00:00.500Z"),
        val duration: Duration = Duration.parse("PT1S"),
    ) : Clock {

        var currentInstant: Instant = startTime
            private set

        override fun now(): Instant {
            return currentInstant
                .also {
                    currentInstant += duration
                }
        }
    }


    /**
     * Limitations:
     * 1. The [Clock] produced is not thread safe.
     * 2. There will be an arithmetic overflow if [Clock.now] is called [Int.MAX_VALUE] times.
     * @return a [Clock] that will return a monotonic sequence of [Instant]s each time [Clock.now] is called.  Each call
     * will produce an [Instant] one second later that the previous.
     * @param startTime determines the first [Instant] to be returned by [Clock.now]
     */
    fun produceSecondTickingClock(startTime: Instant = Instant.parse("2024-08-09T00:00:00.500Z")) =
        TickingClock(startTime)

    /**
     * Limitations:
     * 1. The [Clock] produced is not thread safe.
     * 2. There will be an arithmetic overflow if [Clock.now] is called [Int.MAX_VALUE] times.
     * @return a [Clock] that will return a monotonic sequence of [Instant]s each time [Clock.now] is called.  Each call
     * will produce an [Instant] one day later that the previous.
     * @param startTime determines the first [Instant] to be returned by [Clock.now]
     */
    fun produceDayTickingClock(startTime: Instant = Instant.parse("2024-08-09T00:00:00.500Z")) =
        TickingClock(startTime, Duration.parse("P1D"))

}
