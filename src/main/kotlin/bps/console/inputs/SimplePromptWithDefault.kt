package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

open class SimplePromptWithDefault<T>(
    override val basicPrompt: String,
    val defaultValue: T,
    override val inputReader: InputReader = DefaultInputReader,
    override val outPrinter: OutPrinter = DefaultOutPrinter,
    final override val validate: (String) -> Boolean =
        {
            it.isNotBlank()
        },
    @Suppress("UNCHECKED_CAST")
    override val transformer: (String) -> T =
        {
            it as T
        },
) : SimplePrompt<T> {

    override fun actionOnInvalid(input: String): T =
        if (input.isBlank())
            defaultValue
        else
            transformer(input)

}
