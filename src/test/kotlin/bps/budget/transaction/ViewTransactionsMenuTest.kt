package bps.budget.transaction

import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.Transaction
import bps.budget.model.defaultGeneralAccountName
import bps.budget.persistence.BudgetDao
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import io.kotest.core.spec.style.FreeSpec

class TransactionsMenuTest : FreeSpec() {

    init {
        val outputs: MutableList<String> = mutableListOf()
        val outPrinter = OutPrinter { outputs.add(it) }
        val inputs: MutableList<String> = mutableListOf()
        val inputReader = InputReader { inputs.removeFirst() }

        val fetchTransactionsCallsExpected = mutableMapOf<Pair<Int, Long>, List<Transaction>>()
        val fetchTransactionsCallsMade = mutableListOf<Pair<Int, Long>>()

        val selectedAccount = CategoryAccount(
            name = "Test Category Account",
        )
        val generalAccount = CategoryAccount(
            name = defaultGeneralAccountName,
        )

        val budgetDao = object : BudgetDao {
            override fun fetchTransactions(
                account: Account,
                data: BudgetData,
                limit: Int,
                offset: Long,
            ): List<Transaction> {
                fetchTransactionsCallsMade.add(limit to offset)
                return fetchTransactionsCallsExpected[limit to offset] ?: emptyList()
            }

            override fun close() {
            }

        }
//        val budgetData = BudgetData(
//            generalAccount = generalAccount,
//            categoryAccounts = TODO(),
//            realAccounts = TODO(),
//            draftAccounts = TODO(),
//        )
//        val subject: ViewTransactionsMenu = ViewTransactionsMenu(
//            transactions = TODO(),
//            account = selectedAccount,
//            budgetDao = TODO(),
//            budgetData = TODO(),
//            limit = TODO(),
//            offset = TODO(),
//            header = TODO(),
//            prompt = TODO(),
//            outPrinter = TODO(),
//        )
//        val menuSession: MenuSession = MenuSession(
//            ViewTransactionsMenu(
//                transactions = budgetDao.fetchTransactions(selectedAccount, budgetData),
//                account = selectedAccount,
//                budgetDao = budgetDao,
//                budgetData = budgetData,
//                outPrinter = outPrinter,
//            ),
//        )
        "expect plenty of transactions" {

        }
    }

}
