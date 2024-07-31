package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import java.time.LocalDateTime

fun interface Prompt<out T> {

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

interface RecursivePrompt<T> : Prompt<T> {
    val prompts: List<Prompt<*>>
    val transformer: (List<*>) -> T
    val onError: (Throwable) -> T
        //        get() = { getResult() }
        get() = {
            throw it
        }

    /**
     * calls [onError] on exception
     */
    override fun getResult(): T =
        try {
            transformer(
                prompts.map { innerPrompt: Prompt<*> ->
                    innerPrompt.getResult()
                },
            )
        } catch (ex: Exception) {
            onError(ex)
        }

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

class TimestampPrompt(
    inputReader: InputReader = DefaultInputReader,
    outPrinter: OutPrinter = DefaultOutPrinter,
    val now: LocalDateTime = LocalDateTime.now(),
) : SimplePromptWithDefault<LocalDateTime>(
    "Time: ", now.toString(), inputReader, outPrinter,
    {
        when (it) {
            "" -> {
                now
            }
            else -> {
                RecursivePrompt<LocalDateTime>(
                    listOf(
                        SimplePromptWithDefault("          year: ", now.year.toString(), inputReader, outPrinter),
                        SimplePromptWithDefault("         month: ", now.month.toString(), inputReader, outPrinter),
                        SimplePromptWithDefault("  day of month: ", now.dayOfMonth.toString(), inputReader, outPrinter),
                        SimplePromptWithDefault(" 24 hour clock: ", now.hour.toString(), inputReader, outPrinter),
                        SimplePromptWithDefault("minute of hour: ", now.minute.toString(), inputReader, outPrinter),
                        SimplePromptWithDefault<String>(
                            "        second: ",
                            now.second.toString(),
                            inputReader,
                            outPrinter,
                        ),
                    ),
                ) { entries ->
                    LocalDateTime.parse("${entries[0]}-${entries[1]}-${entries[2]}T${entries[3]}:${entries[4]}:${entries[5]}")
                }
                    .getResult()
            }
        }
    },
)
