package bps.budget

import bps.budget.jdbc.BasicAccountsTestFixture
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class BudgetApplicationTransactionsTest : FreeSpec(), BasicAccountsTestFixture {

    override val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)

    // NOTE these allow to pause the application between tests.  This was needed so that I wouldn't have to
    //      quit the application (closing the JDBC connection) between tests.
    //      When the application is paused, it will stop before printing any more input.
    val paused = AtomicBoolean(false)
    val waitForUnPause = AtomicReference(CountDownLatch(0))

    // NOTE waitForPause before validation to allow the application to finish processing and get to the point of making
    //      more output so that validation happens after processing.
    val waitForPause = AtomicReference(CountDownLatch(1))

    private fun pause() {
        check(!paused.get()) { "already paused" }
        waitForUnPause.set(CountDownLatch(1))
        paused.set(true)
        waitForPause.get().countDown()
    }

    private fun unPause() {
        check(paused.get()) { "not paused" }
        waitForPause.set(CountDownLatch(1))
        paused.set(false)
        waitForUnPause.get().countDown()
    }

    // NOTE the thread clearing this is not the thread that adds to it
    val inputs = CopyOnWriteArrayList<String>()
    val inputReader = InputReader {
        inputs.removeFirst()
    }

    // NOTE the thread clearing this is not the thread that adds to it
    val outputs = CopyOnWriteArrayList<String>()

    // NOTE when the inputs is empty, the application will pause itself
    val outPrinter = OutPrinter {
        if (inputs.isEmpty()) {
            pause()
        }
        if (paused.get())
            waitForUnPause.get().await()
        outputs.add(it)
    }

    init {
        createBasicAccountsBeforeSpec()
        resetBalancesAndTransactionAfterSpec()
        closeJdbcAfterSpec()

        beforeEach {
            inputs.clear()
            outputs.clear()
        }
        val uiFunctions = ConsoleUiFacade(inputReader, outPrinter)
        "run application with data from DB" - {
            val application = BudgetApplication(
                uiFunctions,
                configurations,
                inputReader,
                outPrinter,
            )
            thread {
                application.run()
            }
            "record income" {
                // prepare inputs
                inputs.addAll(
                    listOf("1", "1", "5000", "", ""),
                )
                unPause()
                waitForPause.get().await()
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
                    """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. View History
                            | 5. $recordDrafts
                            | 6. $clearDrafts
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                    "Enter selection: ",
                    """
            |The user should enter the real fund account into which the money is going (e.g., savings).
            |The same amount of money should be automatically entered into the general category fund account.
            |""".trimMargin(),
                    """Select account receiving the income:
 1. RealAccount('Checking', 0.00)
 2. RealAccount('Wallet', 0.00)
Enter selection: """,
                    "Enter the amount of income: ",
                    "Enter description of income:  [income] ",
                    "Enter the time income was received:  [now] ",
                )
            }
            "allocate to food and necessities" {
                inputs.addAll(
                    listOf("2", "3", "300", "", "5", "100", "", "10"),
                )
                unPause()
                waitForPause.get().await()
                application.budgetData.asClue { budgetData: BudgetData ->
                    budgetData.categoryAccounts shouldContain budgetData.generalAccount
                    budgetData.generalAccount.balance shouldBe BigDecimal(5000 - 400).setScale(2)
                    budgetData.categoryAccounts.size shouldBe 10
                    budgetData.categoryAccounts
                        .find { it.name == defaultFoodAccountName }!!
                        .balance shouldBe BigDecimal(300).setScale(2)
                }
                application.budgetDao.load().asClue { budgetData: BudgetData ->
                    budgetData.categoryAccounts shouldContain budgetData.generalAccount
                    budgetData.generalAccount.balance shouldBe BigDecimal(5000 - 400).setScale(2)
                    budgetData.categoryAccounts.size shouldBe 10
                    budgetData.categoryAccounts
                        .find { it.name == defaultFoodAccountName }!!
                        .balance shouldBe BigDecimal(300).setScale(2)
                }
                outputs shouldContainExactly listOf(
                    """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. View History
                            | 5. $recordDrafts
                            | 6. $clearDrafts
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                    "Enter selection: ",
                    """
                |Every month or so, the user may want to distribute the income from the general category fund accounts into the other category fund accounts.
                |Optional: You may want to add options to automate this procedure for the user.
                |I.e., let the user decide on a predetermined amount that will be transferred to each category fund account each month.
                |For some category fund accounts the user may prefer to bring the balance up to a certain amount each month.""".trimMargin(),
                    "Select account to allocate money into from ${application.budgetData.generalAccount.name}: " + """
 1. CategoryAccount('Education', 0.00)
 2. CategoryAccount('Entertainment', 0.00)
 3. CategoryAccount('Food', 0.00)
 4. CategoryAccount('Medical', 0.00)
 5. CategoryAccount('Necessities', 0.00)
 6. CategoryAccount('Network', 0.00)
 7. CategoryAccount('Transportation', 0.00)
 8. CategoryAccount('Travel', 0.00)
 9. CategoryAccount('Work', 0.00)
 10. Back
 11. Quit
""",
                    "Enter selection: ",
                    "Enter the amount to allocate into ${application.budgetData.categoryAccounts[2].name} (0.00 - 5000.00]: ",
                    "Enter description of transaction:  [allowance] ",
                    "Select account to allocate money into from ${application.budgetData.generalAccount.name}: " + """
 1. CategoryAccount('Education', 0.00)
 2. CategoryAccount('Entertainment', 0.00)
 3. CategoryAccount('Food', 0.00)
 4. CategoryAccount('Medical', 0.00)
 5. CategoryAccount('Necessities', 0.00)
 6. CategoryAccount('Network', 0.00)
 7. CategoryAccount('Transportation', 0.00)
 8. CategoryAccount('Travel', 0.00)
 9. CategoryAccount('Work', 0.00)
 10. Back
 11. Quit
""",
                    "Enter selection: ",
                    "Enter the amount to allocate into ${application.budgetData.categoryAccounts[5].name} (0.00 - 4700.00]: ",
                    "Enter description of transaction:  [allowance] ",
                    "Select account to allocate money into from ${application.budgetData.generalAccount.name}: " + """
 1. CategoryAccount('Education', 0.00)
 2. CategoryAccount('Entertainment', 0.00)
 3. CategoryAccount('Food', 0.00)
 4. CategoryAccount('Medical', 0.00)
 5. CategoryAccount('Necessities', 0.00)
 6. CategoryAccount('Network', 0.00)
 7. CategoryAccount('Transportation', 0.00)
 8. CategoryAccount('Travel', 0.00)
 9. CategoryAccount('Work', 0.00)
 10. Back
 11. Quit
""",
                    "Enter selection: ",
                )
            }
            "!write a check to SuperMarket" {
                inputs.addAll(
                    listOf("4", "1", "300", "", "9"),
                )
                unPause()
                waitForPause.get().await()
                application.budgetData.asClue { budgetData: BudgetData ->
                    budgetData.categoryAccounts shouldContain budgetData.generalAccount
                    budgetData.generalAccount.balance shouldBe BigDecimal(5000 - 300).setScale(2)
                    budgetData.categoryAccounts.size shouldBe 10
                    budgetData.categoryAccounts
                        .find { it.name == defaultFoodAccountName }!!
                        .balance shouldBe BigDecimal(300).setScale(2)
                }
                application.budgetDao.load().asClue { budgetData: BudgetData ->
                    budgetData.categoryAccounts shouldContain budgetData.generalAccount
                    budgetData.generalAccount.balance shouldBe BigDecimal(5000 - 300).setScale(2)
                    budgetData.categoryAccounts.size shouldBe 10
                    budgetData.categoryAccounts
                        .find { it.name == defaultFoodAccountName }!!
                        .balance shouldBe BigDecimal(300).setScale(2)
                }
                unPause()
                outputs shouldContainExactly listOf(
                    """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. View History
                            | 5. $recordDrafts
                            | 6. $clearDrafts
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                    "Enter selection: ",
                    """
                |Every month or so, the user may want to distribute the income from the general category fund accounts into the other category fund accounts.
                |Optional: You may want to add options to automate this procedure for the user.
                |I.e., let the user decide on a predetermined amount that will be transferred to each category fund account each month.
                |For some category fund accounts the user may prefer to bring the balance up to a certain amount each month.""".trimMargin(),
                    "Select account to allocate money into from ${application.budgetData.generalAccount.name}: " + """
 1. CategoryAccount('Education', 0.00)
 2. CategoryAccount('Entertainment', 0.00)
 3. CategoryAccount('Food', 0.00)
 4. CategoryAccount('Medical', 0.00)
 5. CategoryAccount('Necessities', 0.00)
 6. CategoryAccount('Network', 0.00)
 7. CategoryAccount('Transportation', 0.00)
 8. CategoryAccount('Travel', 0.00)
 9. CategoryAccount('Work', 0.00)
Enter selection: """,
                    "Enter the amount to allocate into ${application.budgetData.categoryAccounts[2].name} (0.00 - 5000.00]: ",
                    "Enter description of transaction:  [allowance] ",
                    """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. View History
                            | 5. $recordDrafts
                            | 6. $clearDrafts
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                    "Enter selection: ",
                    "Quitting\n",
                )
                val amount = BigDecimal("100.00")
                val writeCheck: Transaction =
                    Transaction
                        .Builder(
                            description = "groceries",
                            timestamp = OffsetDateTime.now(),
                        )
                        .apply {
                            categoryItems.add(
                                Transaction.ItemBuilder(
                                    -amount,
                                    categoryAccount = application.budgetData.categoryAccounts.find { it.name == defaultFoodAccountName }!!,
                                ),
                            )
                            draftItems.add(
                                Transaction.ItemBuilder(
                                    amount,
                                    draftAccount = application.budgetData.draftAccounts.find { it.name == defaultCheckingDraftsAccountName }!!,
                                ),
                            )
                        }
                        .build()
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
                val writeCheck: Transaction = Transaction.Builder(
                    description = "groceries",
                    timestamp = OffsetDateTime.now(),
                )
                    .apply {
                        realItems.add(
                            Transaction.ItemBuilder(
                                -amount,
                                realAccount = application.budgetData.realAccounts.find { it.name == defaultCheckingAccountName }!!,
                            ),
                        )
                        draftItems.add(
                            Transaction.ItemBuilder(
                                -amount,
                                draftAccount = application.budgetData.draftAccounts.find { it.name == defaultCheckingDraftsAccountName }!!,
                            ),
                        )

                    }
                    .build()
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
