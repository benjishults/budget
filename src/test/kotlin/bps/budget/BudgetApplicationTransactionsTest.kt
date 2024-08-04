package bps.budget

import bps.budget.jdbc.BasicAccountsTestFixture
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.model.TransactionItem
import bps.budget.model.defaultCheckingAccountName
import bps.budget.model.defaultCheckingDraftsAccountName
import bps.budget.model.defaultEducationAccountName
import bps.budget.model.defaultEntertainmentAccountName
import bps.budget.model.defaultFoodAccountName
import bps.budget.model.defaultGeneralAccountName
import bps.budget.model.defaultMedicalAccountName
import bps.budget.model.defaultNecessitiesAccountName
import bps.budget.model.defaultNetworkAccountName
import bps.budget.model.defaultTransportationAccountName
import bps.budget.model.defaultTravelAccountName
import bps.budget.model.defaultWalletAccountName
import bps.budget.model.defaultWorkAccountName
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.ui.ConsoleUiFacade
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import io.kotest.assertions.asClue
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.OffsetDateTime

class BudgetApplicationTransactionsTest : FreeSpec(), BasicAccountsTestFixture {

    override val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)

    init {
        resetBalancesAndTransactionAfterSpec()
        useBasicAccounts()

        val outputs: MutableList<String> = mutableListOf()
        val outPrinter = OutPrinter {
            outputs.add(it)
        }
        val inputs: MutableList<String> = mutableListOf()
        val inputReader = InputReader {
            inputs.removeFirst()
        }
        beforeEach {
            inputs.clear()
            outputs.clear()
        }
        val uiFunctions = ConsoleUiFacade(inputReader, outPrinter)
        "with data from DB" - {
            val application = BudgetApplication(
                uiFunctions,
                configurations,
                inputReader,
                outPrinter,
            )
            "record income" {
                inputs.addAll(
                    listOf("1", "2", "5000", "", "", "8"),
                )
                application.use { application: BudgetApplication ->
                    application.run()
                    application.budgetData.asClue { budgetData: BudgetData ->
                        budgetData.categoryAccounts shouldContain budgetData.generalAccount
                        budgetData.generalAccount.balance shouldBe BigDecimal(5000).setScale(2)
                        budgetData.categoryAccounts.size shouldBe 10
                    }
                    application.budgetDao.load().asClue { budgetData: BudgetData ->
                        budgetData.categoryAccounts shouldContain budgetData.generalAccount
                        budgetData.generalAccount.balance shouldBe BigDecimal(5000).setScale(2)
                        budgetData.categoryAccounts.size shouldBe 10
                    }
                    outputs shouldContainExactly listOf(
                        """Budget!
 1. Record Income
 2. Make Allowances
 3. Record Transactions
 4. Write Checks or Use Credit Cards
 5. Clear Drafts
 6. Transfer Money
 7. Customize
 8. Quit
""",
                        "Enter selection: ",
                        """
            |The user should enter the real fund account into which the money is going (e.g., savings).
            |The same amount of money should be automatically entered into the general category fund account.
            |""".trimMargin(),
                        """Select account receiving the income:
 1. RealAccount('Wallet', 0.00)
 2. RealAccount('Checking', 0.00)
Enter selection: """,
                        "Enter the amount of income: ",
                        "Enter description of income:  [income] ",
                        "Enter the time income was received:  [now] ",
                        """Budget!
 1. Record Income
 2. Make Allowances
 3. Record Transactions
 4. Write Checks or Use Credit Cards
 5. Clear Drafts
 6. Transfer Money
 7. Customize
 8. Quit
""",
                        "Enter selection: ",
                        "Quitting\n",
                    )
                }
            }
            "!allocate to food" {
                val amount = BigDecimal("300.00")
                val allocate = Transaction(
                    amount = amount,
                    description = "allocate",
                    timestamp = OffsetDateTime.now(),
                    categoryItems = buildList {
                        add(TransactionItem(-amount, categoryAccount = application.budgetData.generalAccount))
                        add(
                            TransactionItem(
                                amount,
                                categoryAccount = application.budgetData.categoryAccounts.find { it.name == defaultFoodAccountName }!!,
                            ),
                        )
                    },
                )
                application.budgetData.commit(allocate)
                jdbcDao.commit(allocate)
            }
            "!write a check for food" {
                val amount = BigDecimal("100.00")
                val writeCheck = Transaction(
                    amount = amount,
                    description = "groceries",
                    timestamp = OffsetDateTime.now(),
                    categoryItems = listOf(
                        TransactionItem(
                            -amount,
                            categoryAccount = application.budgetData.categoryAccounts.find { it.name == defaultFoodAccountName }!!,
                        ),
                    ),
                    draftItems = listOf(
                        TransactionItem(
                            amount,
                            draftAccount = application.budgetData.draftAccounts.find { it.name == defaultCheckingDraftsAccountName }!!,
                        ),
                    ),
                )
                application.budgetData.commit(writeCheck)
                jdbcDao.commit(writeCheck)
            }
            "!check balances after writing check" {
                application.budgetData.realAccounts.forEach { realAccount: RealAccount ->
                    when (realAccount.name) {
                        defaultCheckingAccountName -> {
                            realAccount.balance shouldBe BigDecimal("1000.00")
                        }
                        defaultWalletAccountName ->
                            realAccount.balance shouldBe BigDecimal.ZERO.setScale(2)
                        else ->
                            fail("unexpected real account")
                    }
                }
                application.budgetData.categoryAccounts.forEach { it: CategoryAccount ->
                    when (it.name) {
                        defaultGeneralAccountName -> it.balance shouldBe BigDecimal("700.00")
                        defaultFoodAccountName -> it.balance shouldBe BigDecimal("200.00")
                        defaultNecessitiesAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultWorkAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultTransportationAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultTravelAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultMedicalAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultEducationAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultEntertainmentAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultNetworkAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        else -> fail("unexpected category account: $it")
                    }
                }
                application.budgetData.draftAccounts.forEach { it: DraftAccount ->
                    when (it.name) {
                        defaultCheckingDraftsAccountName -> it.balance shouldBe BigDecimal("100.00")
                        else -> fail("unexpected draft account: $it")
                    }
                }
            }
            "!check clears" {
                val amount = BigDecimal("100.00")
                val writeCheck = Transaction(
                    amount = amount,
                    description = "groceries",
                    timestamp = OffsetDateTime.now(),
                    realItems = listOf(
                        TransactionItem(
                            -amount,
                            realAccount = application.budgetData.realAccounts.find { it.name == defaultCheckingAccountName }!!,
                        ),
                    ),
                    draftItems = listOf(
                        TransactionItem(
                            -amount,
                            draftAccount = application.budgetData.draftAccounts.find { it.name == defaultCheckingDraftsAccountName }!!,
                        ),
                    ),
                )
                application.budgetData.commit(writeCheck)
                jdbcDao.commit(writeCheck)
            }
            "!check balances after check clears" {
                checkBalancesAfterCheckClears(application.budgetData)
            }
            "!check balances in DB" {
                checkBalancesAfterCheckClears(jdbcDao.load())
            }
        }
    }

}

private fun checkBalancesAfterCheckClears(budgetData: BudgetData) {
    budgetData.realAccounts.size shouldBe 2
    budgetData.realAccounts.forEach { realAccount: RealAccount ->
        when (realAccount.name) {
            defaultCheckingAccountName -> {
                realAccount.balance shouldBe BigDecimal("900.00")
            }
            defaultWalletAccountName ->
                realAccount.balance shouldBe BigDecimal.ZERO.setScale(2)
            else ->
                fail("unexpected real account: $realAccount")
        }
    }
    budgetData.categoryAccounts.size shouldBe 10
    budgetData.categoryAccounts.forEach { it: CategoryAccount ->
        when (it.name) {
            defaultGeneralAccountName -> it.balance shouldBe BigDecimal("700.00")
            defaultFoodAccountName -> it.balance shouldBe BigDecimal("200.00")
            defaultNecessitiesAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
            defaultWorkAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
            defaultTransportationAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
            defaultTravelAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
            defaultMedicalAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
            defaultEducationAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
            defaultEntertainmentAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
            defaultNetworkAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
            else -> fail("unexpected category account: $it")
        }
    }
    budgetData.draftAccounts.size shouldBe 1
    budgetData.draftAccounts.forEach { it: DraftAccount ->
        when (it.name) {
            defaultCheckingDraftsAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
            else -> fail("unexpected draft account: $it")
        }
    }

}
