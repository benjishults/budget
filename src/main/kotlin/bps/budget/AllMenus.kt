package bps.budget

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

const val recordIncome = "Record Income"
const val makeAllowances = "Make Allowances"
const val recordDrafts = "Write Checks or Use Credit Cards"
const val clearDrafts = "Clear Drafts"
const val transfer = "Transfer Money"
const val setup = "Manage Accounts"
const val recordSpending = "Record Spending"

class AllMenus(
    val inputReader: InputReader = DefaultInputReader,
    val outPrinter: OutPrinter = DefaultOutPrinter,
)
