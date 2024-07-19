package bps.console.io

fun interface OutPrinter : (String) -> Unit

object DefaultOutPrinter : OutPrinter {
    override fun invoke(out: String): Unit =
        print(out)
}
