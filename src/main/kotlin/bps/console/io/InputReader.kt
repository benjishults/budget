package bps.console.io

fun interface InputReader : () -> String?

object DefaultInputReader : InputReader {
    override fun invoke(): String? =
        readlnOrNull()
}
