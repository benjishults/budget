package bps.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.ConcurrentHashMap

sealed interface Interval<T : Comparable<T>> {
    val start: T
    val unit: DateTimeUnit
}

sealed interface LocalInterval : Interval<LocalDateTime>

/**
 * A [NaturalInterval] is an [Interval] that starts at the natural first moment of the [Interval].
 * For example, the first moment of a month.
 */
sealed interface NaturalInterval<T : Comparable<T>> : Interval<T>

/**
 * An [PeriodInterval] is a specific period of time.  It has a start-date and a period.
 */
sealed interface PeriodInterval<T : Comparable<T>> : Interval<T>

sealed interface NaturalLocalInterval : LocalInterval, NaturalInterval<LocalDateTime>

data class NaturalMonthLocalInterval(
    override val start: LocalDateTime,
) : PeriodInterval<LocalDateTime>, NaturalLocalInterval {
    override val unit: DateTimeUnit.MonthBased = DateTimeUnit.MonthBased(1)

    init {
        require(
//            (start.month.value - 1) % unit.months == 0 &&
            start.dayOfMonth == 1 &&
                    start.hour == 0 &&
                    start.minute == 0 &&
                    start.second == 0 &&
                    start.nanosecond == 0,
        )
    }
}

private val monthYearToNaturalMonthInterval: ConcurrentHashMap<Pair<Int, Int>, NaturalMonthLocalInterval> =
    ConcurrentHashMap()

data class NaturalWeekLocalInterval(
    override val start: LocalDateTime,
) : PeriodInterval<LocalDateTime>, NaturalLocalInterval {
    override val unit: DateTimeUnit.DayBased = DateTimeUnit.DayBased(7)

//    constructor(
//        start: LocalDateTime,
//        weeks: Int = 1,
//    ) : this(start, DateTimeUnit.DayBased(weeks))

    init {
        require(
//            unit.days % 7 == 0 &&
            start.dayOfWeek == DayOfWeek.SUNDAY &&
                    start.hour == 0 &&
                    start.minute == 0 &&
                    start.second == 0 &&
                    start.nanosecond == 0,
        )
    }
}

/**
 * @return the [LocalDateTime] at the start of the receiver's month.
 */
fun LocalDateTime.atStartOfMonth(): LocalDateTime =
    LocalDateTime(year, month, 1, 0, 0, 0)

/**
 * @return the [LocalDateTime] at the start of the receiver's month in the given [TimeZone].
 */
fun Instant.atStartOfMonth(timeZone: TimeZone): LocalDateTime =
    toLocalDateTime(timeZone)
        .atStartOfMonth()

/**
 * @return the [LocalDateTime] at the start of the receiver's month in the given [TimeZone].
 */
fun java.time.Instant.atStartOfMonth(timeZone: TimeZone): LocalDateTime =
    toKotlinInstant()
        .toLocalDateTime(timeZone)
        .atStartOfMonth()

fun LocalDateTime.naturalMonthInterval(): NaturalMonthLocalInterval =
    monthYearToNaturalMonthInterval.computeIfAbsent(year to monthNumber) {
        NaturalMonthLocalInterval(LocalDateTime(year, monthNumber, 1, 0, 0, 0, 0))
    }
