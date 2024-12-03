package bps.console.inputs

import bps.budget.WithIo
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class TimestampPrompt(
    queryForNow: String,
    timeZone: TimeZone,
    clock: Clock = Clock.System,
    inputReader: InputReader = DefaultInputReader,
    outPrinter: OutPrinter = DefaultOutPrinter,
    now: LocalDateTime = clock.now().toLocalDateTime(timeZone),
) : SimplePromptWithDefault<LocalDateTime>(
    basicPrompt = queryForNow,
    defaultValue = now,
    inputReader = inputReader,
    outPrinter = outPrinter,
    transformer = { acceptDefault: String ->
        when (acceptDefault) {
            "Y", "y", "" -> {
                now
            }
            else -> {
                val year: Int =
                    SimplePromptWithDefault(
                        "         year [${now.year}]: ",
                        now.year,
                        inputReader,
                        outPrinter,
                    ) { it.toInt() }
                        .getResult()
                        ?: throw IllegalStateException("year")
                val month: Int =
                    SimplePromptWithDefault(
                        String.format("   month (1-12) [%2d]: ", now.month.value),
                        now.month.value,
                        inputReader,
                        outPrinter,
                    ) { it.toInt() }
                        .getResult()
                        ?: throw IllegalStateException("month")
                val day: Int =
                    SimplePromptWithDefault(
                        String.format("   day of month [%2d]: ", now.dayOfMonth),
                        now.dayOfMonth,
                        inputReader,
                        outPrinter,
                    ) { it.toInt() }
                        .getResult()
                        ?: throw IllegalStateException("day of month")
                val hour: Int =
                    SimplePromptWithDefault(
                        String.format("hour (24-clock) [%2d]: ", now.hour),
                        now.hour,
                        inputReader,
                        outPrinter,
                    ) { it.toInt() }
                        .getResult()
                        ?: throw IllegalStateException("hour")
                val minute: Int =
                    SimplePromptWithDefault(
                        String.format(" minute of hour [%2d]: ", now.minute),
                        now.minute,
                        inputReader,
                        outPrinter,
                    ) { it.toInt() }
                        .getResult()
                        ?: throw IllegalStateException("minute")
                val second: Int =
                    SimplePromptWithDefault(
                        String.format("         second [%2d]: ", now.second),
                        now.second,
                        inputReader,
                        outPrinter,
                    ) { it.toInt() }
                        .getResult()
                        ?: throw IllegalStateException("second")
                LocalDateTime.parse(
                    String.format(
                        "%04d-%02d-%02dT%02d:%02d:%02d",
                        year,
                        month,
                        day,
                        hour,
                        minute,
                        second,
                    ),
                )
            }
        }
    },
)

/**
 * Throws whatever exception is thrown by the [errorConverter] if the user gives up.
 * By default, this is a [TryAgainAtMostRecentMenuException].
 */
fun WithIo.getTimestampFromUser(
    queryForNow: String = "Use current time [Y]? ",
    timeZone: TimeZone,
    clock: Clock,
    errorConverter: (IllegalStateException) -> Nothing = {
        throw TryAgainAtMostRecentMenuException(
            "No ${it.message} entered.",
            it,
        )
    },
): Instant =
    try {
        TimestampPrompt(
            queryForNow,
            timeZone,
            clock,
            inputReader,
            outPrinter,
        )
            .getResult()!!
            .toInstant(timeZone)
    } catch (ex: IllegalStateException) {
        errorConverter(ex)
    }


//fun LocalDateTime.toInstantForTimeZone(timeZone: TimeZone): Instant =
//    ZonedDateTime
//        .of(this, timeZone.toZoneId())
//        .toInstant()
