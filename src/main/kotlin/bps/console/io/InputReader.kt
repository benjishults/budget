package bps.console.io

fun interface InputReader : () -> String

val DefaultInputReader: InputReader =
    InputReader {
        readln()
    }
