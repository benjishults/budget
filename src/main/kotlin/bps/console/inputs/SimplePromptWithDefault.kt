package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

open class SimplePromptWithDefault<T>(
    override val basicPrompt: String,
    val defaultValue: String,
    override val inputReader: InputReader = DefaultInputReader,
    override val outPrinter: OutPrinter = DefaultOutPrinter,
    override val validate: (String) -> Boolean = { true },
    @Suppress("UNCHECKED_CAST")
    override val transformer: (String) -> T = { it as T },
) : SimplePrompt<T> {

    override fun actionOnInvalid(input: String): T =
        if (input.isBlank())
            transformer(defaultValue)
        else
            super.actionOnInvalid(input)

//    override fun readInput(): String =
//        super.readInput()
//            .let {
//                it.ifBlank { defaultValue }
//            }

    override fun outputPrompt() =
        outPrinter("$basicPrompt [$defaultValue] ")

}
