package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

interface SimplePrompt<T> : Prompt<T> {
    val basicPrompt: String
    val inputReader: InputReader
    val outPrinter: OutPrinter
    val validate: (String) -> Boolean
        get() = { it.isNotBlank() }
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
            inputReader().let {
                if (validate(it))
                    it
                else {
                    return actionOnInvalid(it)
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

