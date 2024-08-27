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
    val additionalValidation: (String) -> Boolean = { true },
    @Suppress("UNCHECKED_CAST")
    override val transformer: (String) -> T =
        {
            it as T
        },
) : SimplePrompt<T> {

    final override val validate: (String) -> Boolean =
        {
            it.isNotBlank()
        }

    override fun actionOnInvalid(input: String): T =
        if (input.isBlank())
            defaultValue
        else if (additionalValidation(input))
            transformer(input)
        else
            super.actionOnInvalid(input)

}
