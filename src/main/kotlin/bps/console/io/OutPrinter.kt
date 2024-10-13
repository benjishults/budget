package bps.console.io

fun interface OutPrinter : (String) -> Unit {
    fun important(message: String) {
        invoke("\n\n$message\n\n")
    }
}

val DefaultOutPrinter: OutPrinter = OutPrinter {
    print(it)
}
