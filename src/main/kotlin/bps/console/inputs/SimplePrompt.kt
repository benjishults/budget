package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

interface SimplePrompt<T> : Prompt<T> {
    val basicPrompt: String
    val inputReader: InputReader
    val outPrinter: OutPrinter
    val transformer: (String?) -> T

    fun outputPrompt() =
        outPrinter(basicPrompt)

    fun readInput(): String? =
        inputReader()

    override fun getResult(): T {
        outputPrompt()
        return transformer(readInput())
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        operator fun <T> invoke(
            prompt: String,
            inputReader: InputReader = DefaultInputReader,
            outPrinter: OutPrinter = DefaultOutPrinter,
            transformer: (String?) -> T = { it!! as T },
        ) =
            object : SimplePrompt<T> {
                override val basicPrompt: String = prompt
                override val inputReader: InputReader = inputReader
                override val outPrinter: OutPrinter = outPrinter
                override val transformer: (String?) -> T = transformer
            }
    }
}

