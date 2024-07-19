package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

interface Prompt {
    val basicPrompt: String
    val inputReader: InputReader
    val outPrinter: OutPrinter

    fun outputPrompt() =
        outPrinter(basicPrompt)

    fun readInput(): String? =
        inputReader()

    fun getResult(): String? {
        outputPrompt()
        return readInput()
    }

    companion object {
        operator fun invoke(
            prompt: String,
            inputReader: InputReader,
            outPrinter: OutPrinter,
        ) =
            object : Prompt {
                override val basicPrompt: String = prompt
                override val inputReader: InputReader = inputReader
                override val outPrinter: OutPrinter = outPrinter
            }
    }
}

data class PromptWithDefault(
    override val basicPrompt: String,
    val defaultValue: String,
    override val inputReader: InputReader = DefaultInputReader,
    override val outPrinter: OutPrinter = DefaultOutPrinter,
) : Prompt {
    override fun readInput(): String =
        super.readInput()
            .let {
                if (it.isNullOrBlank())
                    defaultValue
                else
                    it
            }

    override fun outputPrompt() =
        outPrinter("$basicPrompt [$defaultValue] ")

}

fun <T> collectInputs(
    prompts: List<Prompt>,
    inputsConverter: (List<String>) -> T,
): T =
    inputsConverter(
        prompts.map { prompt: Prompt ->
            prompt.getResult()!!
        },
    )
