package bps.budget

import bps.budget.auth.User
import bps.budget.jdbc.BasicAccountsJdbcTestFixture
import bps.budget.model.BudgetData
import bps.budget.model.defaultCheckingAccountName
import bps.budget.model.defaultFoodAccountName
import bps.budget.model.defaultNecessitiesAccountName
import bps.budget.model.defaultWalletAccountName
import bps.budget.persistence.getBudgetNameFromPersistenceConfig
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.ui.ConsoleUiFacade
import bps.console.ComplexConsoleIoTestFixture
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeTrue
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
    ComplexConsoleIoTestFixture by ComplexConsoleIoTestFixture(true) {

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
                inputs.addAll(
                    listOf("1", "1", "5000", "", "", "2", "200", "", "", "3"),
                )
                unPause()
                waitForPause(helper.awaitMillis).shouldBeTrue()
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
            }
            "allocate to food and necessities" {
                inputs.addAll(
                    listOf("2", "3", "300", "", "5", "100", "", "10"),
                )
                unPause()
                waitForPause(helper.awaitMillis).shouldBeTrue()
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
                    "Every month or so, you may want to distribute the income from the \"general\" category fund account into the other category fund accounts.\n",
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
                    "Enter the amount to allocate into ${application.budgetData.categoryAccounts[2].name} [0.00, 5200.00]: ",
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
                    "Enter the amount to allocate into ${application.budgetData.categoryAccounts[5].name} [0.00, 4900.00]: ",
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
                inputs.addAll(listOf("4", "1", "3", "5", "14"))
                unPause()
                waitForPause(helper.awaitMillis).shouldBeTrue()
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
                        |'General' Account Transactions
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:00 |   5,000.00 | income into $defaultCheckingAccountName
                        | 2. 2024-08-08 19:00:01 |     200.00 | income into $defaultWalletAccountName
                        | 3. 2024-08-08 19:00:02 |    -300.00 | allowance into $defaultFoodAccountName
                        | 4. 2024-08-08 19:00:03 |    -100.00 | allowance into $defaultNecessitiesAccountName
                        | 5. Back
                        | 6. Quit
                        |""".trimMargin(),
                    "Select transaction for details: ",
                    """
                        |2024-08-08 19:00:02
                        |allowance into Food
                        |Category Account | Amount     | Description
                        |General          |    -300.00 |
                        |Food             |     300.00 |
                        |""".trimMargin(),
                    """
                        |'General' Account Transactions
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:00 |   5,000.00 | income into $defaultCheckingAccountName
                        | 2. 2024-08-08 19:00:01 |     200.00 | income into $defaultWalletAccountName
                        | 3. 2024-08-08 19:00:02 |    -300.00 | allowance into $defaultFoodAccountName
                        | 4. 2024-08-08 19:00:03 |    -100.00 | allowance into $defaultNecessitiesAccountName
                        | 5. Back
                        | 6. Quit
                        |""".trimMargin(),
                    "Select transaction for details: ",
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
                )
            }
            "record spending" {
                inputs.addAll(listOf("3", "2", "1.5", "Pepsi", "", "3", "", "", "3"))
                unPause()
                waitForPause(helper.awaitMillis).shouldBeTrue()
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
                    """
                        |Select real account money was spent from.
                        | 1.   5,000.00 | Checking
                        | 2.     200.00 | Wallet
                        | 3. Back
                        | 4. Quit
                        |
                    """.trimMargin(),
                    "Enter selection: ",
                    "Enter the amount spent from Wallet [0.00, 200.00]: ",
                    "Enter description of transaction [spending]: ",
                    "Use current time [Y]? ",
                    """
                        |Select a category that some of that money was spent on.  Left to cover: $1.50
                        | 1.       0.00 | Education
                        | 2.       0.00 | Entertainment
                        | 3.     300.00 | Food
                        | 4.   4,800.00 | General
                        | 5.       0.00 | Medical
                        | 6.     100.00 | Necessities
                        | 7.       0.00 | Network
                        | 8.       0.00 | Transportation
                        | 9.       0.00 | Travel
                        |10.       0.00 | Work
                        |11. Back
                        |12. Quit
                        |""".trimMargin(),
                    "Enter selection: ",
                    "Enter the amount spent on Food [0.00, [1.50]]: ",
                    "Enter description for Food spend [Pepsi]: ",
                    """
                        |Select real account money was spent from.
                        | 1.   5,000.00 | Checking
                        | 2.     198.50 | Wallet
                        | 3. Back
                        | 4. Quit
                        |
                    """.trimMargin(),
                    "Enter selection: ",
                )
            }
            "write a check to SuperMarket" {
                inputs.addAll(
                    listOf("5", "1", "300", "SuperMarket", "", "3", "200", "", "6", "100", "", "3"),
                )
                unPause()
                waitForPause(helper.awaitMillis).shouldBeTrue()
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
                    """
            |Writing a check or using a credit card is slightly different from paying cash or using a debit card.
            |You will have a "drafts" account associated with each checking account or credit card.
            |When a check is written or credit card charged, the amount is transferred from the category accounts
            |(such as food or rent) to the "draft" account.
            |When the check clears or the credit card bill is paid, those transactions are cleared from the "draft" account.
            |""".trimMargin(),
                    """
                        |Select account the draft or charge was made on
                        | 1.   5,000.00 | Checking Drafts
                        | 2. Back
                        | 3. Quit
                        |
                    """.trimMargin(),
                    "Enter selection: ",
                    "Enter the amount of check or charge on Checking Drafts [0.00, 5000.00]: ",
                    "Enter description of recipient of draft or charge: ",
                    "Use current time [Y]? ",
                    """
                        |Select a category that some of that money was spent on.  Left to cover: $300.00
                        | 1.       0.00 | Education
                        | 2.       0.00 | Entertainment
                        | 3.     298.50 | Food
                        | 4.   4,800.00 | General
                        | 5.       0.00 | Medical
                        | 6.     100.00 | Necessities
                        | 7.       0.00 | Network
                        | 8.       0.00 | Transportation
                        | 9.       0.00 | Travel
                        |10.       0.00 | Work
                        |11. Back
                        |12. Quit
                        |""".trimMargin(),
                    "Enter selection: ",
                    "Enter the amount spent on Food [0.00, [298.50]]: ",
                    "Enter description for Food spend [SuperMarket]: ",
                    """
                        |Select a category that some of that money was spent on.  Left to cover: $100.00
                        | 1.       0.00 | Education
                        | 2.       0.00 | Entertainment
                        | 3.      98.50 | Food
                        | 4.   4,800.00 | General
                        | 5.       0.00 | Medical
                        | 6.     100.00 | Necessities
                        | 7.       0.00 | Network
                        | 8.       0.00 | Transportation
                        | 9.       0.00 | Travel
                        |10.       0.00 | Work
                        |11. Back
                        |12. Quit
                        |""".trimMargin(),
                    "Enter selection: ",
                    "Enter the amount spent on Necessities [0.00, [100.00]]: ",
                    "Enter description for Necessities spend [SuperMarket]: ",
                    """
                        |Select account the draft or charge was made on
                        | 1.   4,700.00 | Checking Drafts
                        | 2. Back
                        | 3. Quit
                        |
                    """.trimMargin(),
                    "Enter selection: ",
                )
            }
            "!ensure user can back out of a transaction without saving" {
            }
            "!check clears" {
                inputs.addAll(
                    listOf("6", "1", "300", "SuperMarket", "", "3", "200", "", "6", "100", "", "3"),
                )
                unPause()
                waitForPause(helper.awaitMillis).shouldBeTrue()
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
                )
            }
            "!spend using credit card" {
            }
            "!pay credit card balance" {
            }
            "!check balances in DB" {
            }
        }
    }
}

