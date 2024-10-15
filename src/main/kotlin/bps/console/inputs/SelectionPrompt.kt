package bps.console.inputs

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

// FIXME replace this with ScrollingSelectionMenu
interface SelectionPrompt<T : Any> : Prompt<T> {
    val header: String?
    val prompt: String
    val inputReader: InputReader
    val outPrinter: OutPrinter
    val options: List<T>

    fun print() {
        outPrinter(
            options
                .foldIndexed(
                    header
                        ?.let { StringBuilder("$header\n") }
                        ?: StringBuilder(""),
                ) { index: Int, builder: StringBuilder, item: T ->
                    builder.append(String.format("%2d. $item\n", index + 1))
                }
                .append(prompt)
                .toString(),
        )
    }

    fun readInput(): String? =
        inputReader()

    override fun getResult(): T {
        print()
        // TODO handle errors better
        return readInput()
            ?.toIntOrNull()
            ?.let { choice ->
                options[choice - 1]
            }
            ?: getResult()
    }

    companion object {
        operator fun <T : Any> invoke(
            header: String?,
            options: List<T>,
            prompt: String = "Enter selection: ",
            inputReader: InputReader = DefaultInputReader,
            outPrinter: OutPrinter = DefaultOutPrinter,
        ) =
            object : SelectionPrompt<T> {
                override val header: String? = header
                override val prompt: String = prompt
                override val inputReader: InputReader = inputReader
                override val outPrinter: OutPrinter = outPrinter
                override val options: List<T> = options
            }
    }

}
