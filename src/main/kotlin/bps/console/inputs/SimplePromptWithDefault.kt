package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

/**
 * @param additionalValidation if [validator] fails on the user's input and that input was not blank, then this will
 * be called prior to calling [transformer].
 * @param transformer Called on entry after it has gone through [validator] or [additionalValidation] if that fails.
 * The default implementation simply casts to [T].
 * @param T the type of the result.
 */
open class SimplePromptWithDefault<T : Any>(
    override val basicPrompt: String,
    val defaultValue: T,
    override val inputReader: InputReader = DefaultInputReader,
    override val outPrinter: OutPrinter = DefaultOutPrinter,
    /**
     * If [validator] fails on the user's input and that input was not blank, then this will be called prior to
     * calling [transformer]
     */
    val additionalValidation: StringValidator = AcceptAnythingStringValidator,
    /**
     * Called on entry after it has gone through [validator] or [additionalValidation] if that fails.
     *
     * The default implementation simply casts to [T]
     */
    @Suppress("UNCHECKED_CAST")
    override val transformer: (String) -> T = { it as T },
) : SimplePrompt<T> {

    /**
     * Fails if the input is blank.
     */
    final override val validator: StringValidator = NonBlankStringValidator

    /**
     * This implementation:
     * 1. returns [defaultValue] if the input was blank,
     * 2. returns the result of [transformer] if [additionalValidation] passes,
     * 3. otherwise, prints [additionalValidation]'s [StringValidator.errorMessage] as important and calls [SimplePrompt.actionOnInvalid]
     *    which gives the user a chance to try again or returns `null`.
     */
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
