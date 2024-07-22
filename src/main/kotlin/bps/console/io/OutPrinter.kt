package bps.console.io

fun interface OutPrinter : (String) -> Unit

val DefaultOutPrinter: OutPrinter = OutPrinter {
    print(it)
}
