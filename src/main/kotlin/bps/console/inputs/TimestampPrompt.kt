package bps.console.inputs

import bps.budget.WithIo
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class TimestampPrompt(
    basicPrompt: String,
    timeZone: TimeZone,
    clock: Clock = Clock.System,
    inputReader: InputReader = DefaultInputReader,
    outPrinter: OutPrinter = DefaultOutPrinter,
    now: LocalDateTime = clock.now().toLocalDateTime(timeZone),
) : SimplePromptWithDefault<LocalDateTime>(
    basicPrompt = basicPrompt,
    defaultValue = now,
    inputReader = inputReader,
    outPrinter = outPrinter,
    transformer = { acceptDefault: String ->
        when (acceptDefault) {
            "Y", "y", "" -> {
                now
            }
            else -> {
                CompositePrompt(
                    listOf(
                        SimplePromptWithDefault(
                            "         year [${now.year}]: ",
                            now.year,
                            inputReader,
                            outPrinter,
                        ) { it.toInt() },
                        SimplePromptWithDefault(
                            "   month (1-12) [${now.month.value}]: ",
                            now.month.value,
                            inputReader,
                            outPrinter,
                        ) { it.toInt() },
                        SimplePromptWithDefault(
                            "   day of month [${now.dayOfMonth}]: ",
                            now.dayOfMonth,
                            inputReader,
                            outPrinter,
                        ) { it.toInt() },
                        SimplePromptWithDefault(
                            "hour (24-clock) [${now.hour}]: ",
                            now.hour,
                            inputReader,
                            outPrinter,
                        ) { it.toInt() },
                        SimplePromptWithDefault(
                            " minute of hour [${now.minute}]: ",
                            now.minute,
                            inputReader,
                            outPrinter,
                        ) { it.toInt() },
                        SimplePromptWithDefault(
                            "         second [${now.second}]: ",
                            now.second,
                            inputReader,
                            outPrinter,
                        ) { it.toInt() },
                    ),
                ) { entries: List<*> ->
                    LocalDateTime.parse(
                        String.format(
                            "%04d-%02d-%02dT%02d:%02d:%02d",
                            entries[0],
                            entries[1],
                            entries[2],
                            entries[3],
                            entries[4],
                            entries[5],
                        ),
                    )
                }
                    .getResult()
            }
        }
    },
)

fun WithIo.getTimestampFromUser(
    prompt: String = "Use current time [Y]? ",
    timeZone: TimeZone,
    clock: Clock,
) = TimestampPrompt(
    prompt,
    timeZone,
    clock,
    inputReader,
    outPrinter,
)
    .getResult()
    .toInstant(timeZone)


//fun LocalDateTime.toInstantForTimeZone(timeZone: TimeZone): Instant =
//    ZonedDateTime
//        .of(this, timeZone.toZoneId())
//        .toInstant()
