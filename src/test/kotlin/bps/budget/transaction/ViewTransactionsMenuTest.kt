package bps.budget.transaction

import bps.budget.model.Account
import bps.budget.model.CategoryAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.console.SimpleConsoleIoTestFixture
import bps.console.app.MenuApplicationWithQuit
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.math.BigDecimal
import java.util.UUID

class ViewTransactionsMenuTest : FreeSpec(),
    SimpleConsoleIoTestFixture {

    override val inputs: MutableList<String> = mutableListOf()
    override val outputs: MutableList<String> = mutableListOf()

    init {
        clearInputsAndOutputsBeforeEach()

        val clock = object : Clock {
            var secondCount = 0
            override fun now(): Instant =
                Instant.parse(String.format("2024-08-09T00:00:%02d.500Z", secondCount++))
        }
        val fetchTransactionsCallsExpected = mutableMapOf<Pair<Int, Int>, List<BudgetDao.ExtendedTransactionItem>>()
        val fetchTransactionsCallsMade = mutableListOf<Pair<Int, Int>>()

        val budgetId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val selectedAccount = CategoryAccount(
            id = accountId,
            name = "Test Category Account",
            budgetId = budgetId,
            balance = BigDecimal(1000.00).setScale(2),
        )

        val budgetDao = object : BudgetDao {
            override fun fetchTransactionItemsInvolvingAccount(
                account: Account,
                limit: Int,
                offset: Int,
                balanceAtEndOfPage: BigDecimal?,
            ): List<BudgetDao.ExtendedTransactionItem> {
                fetchTransactionsCallsMade.add(limit to offset)
                return fetchTransactionsCallsExpected[limit to offset] ?: emptyList()
            }

            override fun close() {
            }

            override fun getTransactionOrNull(
                transactionId: UUID,
                budgetId: UUID,
                accountIdToAccountMap: Map<UUID, Account>,
            ): Transaction? = null

        }
//        val menuSession: MenuSession = MenuSession(
//            ViewTransactionsMenu(
//                transactions = budgetDao.fetchTransactions(selectedAccount, budgetData),
//                account = selectedAccount,
//                budgetDao = budgetDao,
//                budgetData = budgetData,
//                outPrinter = outPrinter,
//            ),
//        )
        "!expect plenty of transactions" {
            val contextStack = mutableListOf<BigDecimal>()
            val menu = ViewTransactionsMenu(
                account = selectedAccount,
                budgetDao = budgetDao,
                budgetId = budgetId,
                contextStack = contextStack,
                limit = 3,
                accountIdToAccountMap = mapOf(accountId to selectedAccount),
                timeZone = TimeZone.UTC,
                outPrinter = outPrinter,
            )
            fetchTransactionsCallsExpected.clear()
            fetchTransactionsCallsExpected[3 to 0] = listOf(
                BudgetDao.ExtendedTransactionItem(
                    item = Transaction.ItemBuilder(
                        id = UUID.randomUUID(),
                        amount = 5.toBigDecimal().setScale(2),
                        description = "first",
                        categoryAccount = selectedAccount,
                    ),
                    accountBalanceAfterItem = selectedAccount.balance - (7.toBigDecimal() + 6.toBigDecimal()),
                    transactionId = UUID.randomUUID(),
                    transactionDescription = "first transaction",
                    transactionTimestamp = clock.now(),
                    budgetDao = budgetDao,
                    budgetId = budgetId,
                ),
                BudgetDao.ExtendedTransactionItem(
                    item = Transaction.ItemBuilder(
                        id = UUID.randomUUID(),
                        amount = 6.toBigDecimal().setScale(2),
                        description = "second",
                        categoryAccount = selectedAccount,
                    ),
                    accountBalanceAfterItem = selectedAccount.balance - 7.toBigDecimal(),
                    transactionId = UUID.randomUUID(),
                    transactionDescription = "first transaction",
                    transactionTimestamp = clock.now(),
                    budgetDao = budgetDao,
                    budgetId = budgetId,
                ),
                BudgetDao.ExtendedTransactionItem(
                    item = Transaction.ItemBuilder(
                        id = UUID.randomUUID(),
                        amount = 7.toBigDecimal().setScale(2),
                        description = "third",
                        categoryAccount = selectedAccount,
                    ),
                    accountBalanceAfterItem = selectedAccount.balance,
                    transactionId = UUID.randomUUID(),
                    transactionDescription = "first transaction",
                    transactionTimestamp = clock.now(),
                    budgetDao = budgetDao,
                    budgetId = budgetId,
                ),
            )
            inputs.addAll(listOf("n", "n", "p", "p"))
            MenuApplicationWithQuit(
                topLevelMenu = menu,
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .use { it.run() }
            outputs shouldContainExactly listOf(
                """
'Test Category Account' Account Transactions
    Time Stamp          | Amount     | Balance    | Description
 1. 2024-08-09 00:00:00 |       5.00 |     987.00 | first
 2. 2024-08-09 00:00:01 |       6.00 |     993.00 | second
 3. 2024-08-09 00:00:02 |       7.00 |   1,000.00 | third
 4. Next Items (n)
 5. Back (b)
 6. Quit (q)
""".trimIndent(),
                "Select transaction for details: ",
            )

        }
    }

}
