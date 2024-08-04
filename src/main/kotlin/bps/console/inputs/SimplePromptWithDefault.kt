package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

@Suppress("UNCHECKED_CAST")
open class SimplePromptWithDefault<T>(
    override val basicPrompt: String,
    val defaultValue: String,
    override val inputReader: InputReader = DefaultInputReader,
    override val outPrinter: OutPrinter = DefaultOutPrinter,
    override val transformer: (String?) -> T = { it as T },
) : SimplePrompt<T> {
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
