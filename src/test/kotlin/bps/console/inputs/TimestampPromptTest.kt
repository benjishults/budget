package bps.console.inputs

import bps.console.io.InputReader
import bps.console.io.OutPrinter
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

class TimestampPromptTest : FreeSpec() {

    init {
        val outputs: MutableList<String> = mutableListOf()
        val outPrinter = OutPrinter {
            outputs.add(it)
        }
        val inputs: MutableList<String> = mutableListOf()
        val inputReader = InputReader {
            inputs.removeFirst()
        }
        beforeEach {
            inputs.clear()
            outputs.clear()
        }

        var now: ZonedDateTime =
            ZonedDateTime.of(LocalDateTime.parse("2024-08-09T00:00:00"), ZoneId.of("America/Chicago"))

        var subject = TimestampPrompt(
            "Use current time (Y/n)? ",
            TimeZone.getTimeZone("America/Chicago"),
            inputReader,
            outPrinter,
            now,
        )
        "run prompt accepting default in America/Chicago" {
            inputs.add("")
            subject.getResult() shouldBe now.toInstant()
            outputs shouldContainExactly listOf("Use current time (Y/n)?  [Y] ")
        }
        "run prompt with inputs America/Chicago" {
            inputs.addAll(listOf("n", "2024", "8", "9", "0", "0", "0"))
            subject.getResult() shouldBe now.toInstant()
            outputs shouldContainExactly listOf(
                "Use current time (Y/n)?  [Y] ",
                "         year:  [2024] ",
                "   month (1-12):  [8] ",
                "   day of month:  [9] ",
                "hour (24-clock):  [0] ",
                " minute of hour:  [0] ",
                "         second:  [0] ",
            )
        }
        now =
            ZonedDateTime.of(LocalDateTime.parse("2024-08-09T00:00:00"), ZoneId.of("America/Los_Angeles"))
        subject = TimestampPrompt(
            "Use current time (Y/n)? ",
            TimeZone.getTimeZone("America/Los_Angeles"),
            inputReader,
            outPrinter,
            now,
        )
        "run prompt accepting default in America/Los_Angeles" {
            inputs.add("")
            subject.getResult() shouldBe now.toInstant()
            outputs shouldContainExactly listOf("Use current time (Y/n)?  [Y] ")
        }
        "run prompt with inputs in America/Los_Angeles" {
            inputs.addAll(listOf("n", "2024", "8", "9", "0", "0", "0"))
            subject.getResult() shouldBe now.toInstant()
            outputs shouldContainExactly listOf(
                "Use current time (Y/n)?  [Y] ",
                "         year:  [2024] ",
                "   month (1-12):  [8] ",
                "   day of month:  [9] ",
                "hour (24-clock):  [0] ",
                " minute of hour:  [0] ",
                "         second:  [0] ",
            )
        }
    }
}
