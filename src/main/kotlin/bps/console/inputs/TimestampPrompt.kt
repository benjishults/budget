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
    now: ZonedDateTime = ZonedDateTime.now(),
) : SimplePromptWithDefault<Instant>(
    basicPrompt,
    "now",
    inputReader,
    outPrinter,
    transformer = {
        when (it) {
            "now", "" -> {
                now.toInstant()
            }
            else -> {
                LocalDateTime.now()
                RecursivePrompt<Instant>(
                    listOf(
                        SimplePromptWithDefault("           year: ", now.year.toString(), inputReader, outPrinter),
                        SimplePromptWithDefault(
                            "          month: ",
                            now.month.value.toString(),
                            inputReader,
                            outPrinter,
                        ),
                        SimplePromptWithDefault(
                            "   day of month: ",
                            now.dayOfMonth.toString(),
                            inputReader,
                            outPrinter,
                        ),
                        SimplePromptWithDefault("hour (24-clock): ", now.hour.toString(), inputReader, outPrinter),
                        SimplePromptWithDefault(" minute of hour: ", now.minute.toString(), inputReader, outPrinter),
                        SimplePromptWithDefault<String>(
                            "         second: ",
                            now.second.toString(),
                            inputReader,
                            outPrinter,
                        ),
                    ),
                ) { entries ->
                    LocalDateTime.parse(
                        String.format(
                            "${entries[0]}-%02d-%02dT%02d:%02d:%02d",
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
