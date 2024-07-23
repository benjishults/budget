package bps.budget.ui

import bps.budget.model.CategoryAccount
import bps.console.inputs.RecursivePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

interface UiFunctions {
    fun createGeneralAccount(): CategoryAccount
}

class ConsoleUiFunctions(
    val inputReader: InputReader = DefaultInputReader,
    val outPrinter: OutPrinter = DefaultOutPrinter,
) : UiFunctions {

    override fun createGeneralAccount(): CategoryAccount =
        RecursivePrompt(
            listOf(
                SimplePromptWithDefault<String>(
                    """
            |Looks like this is your first time running Budget.
            |Enter the name for your "General" account""".trimMargin(),
                    "General",
                    inputReader,
                    outPrinter,
                ),
                SimplePromptWithDefault(
                    """Enter the description for your "General" account""".trimMargin(),
                    "Income is automatically deposited here and allowances are made from here.",
                    inputReader,
                    outPrinter,
                ),
            ),
        ) { CategoryAccount(it[0] as String, it[1] as String) }
            .getResult()

}
