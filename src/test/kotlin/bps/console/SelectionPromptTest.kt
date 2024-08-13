package bps.console

import bps.console.inputs.SelectionPrompt
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class SelectionPromptTest : FreeSpec(),
    SimpleConsoleIoTestFixture {

    override val inputs: MutableList<String> = mutableListOf()
    override val outputs: MutableList<String> = mutableListOf()

    init {
        clearInputsAndOutputsBeforeEach()
        "basic" {
            inputs.add("3")
            val selectionPrompt = SelectionPrompt(
                header = "select",
                inputReader = inputReader,
                outPrinter = outPrinter,
                options = listOf(1, 2, 3, 4, 5),
            )
            selectionPrompt.getResult() shouldBe 3
            outputs shouldContainExactly listOf(
                """
                |select
                | 1. 1
                | 2. 2
                | 3. 3
                | 4. 4
                | 5. 5
                |Enter selection: """.trimMargin(),
            )
            inputs shouldHaveSize 0
        }
    }

}
