package bps.budget

import bps.budget.auth.User
import bps.budget.jdbc.BasicAccountsJdbcTestFixture
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
import bps.budget.persistence.getBudgetNameFromPersistenceConfig
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.ui.ConsoleUiFacade
import bps.console.ComplexConsoleIoTestFixture
import io.kotest.assertions.asClue
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal
import java.util.UUID
import kotlin.concurrent.thread

class BudgetApplicationTransactionsTest : FreeSpec(),
    BasicAccountsJdbcTestFixture,
    ComplexConsoleIoTestFixture by ComplexConsoleIoTestFixture() {

    override val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)

    init {
        val budgetId: UUID = UUID.fromString("89bc165a-ee70-43a4-b637-2774bcfc3ea4")
        clearInputsAndOutputsBeforeEach()
        val userId = UUID.fromString("f0f209c8-1b1e-43b3-8799-2dba58524d02")
        createBasicAccountsBeforeSpec(
            budgetId = budgetId,
            budgetName = getBudgetNameFromPersistenceConfig(configurations.persistence)!!,
            user = User(userId, configurations.user.defaultLogin!!),
        )
        resetBalancesAndTransactionAfterSpec(budgetId)
        closeJdbcAfterSpec()

        val uiFunctions = ConsoleUiFacade(inputReader, outPrinter)

        val clock = object : Clock {
            var secondCount = 0
            override fun now(): Instant =
                Instant.parse(String.format("2024-08-09T00:00:%02d.500Z", secondCount++))
        }

        "run application with data from DB" - {
//            Thread.currentThread().name = "Test Thread"
            val application = BudgetApplication(
                uiFunctions,
                configurations,
                inputReader,
                outPrinter,
                clock,
            )
            thread(name = "Test Application Thread") {
                application.run()
            }
            "record income" {
                // prepare inputs
                inputs.addAll(
                    listOf("1", "1", "5000", "", "", "2", "200", "", "", "3"),
                )
                unPause()
                waitForPause()
                application.budgetData.asClue { budgetData: BudgetData ->
                    budgetData.categoryAccounts shouldContain budgetData.generalAccount
                    budgetData.generalAccount.balance shouldBe BigDecimal(5200).setScale(2)
                    budgetData.categoryAccounts.size shouldBe 10
                }
                application.budgetDao.load(application.budgetData.id, userId).asClue { budgetData: BudgetData ->
                    budgetData.categoryAccounts shouldContain budgetData.generalAccount
                    budgetData.generalAccount.balance shouldBe BigDecimal(5200).setScale(2)
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
                        |Enter the real fund account into which the money is going (e.g., savings).
                        |The same amount of money will be automatically entered into the 'General' account.
                        |""".trimMargin(),
                    """
                        |Select account receiving the income:
                        | 1.       0.00 | Checking
                        | 2.       0.00 | Wallet
                        | 3. Back
                        | 4. Quit
                        |""".trimMargin(),
                    "Enter selection: ",
                    "Enter the amount of income: ",
                    "Enter description of income [income into $defaultCheckingAccountName]: ",
                    "Use current time [Y]? ",
                    """
                        |Select account receiving the income:
                        | 1.   5,000.00 | Checking
                        | 2.       0.00 | Wallet
                        | 3. Back
                        | 4. Quit
                        |""".trimMargin(),
                    "Enter selection: ",
                    "Enter the amount of income: ",
                    "Enter description of income [income into $defaultWalletAccountName]: ",
                    "Use current time [Y]? ",
                    """
                        |Select account receiving the income:
                        | 1.   5,000.00 | Checking
                        | 2.     200.00 | Wallet
                        | 3. Back
                        | 4. Quit
                        |""".trimMargin(),
                    "Enter selection: ",
                )
            }
            "allocate to food and necessities" {
                inputs.addAll(
                    listOf("2", "3", "300", "", "5", "100", "", "10"),
                )
                unPause()
                waitForPause()
                application.budgetData.asClue { budgetData: BudgetData ->
                    budgetData.categoryAccounts shouldContain budgetData.generalAccount
                    budgetData.generalAccount.balance shouldBe BigDecimal(5200 - 400).setScale(2)
                    budgetData.categoryAccounts.size shouldBe 10
                    budgetData.categoryAccounts
                        .find { it.name == defaultFoodAccountName }!!
                        .balance shouldBe BigDecimal(300).setScale(2)
                }
                application.budgetDao.load(application.budgetData.id, userId).asClue { budgetData: BudgetData ->
                    budgetData.categoryAccounts shouldContain budgetData.generalAccount
                    budgetData.generalAccount.balance shouldBe BigDecimal(5200 - 400).setScale(2)
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
 1.       0.00 | Education
 2.       0.00 | Entertainment
 3.       0.00 | Food
 4.       0.00 | Medical
 5.       0.00 | Necessities
 6.       0.00 | Network
 7.       0.00 | Transportation
 8.       0.00 | Travel
 9.       0.00 | Work
10. Back
11. Quit
""",
                    "Enter selection: ",
                    "Enter the amount to allocate into ${application.budgetData.categoryAccounts[2].name} (0.00 - 5200.00]: ",
                    "Enter description of transaction [allowance into $defaultFoodAccountName]: ",
                    "Select account to allocate money into from ${application.budgetData.generalAccount.name}: " + """
 1.       0.00 | Education
 2.       0.00 | Entertainment
 3.     300.00 | Food
 4.       0.00 | Medical
 5.       0.00 | Necessities
 6.       0.00 | Network
 7.       0.00 | Transportation
 8.       0.00 | Travel
 9.       0.00 | Work
10. Back
11. Quit
""",
                    "Enter selection: ",
                    "Enter the amount to allocate into ${application.budgetData.categoryAccounts[5].name} (0.00 - 4900.00]: ",
                    "Enter description of transaction [allowance into $defaultNecessitiesAccountName]: ",
                    "Select account to allocate money into from ${application.budgetData.generalAccount.name}: " + """
 1.       0.00 | Education
 2.       0.00 | Entertainment
 3.     300.00 | Food
 4.       0.00 | Medical
 5.     100.00 | Necessities
 6.       0.00 | Network
 7.       0.00 | Transportation
 8.       0.00 | Travel
 9.       0.00 | Work
10. Back
11. Quit
""",
                    "Enter selection: ",
                )
            }
            "view transactions" {
                inputs.addAll(listOf("4", "1", ""))
                unPause()
                waitForPause()
                outputs shouldContainExactly listOf(
                    """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. $viewHistory
                            | 5. $recordDrafts
                            | 6. $clearDrafts
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                    "Enter selection: ",
                    """Select account to view history
 1.   4,800.00 | General
 2.       0.00 | Education
 3.       0.00 | Entertainment
 4.     300.00 | Food
 5.       0.00 | Medical
 6.     100.00 | Necessities
 7.       0.00 | Network
 8.       0.00 | Transportation
 9.       0.00 | Travel
10.       0.00 | Work
11.   5,000.00 | Checking
12.     200.00 | Wallet
13.       0.00 | Checking Drafts
14. Back
15. Quit
""",
                    "Enter selection: ",
                    """
                        |CategoryAccount('General', 4800.00) Transactions
                        |    Time Stamp          | Balance    | Description
                        | 1. 2024-08-08 19:00:00 |   5,000.00 | income into $defaultCheckingAccountName
                        | 2. 2024-08-08 19:00:01 |     200.00 | income into $defaultWalletAccountName
                        | 3. 2024-08-08 19:00:02 |    -300.00 | allowance into $defaultFoodAccountName
                        | 4. 2024-08-08 19:00:03 |    -100.00 | allowance into $defaultNecessitiesAccountName
                        | 5. Next Items
                        | 6. Back
                        | 7. Quit
                        |""".trimMargin(),
                    "Select transaction for details: ",
                )
            }
            "!write a check to SuperMarket" {
                inputs.addAll(
                    listOf("4", "1", "300", "", "9"),
                )
                unPause()
                waitForPause()
                application.budgetData.asClue { budgetData: BudgetData ->
                    budgetData.categoryAccounts shouldContain budgetData.generalAccount
                    budgetData.generalAccount.balance shouldBe BigDecimal(5000 - 300).setScale(2)
                    budgetData.categoryAccounts.size shouldBe 10
                    budgetData.categoryAccounts
                        .find { it.name == defaultFoodAccountName }!!
                        .balance shouldBe BigDecimal(300).setScale(2)
                }
                application.budgetDao.load(application.budgetData.id, userId).asClue { budgetData: BudgetData ->
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
                    "Enter description of transaction [allowance into $defaultFoodAccountName]: ",
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
                            timestamp = clock.now(),
                        )
                        .apply {
                            categoryItemBuilders.add(
                                Transaction.ItemBuilder(
                                    -amount,
                                    categoryAccount = application.budgetData.categoryAccounts.find { it.name == defaultFoodAccountName }!!,
                                ),
                            )
                            draftItemBuilders.add(
                                Transaction.ItemBuilder(
                                    amount,
                                    draftAccount = application.budgetData.draftAccounts.find { it.name == defaultCheckingDraftsAccountName }!!,
                                ),
                            )
                        }
                        .build()
                application.budgetData.commit(writeCheck)
                jdbcDao.commit(writeCheck, application.budgetData.id)
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
                    timestamp = clock.now(),
                )
                    .apply {
                        realItemBuilders.add(
                            Transaction.ItemBuilder(
                                -amount,
                                realAccount = application.budgetData.realAccounts.find { it.name == defaultCheckingAccountName }!!,
                            ),
                        )
                        draftItemBuilders.add(
                            Transaction.ItemBuilder(
                                -amount,
                                draftAccount = application.budgetData.draftAccounts.find { it.name == defaultCheckingDraftsAccountName }!!,
                            ),
                        )

                    }
                    .build()
                application.budgetData.commit(writeCheck)
                jdbcDao.commit(writeCheck, application.budgetData.id)
            }
            "!check balances after check clears" {
                checkBalancesAfterCheckClears(application.budgetData)
            }
            "!check balances in DB" {
                checkBalancesAfterCheckClears(jdbcDao.load(application.budgetData.id, userId))
            }
        }
    }


}

fun checkBalancesAfterCheckClears(budgetData: BudgetData) {
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
