package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TimestampPrompt(
    basicPrompt: String,
    inputReader: InputReader = DefaultInputReader,
    outPrinter: OutPrinter = DefaultOutPrinter,
    now: ZonedDateTime = ZonedDateTime.now(),
) : SimplePromptWithDefault<OffsetDateTime>(
    basicPrompt,
    "now",
    inputReader,
    outPrinter,
    {
        when (it) {
            "now" -> {
                OffsetDateTime.now()
            }
            else -> {
                RecursivePrompt<OffsetDateTime>(
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
                        .toOffsetDateTimeSystemDefault()
                }
                    .getResult()
            }
        }
    },
)

fun LocalDateTime.toOffsetDateTimeSystemDefault(): OffsetDateTime =
    OffsetDateTime.of(
        this,
        ZoneOffset
            .systemDefault()
            .rules
            .getOffset(this),
    )
