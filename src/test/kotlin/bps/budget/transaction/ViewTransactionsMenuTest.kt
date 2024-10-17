package bps.budget.transaction

import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.defaultGeneralAccountName
import bps.budget.persistence.BudgetDao
import bps.console.SimpleConsoleIoTestFixture
import io.kotest.core.spec.style.FreeSpec

class TransactionsMenuTest : FreeSpec(),
    SimpleConsoleIoTestFixture {

    override val inputs: MutableList<String> = mutableListOf()
    override val outputs: MutableList<String> = mutableListOf()

    init {
        clearInputsAndOutputsBeforeEach()

        val fetchTransactionsCallsExpected = mutableMapOf<Pair<Int, Int>, List<BudgetDao.ExtendedTransactionItem>>()
        val fetchTransactionsCallsMade = mutableListOf<Pair<Int, Int>>()

        val selectedAccount = CategoryAccount(
            name = "Test Category Account",
        )
        val generalAccount = CategoryAccount(
            name = defaultGeneralAccountName,
        )

        val budgetDao = object : BudgetDao {
            override fun Account.fetchTransactionItemsInvolvingAccount(
                budgetData: BudgetData,
                limit: Int,
                offset: Int,
            ): List<BudgetDao.ExtendedTransactionItem> {
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
