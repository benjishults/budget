package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

interface SimplePrompt<T> : Prompt<T> {
    // TODO specify that this shouldn't contain ending spaces or punctuation and make it so
    val basicPrompt: String
    val inputReader: InputReader
    val outPrinter: OutPrinter

    /**
     * returns `true` if the input is acceptable
     */
    val validate: (String) -> Boolean
        get() = { it.isNotBlank() }

    /**
     * transforms valid input into an instance of [T].
     */
    val transformer: (String) -> T

    fun outputPrompt() =
        outPrinter(basicPrompt)

    fun actionOnInvalid(input: String): T {
        outPrinter("Invalid input: '$input\n")
        return this.getResult()
    }

    override fun getResult(): T {
        outputPrompt()
        return transformer(
            inputReader()
                .let { input: String ->
                    if (validate(input))
                        input
                    else {
                        return actionOnInvalid(input)
                    }
                },
        )
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        operator fun <T> invoke(
            prompt: String,
            inputReader: InputReader = DefaultInputReader,
            outPrinter: OutPrinter = DefaultOutPrinter,
            validate: (String) -> Boolean = { it.isNotBlank() },
            transformer: (String) -> T = { it as T },
        ) =
            object : SimplePrompt<T> {
                override val basicPrompt: String = prompt
                override val inputReader: InputReader = inputReader
                override val outPrinter: OutPrinter = outPrinter
                override val transformer: (String) -> T = transformer
                override val validate: (String) -> Boolean = validate
            }
    }
}

