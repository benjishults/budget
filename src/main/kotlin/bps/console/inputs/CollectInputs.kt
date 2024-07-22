package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

interface Prompt<out T> {

    fun getResult(): T

}

interface SimplePrompt<T> : Prompt<T> {
    val basicPrompt: String
    val inputReader: InputReader
    val outPrinter: OutPrinter
    val transformer: (String?) -> T

    fun outputPrompt() =
        outPrinter(basicPrompt)

    fun readInput(): String? =
        inputReader()

    override fun getResult(): T {
        outputPrompt()
        return transformer(readInput())
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        operator fun <T> invoke(
            prompt: String,
            inputReader: InputReader = DefaultInputReader,
            outPrinter: OutPrinter = DefaultOutPrinter,
            transformer: (String?) -> T = { it as T },
        ) =
            object : SimplePrompt<T> {
                override val basicPrompt: String = prompt
                override val inputReader: InputReader = inputReader
                override val outPrinter: OutPrinter = outPrinter
                override val transformer: (String?) -> T = transformer
            }
    }
}

@Suppress("UNCHECKED_CAST")
data class SimplePromptWithDefault<T>(
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

interface RecursivePrompt<T> : Prompt<T> {
    val prompts: List<Prompt<*>>
    val transformer: (List<*>) -> T

    override fun getResult(): T =
        transformer(
            prompts.map { innerPrompt: Prompt<*> ->
                innerPrompt.getResult()
            },
        )

    companion object {
        operator fun <T> invoke(
            prompts: List<Prompt<*>>,
            transformer: (List<*>) -> T,
        ): RecursivePrompt<T> =
            object : RecursivePrompt<T> {
                override val prompts: List<Prompt<*>> = prompts
                override val transformer: (List<*>) -> T = transformer
            }
    }

}

//fun <T> collectInputs(
//    prompts: List<Prompt<*>>,
//    inputsConverter: (List<String>) -> T,
//): T =
//    inputsConverter(
//        prompts.map { prompt: Prompt<> ->
//            prompt.getResult()!!
//        },
//    )
