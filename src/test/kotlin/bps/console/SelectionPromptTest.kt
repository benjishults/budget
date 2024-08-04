package bps.console

import bps.console.inputs.SelectionPrompt
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class SelectionPromptTest : FreeSpec() {

    init {
        val outputs: MutableList<String> = mutableListOf()
        val outPrinter = OutPrinter { outputs.add(it) }
        val inputs: MutableList<String> = mutableListOf()
        val inputReader = InputReader { inputs.removeFirst() }
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
