package bps.budget

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

const val recordIncomeLabel = "Record Income"
const val makeAllowancesLabel = "Make Allowances"
const val writeOrClearChecksLabel = "Write or Clear Checks"
const val useOrPayCreditCardsLabel = "Use or Pay Credit Cards"
const val transferLabel = "Transfer Money"
const val manageAccountsLabel = "Manage Accounts"
const val recordSpendingLabel = "Record Spending"
const val manageTransactionsLabel = "Manage Transactions"

interface WithIo {
    val inputReader: InputReader get() = DefaultInputReader
    val outPrinter: OutPrinter get() = DefaultOutPrinter
}
