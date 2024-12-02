package bps.budget

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

interface WithIo {
    val inputReader: InputReader get() = DefaultInputReader
    val outPrinter: OutPrinter get() = DefaultOutPrinter
}
