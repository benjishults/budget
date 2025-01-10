package bps.kotlin

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

interface WithMockClock {

    /**
     * Limitations:
     * 1. The [Clock] produced is not thread safe.
     * 2. There will be an arithmetic overflow if [Clock.now] is called [Int.MAX_VALUE] times.
     * @return a [Clock] that will return a monotonic sequence of [Instant]s each time [Clock.now] is called.  Each call
     * will produce an [Instant] one second later that the previous.
     * @param startTime determines the first [Instant] to be returned by [Clock.now]
     */
    fun produceSecondTickingClock(startTime: Instant = Instant.parse("2024-08-09T00:00:00.500Z")) =
        object : Clock {
            var callCount: Int = 0
            val pattern: String = startTime.toString().let {
                val prefix = it.substringBeforeLast(':')
                val suffix = it.substringAfterLast('.')
                "$prefix:%02d.$suffix"
            }

            override fun now(): Instant =
                Instant.parse(String.format(pattern, callCount++))
        }

}
