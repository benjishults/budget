package bps.budget

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

const val recordIncome = "Record Income"
const val makeAllowances = "Make Allowances"
const val writeOrClearChecks = "Write or Clear Checks"
const val useOrPayCreditCards = "Use or Pay Credit Cards"
const val transfer = "Transfer Money"
const val manageAccounts = "Manage Accounts"
const val recordSpending = "Record Spending"
const val manageTransactions = "Manage Transactions"

interface WithIo {
    val inputReader: InputReader get() = DefaultInputReader
    val outPrinter: OutPrinter get() = DefaultOutPrinter
}
