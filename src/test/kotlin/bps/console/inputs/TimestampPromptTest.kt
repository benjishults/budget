package bps.console.inputs

import bps.console.SimpleConsoleIoTestFixture
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

class TimestampPromptTest : FreeSpec(),
    SimpleConsoleIoTestFixture {

    override val outputs: MutableList<String> = mutableListOf()
    override val inputs: MutableList<String> = mutableListOf()

    init {
        clearInputsAndOutputsBeforeEach()
        var now: LocalDateTime = LocalDateTime.parse("2024-08-09T00:00:00")

        val clock = object : Clock {
            var secondCount = 0
            override fun now(): Instant =
                Instant.parse(String.format("2024-08-09T00:00:%02dZ", secondCount++))
        }
        var subject = TimestampPrompt(
            "Use current time [Y]? ",
            TimeZone.of("America/Chicago"),
            clock,
            inputReader,
            outPrinter,
            now,
        )
        "run prompt accepting default in America/Chicago" {
            inputs.add("")
            subject.getResult() shouldBe now
            outputs shouldContainExactly listOf("Use current time [Y]? ")
        }
        "run prompt with inputs America/Chicago" {
            inputs.addAll(listOf("n", "2024", "8", "9", "0", "0", "0"))
            subject.getResult() shouldBe now
            outputs shouldContainExactly listOf(
                "Use current time [Y]? ",
                "         year [2024]: ",
                "   month (1-12) [8]: ",
                "   day of month [9]: ",
                "hour (24-clock) [0]: ",
                " minute of hour [0]: ",
                "         second [0]: ",
            )
        }
        subject = TimestampPrompt(
            "Use current time [Y]? ",
            TimeZone.of("America/Los_Angeles"),
            clock,
            inputReader,
            outPrinter,
            now,
        )
        "run prompt accepting default in America/Los_Angeles" {
            inputs.add("")
            subject.getResult() shouldBe now
            outputs shouldContainExactly listOf("Use current time [Y]? ")
        }
        "run prompt with inputs in America/Los_Angeles" {
            inputs.addAll(listOf("n", "2024", "8", "9", "0", "0", "0"))
            subject.getResult() shouldBe now
            outputs shouldContainExactly listOf(
                "Use current time [Y]? ",
                "         year [2024]: ",
                "   month (1-12) [8]: ",
                "   day of month [9]: ",
                "hour (24-clock) [0]: ",
                " minute of hour [0]: ",
                "         second [0]: ",
            )
        }
    }
}
