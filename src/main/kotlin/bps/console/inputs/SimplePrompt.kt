package bps.console.inputs

import bps.budget.toCurrencyAmountOrNull
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import org.apache.commons.validator.routines.EmailValidator
import java.math.BigDecimal

interface SimplePrompt<T : Any> : Prompt<T> {
    // TODO specify that this shouldn't contain ending spaces or punctuation and make it so
    val basicPrompt: String
    val inputReader: InputReader
    val outPrinter: OutPrinter

    /**
     * returns `true` if the input is acceptable
     */
    val validator: SimpleEntryValidator

    /**
     * transforms valid input into an instance of [T].
     */
    val transformer: (String) -> T

    fun actionOnInvalid(input: String, message: String): T? {
        outPrinter.important(message)
        return if (SimplePrompt(
                basicPrompt = "Try again? [Y/n]: ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                validator = AcceptAnythingSimpleEntryValidator,
                transformer = { it != "n" },
            )
                .getResult()!!
        )
            this.getResult()
        else
            null
    }

    override fun getResult(): T? {
        outPrinter(basicPrompt)
        return inputReader()
            .let { input: String ->
                if (validator(input))
                    transformer(input)
                else {
                    actionOnInvalid(input, validator.errorMessage)
                }
            }
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        operator fun <T : Any> invoke(
            basicPrompt: String,
            inputReader: InputReader = DefaultInputReader,
            outPrinter: OutPrinter = DefaultOutPrinter,
            validator: SimpleEntryValidator = NonBlankSimpleEntryValidator,
            transformer: (String) -> T = {
                it as T
            },
        ) =
            object : SimplePrompt<T> {
                override val basicPrompt: String = basicPrompt
                override val inputReader: InputReader = inputReader
                override val outPrinter: OutPrinter = outPrinter
                override val transformer: (String) -> T = transformer
                override val validator: SimpleEntryValidator = validator
            }
    }
}

interface SimpleEntryValidator : (String) -> Boolean {
    val errorMessage: String
}

data object NonBlankSimpleEntryValidator : SimpleEntryValidator {
    override fun invoke(entry: String): Boolean =
        entry.isNotBlank()

    override val errorMessage: String = "Entry must not be blank."

}

data object AcceptAnythingSimpleEntryValidator : SimpleEntryValidator {
    override fun invoke(entry: String): Boolean = true

    override val errorMessage: String = ""
}

data object EmailSimpleEntryValidator : SimpleEntryValidator {
    override fun invoke(entry: String): Boolean = EmailValidator.getInstance().isValid(entry)

    override val errorMessage: String = "Must enter a valid email address."
}

data object PositiveSimpleEntryValidator : SimpleEntryValidator {
    override fun invoke(input: String): Boolean =
        input
            .toCurrencyAmountOrNull()
            ?.let {
                it > BigDecimal.ZERO
            }
            ?: false

    override val errorMessage: String = "Amount must be positive"
}

data object NonNegativeSimpleEntryValidator : SimpleEntryValidator {
    override fun invoke(input: String): Boolean =
        input
            .toCurrencyAmountOrNull()
            ?.let {
                it >= BigDecimal.ZERO.setScale(2)
            }
            ?: false

    override val errorMessage: String = "Amount must be non-negative"
}

data class InRangeInclusiveSimpleEntryValidator(
    val min: BigDecimal,
    val max: BigDecimal,
) : SimpleEntryValidator {
    override fun invoke(input: String): Boolean =
        input
            .toCurrencyAmountOrNull()
            ?.let {
                it in min..max
            }
            ?: false

    override val errorMessage: String = "Amount must be between $min and $max"
}
