package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.TimeZone

class TimestampPrompt(
    basicPrompt: String,
    timeZone: TimeZone,
    inputReader: InputReader = DefaultInputReader,
    outPrinter: OutPrinter = DefaultOutPrinter,
    now: ZonedDateTime = ZonedDateTime.now(timeZone.toZoneId()),
) : SimplePromptWithDefault<Instant>(
    basicPrompt,
    "Y",
    inputReader,
    outPrinter,
    transformer = { acceptDefault: String ->
        when (acceptDefault) {
            "Y", "y", "" -> {
                now.toInstant()
            }
            else -> {
                RecursivePrompt(
                    listOf(
                        SimplePromptWithDefault(
                            "         year: ",
                            now.year.toString(),
                            inputReader,
                            outPrinter,
                        ) { it.toInt() },
                        SimplePromptWithDefault(
                            "   month (1-12): ",
                            now.month.value.toString(),
                            inputReader,
                            outPrinter,
                        ) { it.toInt() },
                        SimplePromptWithDefault(
                            "   day of month: ",
                            now.dayOfMonth.toString(),
                            inputReader,
                            outPrinter,
                        ) { it.toInt() },
                        SimplePromptWithDefault(
                            "hour (24-clock): ",
                            now.hour.toString(),
                            inputReader,
                            outPrinter,
                        ) { it.toInt() },
                        SimplePromptWithDefault(
                            " minute of hour: ",
                            now.minute.toString(),
                            inputReader,
                            outPrinter,
                        ) { it.toInt() },
                        SimplePromptWithDefault(
                            "         second: ",
                            now.second.toString(),
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
                        .toInstantForTimeZone(timeZone)
                }
                    .getResult()
            }
        }
    },
)

fun LocalDateTime.toInstantForTimeZone(timeZone: TimeZone): Instant =
    ZonedDateTime
        .of(this, timeZone.toZoneId())
        .toInstant()
