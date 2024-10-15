package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

open class SimplePromptWithDefault<T : Any>(
    override val basicPrompt: String,
    val defaultValue: T,
    override val inputReader: InputReader = DefaultInputReader,
    override val outPrinter: OutPrinter = DefaultOutPrinter,
    val additionalValidation: SimpleEntryValidator = AcceptAnythingSimpleEntryValidator,
    /**
     * transforms entry after it has gone through [validator] and [additionalValidation]
     */
    @Suppress("UNCHECKED_CAST")
    override val transformer: (String) -> T =
        {
            it as T
        },
) : SimplePrompt<T> {

    final override val validator: SimpleEntryValidator = NonBlankSimpleEntryValidator

    override fun actionOnInvalid(input: String, message: String): T? =
        if (input.isBlank())
            defaultValue
        else if (additionalValidation(input))
            transformer(input)
        else {
            outPrinter.important(additionalValidation.errorMessage)
            super.actionOnInvalid(input, message)
        }

}
