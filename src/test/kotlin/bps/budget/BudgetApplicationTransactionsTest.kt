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
import io.kotest.matchers.collections.shouldContain
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
        System.setProperty("kotest.assertions.collection.print.size", "1000")
        System.setProperty("kotest.assertions.collection.enumerate.size", "1000")
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
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. View History
                            | 5. $writeOrClearChecks
                            | 6. $useOrPayCreditCards
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
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
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf("Enter the amount of income: "),
                    toInput = listOf("5000"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
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
                    ),
                    toInput = listOf("", "", "2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
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
                    ),
                    toInput = listOf("200", "", "", "3"),
                )
                application.budgetData.asClue { budgetData: BudgetData ->
                    budgetData.categoryAccounts shouldContain budgetData.generalAccount
                    budgetData.generalAccount.balance shouldBe BigDecimal(5200).setScale(2)
                    budgetData.categoryAccounts.size shouldBe 14
                }
                application.budgetDao.load(application.budgetData.id, userId).asClue { budgetData: BudgetData ->
                    budgetData.categoryAccounts shouldContain budgetData.generalAccount
                    budgetData.generalAccount.balance shouldBe BigDecimal(5200).setScale(2)
                    budgetData.categoryAccounts.size shouldBe 14
                }
            }
            "allocate to food and necessities" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. View History
                            | 5. $writeOrClearChecks
                            | 6. $useOrPayCreditCards
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Every month or so, you may want to distribute the income from the \"general\" category fund account into the other category fund accounts.\n",
                        "Select account to allocate money into from ${application.budgetData.generalAccount.name}: " + """
 1.       0.00 | Cosmetics
 2.       0.00 | Education
 3.       0.00 | Entertainment
 4.       0.00 | Food
 5.       0.00 | Hobby
 6.       0.00 | Home Upkeep
 7.       0.00 | Housing
 8.       0.00 | Medical
 9.       0.00 | Necessities
10.       0.00 | Network
11.       0.00 | Transportation
12.       0.00 | Travel
13.       0.00 | Work
14. Back
15. Quit
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount to allocate into ${defaultFoodAccountName} [0.00, 5200.00]: ",
                        "Enter description of transaction [allowance into $defaultFoodAccountName]: ",
                    ),
                    toInput = listOf("300", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Select account to allocate money into from ${application.budgetData.generalAccount.name}: " + """
 1.       0.00 | Cosmetics
 2.       0.00 | Education
 3.       0.00 | Entertainment
 4.     300.00 | Food
 5.       0.00 | Hobby
 6.       0.00 | Home Upkeep
 7.       0.00 | Housing
 8.       0.00 | Medical
 9.       0.00 | Necessities
10.       0.00 | Network
11.       0.00 | Transportation
12.       0.00 | Travel
13.       0.00 | Work
14. Back
15. Quit
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("9"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount to allocate into $defaultNecessitiesAccountName [0.00, 4900.00]: ",
                        "Enter description of transaction [allowance into $defaultNecessitiesAccountName]: ",
                        "Select account to allocate money into from ${application.budgetData.generalAccount.name}: " + """
 1.       0.00 | Cosmetics
 2.       0.00 | Education
 3.       0.00 | Entertainment
 4.     300.00 | Food
 5.       0.00 | Hobby
 6.       0.00 | Home Upkeep
 7.       0.00 | Housing
 8.       0.00 | Medical
 9.     200.00 | Necessities
10.       0.00 | Network
11.       0.00 | Transportation
12.       0.00 | Travel
13.       0.00 | Work
14. Back
15. Quit
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("200", "", "14"),
                )
            }
            "view transactions" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. $viewHistory
                            | 5. $writeOrClearChecks
                            | 6. $useOrPayCreditCards
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   4,700.00 | General
 2.       0.00 | Cosmetics
 3.       0.00 | Education
 4.       0.00 | Entertainment
 5.     300.00 | Food
 6.       0.00 | Hobby
 7.       0.00 | Home Upkeep
 8.       0.00 | Housing
 9.       0.00 | Medical
10.     200.00 | Necessities
11.       0.00 | Network
12.       0.00 | Transportation
13.       0.00 | Travel
14.       0.00 | Work
15.   5,000.00 | Checking
16.     200.00 | Wallet
17. Back
18. Quit
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |'General' Account Transactions
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:00 |   5,000.00 | income into $defaultCheckingAccountName
                        | 2. 2024-08-08 19:00:01 |     200.00 | income into $defaultWalletAccountName
                        | 3. 2024-08-08 19:00:02 |    -300.00 | allowance into $defaultFoodAccountName
                        | 4. 2024-08-08 19:00:03 |    -200.00 | allowance into $defaultNecessitiesAccountName
                        | 5. Back
                        | 6. Quit
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |2024-08-08 19:00:02
                        |allowance into Food
                        |Category Account | Amount     | Description
                        |Food             |     300.00 |
                        |General          |    -300.00 |
                        |""".trimMargin(),
                        """
                        |'General' Account Transactions
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:00 |   5,000.00 | income into $defaultCheckingAccountName
                        | 2. 2024-08-08 19:00:01 |     200.00 | income into $defaultWalletAccountName
                        | 3. 2024-08-08 19:00:02 |    -300.00 | allowance into $defaultFoodAccountName
                        | 4. 2024-08-08 19:00:03 |    -200.00 | allowance into $defaultNecessitiesAccountName
                        | 5. Back
                        | 6. Quit
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("5"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   4,700.00 | General
 2.       0.00 | Cosmetics
 3.       0.00 | Education
 4.       0.00 | Entertainment
 5.     300.00 | Food
 6.       0.00 | Hobby
 7.       0.00 | Home Upkeep
 8.       0.00 | Housing
 9.       0.00 | Medical
10.     200.00 | Necessities
11.       0.00 | Network
12.       0.00 | Transportation
13.       0.00 | Travel
14.       0.00 | Work
15.   5,000.00 | Checking
16.     200.00 | Wallet
17. Back
18. Quit
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("17"),
                )
            }
            "record spending" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. $viewHistory
                            | 5. $writeOrClearChecks
                            | 6. $useOrPayCreditCards
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select real account money was spent from.
                        | 1.   5,000.00 | Checking
                        | 2.     200.00 | Wallet
                        | 3. Back
                        | 4. Quit
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf("Enter the amount spent from Wallet [0.00, 200.00]: "),
                    toInput = listOf("1.5"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter description of transaction [spending]: ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("Pepsi", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select a category that some of that money was spent on.  Left to cover: $1.50
                        | 1.       0.00 | Cosmetics
                        | 2.       0.00 | Education
                        | 3.       0.00 | Entertainment
                        | 4.     300.00 | Food
                        | 5.       0.00 | Hobby
                        | 6.       0.00 | Home Upkeep
                        | 7.       0.00 | Housing
                        | 8.       0.00 | Medical
                        | 9.     200.00 | Necessities
                        |10.       0.00 | Network
                        |11.       0.00 | Transportation
                        |12.       0.00 | Travel
                        |13.       0.00 | Work
                        |14. Back
                        |15. Quit
                        |""".trimMargin(),
                        "Enter selection: ",
                        "Enter the amount spent on Food [0.00, [1.50]]: ",
                        "Enter description for Food spend [Pepsi]: ",
                    ),
                    toInput = listOf("4", "", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select real account money was spent from.
                        | 1.   5,000.00 | Checking
                        | 2.     198.50 | Wallet
                        | 3. Back
                        | 4. Quit
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("3"),
                )
            }
            "write a check to SuperMarket" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. $viewHistory
                            | 5. $writeOrClearChecks
                            | 6. $useOrPayCreditCards
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("5"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select the checking account
                        | 1.   5,000.00 | Checking Drafts
                        | 2. Back
                        | 3. Quit
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                         | 1. Write a check on Checking Drafts
                         | 2. Record check cleared
                         | 3. Back
                         | 4. Quit
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf("Enter the amount of check on Checking Drafts [0.00, 5000.00]: "),
                    toInput = listOf("300"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the recipient of the check: ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("SuperMarket", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select a category that some of that money was spent on.  Left to cover: $300.00
                        | 1.       0.00 | Cosmetics
                        | 2.       0.00 | Education
                        | 3.       0.00 | Entertainment
                        | 4.     298.50 | Food
                        | 5.       0.00 | Hobby
                        | 6.       0.00 | Home Upkeep
                        | 7.       0.00 | Housing
                        | 8.       0.00 | Medical
                        | 9.     200.00 | Necessities
                        |10.       0.00 | Network
                        |11.       0.00 | Transportation
                        |12.       0.00 | Travel
                        |13.       0.00 | Work
                        |14. Back
                        |15. Quit
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount spent on Food [0.00, [298.50]]: ",
                        "Enter description for Food spend [SuperMarket]: ",
                    ),
                    toInput = listOf("200", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select a category that some of that money was spent on.  Left to cover: $100.00
                        | 1.       0.00 | Cosmetics
                        | 2.       0.00 | Education
                        | 3.       0.00 | Entertainment
                        | 4.      98.50 | Food
                        | 5.       0.00 | Hobby
                        | 6.       0.00 | Home Upkeep
                        | 7.       0.00 | Housing
                        | 8.       0.00 | Medical
                        | 9.     200.00 | Necessities
                        |10.       0.00 | Network
                        |11.       0.00 | Transportation
                        |12.       0.00 | Travel
                        |13.       0.00 | Work
                        |14. Back
                        |15. Quit
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("9"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount spent on Necessities [0.00, [100.00]]: ",
                        "Enter description for Necessities spend [SuperMarket]: ",
                    ),
                    toInput = listOf("100", ""),
                )
            }
            "check clears" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                         | 1. Write a check on Checking Drafts
                         | 2. Record check cleared
                         | 3. Back
                         | 4. Quit
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select the check that cleared
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:05 |     300.00 | SuperMarket
                        | 2. Back
                        | 3. Quit
                        |""".trimMargin(),
                        "Select the check that cleared: ",
                        "Did the check clear just now [Y]? ",
                    ),
                    toInput = listOf("1", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select the check that cleared
                        |    Time Stamp          | Amount     | Description
                        | 1. Back
                        | 2. Quit
                        |""".trimMargin(),
                        "Select the check that cleared: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                         | 1. Write a check on Checking Drafts
                         | 2. Record check cleared
                         | 3. Back
                         | 4. Quit
                         |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select the checking account
                        | 1.   4,700.00 | Checking Drafts
                        | 2. Back
                        | 3. Quit
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
            }
            "check balances" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. $viewHistory
                            | 5. $writeOrClearChecks
                            | 6. $useOrPayCreditCards
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   4,700.00 | General
 2.       0.00 | Cosmetics
 3.       0.00 | Education
 4.       0.00 | Entertainment
 5.      98.50 | Food
 6.       0.00 | Hobby
 7.       0.00 | Home Upkeep
 8.       0.00 | Housing
 9.       0.00 | Medical
10.     100.00 | Necessities
11.       0.00 | Network
12.       0.00 | Transportation
13.       0.00 | Travel
14.       0.00 | Work
15.   4,700.00 | Checking
16.     198.50 | Wallet
17. Back
18. Quit
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("15"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |'Checking' Account Transactions
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:00 |   5,000.00 | income into $defaultCheckingAccountName
                        | 2. 2024-08-08 19:00:06 |    -300.00 | SuperMarket
                        | 3. Back
                        | 4. Quit
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |2024-08-08 19:00:06
                        |SuperMarket
                        |Category Account | Amount     | Description
                        |Food             |    -200.00 |
                        |Necessities      |    -100.00 |
                        |     Real Items: | Amount     | Description
                        |Checking         |    -300.00 | SuperMarket
                        |""".trimMargin(),
                        """
                        |'Checking' Account Transactions
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:00 |   5,000.00 | income into $defaultCheckingAccountName
                        | 2. 2024-08-08 19:00:06 |    -300.00 | SuperMarket
                        | 3. Back
                        | 4. Quit
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   4,700.00 | General
 2.       0.00 | Cosmetics
 3.       0.00 | Education
 4.       0.00 | Entertainment
 5.      98.50 | Food
 6.       0.00 | Hobby
 7.       0.00 | Home Upkeep
 8.       0.00 | Housing
 9.       0.00 | Medical
10.     100.00 | Necessities
11.       0.00 | Network
12.       0.00 | Transportation
13.       0.00 | Travel
14.       0.00 | Work
15.   4,700.00 | Checking
16.     198.50 | Wallet
17. Back
18. Quit
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("17"),
                )
            }
            "create a credit card account" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. $viewHistory
                            | 5. $writeOrClearChecks
                            | 6. $useOrPayCreditCards
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("8"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                         | 1. Create a New Category
                         | 2. Create a Real Fund
                         | 3. Add a Credit Card
                         | 4. Back
                         | 5. Quit
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter a unique name for the new credit card: ",
                        "Enter a description for the new credit card: ",
                    ),
                    toInput = listOf("Costco Visa", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                         | 1. Create a New Category
                         | 2. Create a Real Fund
                         | 3. Add a Credit Card
                         | 4. Back
                         | 5. Quit
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
            }
            "spend using credit card" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. $viewHistory
                            | 5. $writeOrClearChecks
                            | 6. $useOrPayCreditCards
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("6"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select a credit card
                        | 1.       0.00 | Costco Visa
                        | 2. Back
                        | 3. Quit
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        | 1. Record spending on Costco Visa
                        | 2. Pay Costco Visa bill
                        | 3. View unpaid transactions on Costco Visa
                        | 4. Back
                        | 5. Quit
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount of the charge on Costco Visa: ",
                        "Enter the recipient of the charge: ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("30", "Costco", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select a category that some of that money was spent on.  Left to cover: $30.00
                        | 1.       0.00 | Cosmetics
                        | 2.       0.00 | Education
                        | 3.       0.00 | Entertainment
                        | 4.      98.50 | Food
                        | 5.       0.00 | Hobby
                        | 6.       0.00 | Home Upkeep
                        | 7.       0.00 | Housing
                        | 8.       0.00 | Medical
                        | 9.     100.00 | Necessities
                        |10.       0.00 | Network
                        |11.       0.00 | Transportation
                        |12.       0.00 | Travel
                        |13.       0.00 | Work
                        |14. Back
                        |15. Quit
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount spent on Food [0.00, [30.00]]: ",
                        "Enter description for Food spend [Costco]: ",
                    ),
                    toInput = listOf("20", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select a category that some of that money was spent on.  Left to cover: $10.00
                        | 1.       0.00 | Cosmetics
                        | 2.       0.00 | Education
                        | 3.       0.00 | Entertainment
                        | 4.      78.50 | Food
                        | 5.       0.00 | Hobby
                        | 6.       0.00 | Home Upkeep
                        | 7.       0.00 | Housing
                        | 8.       0.00 | Medical
                        | 9.     100.00 | Necessities
                        |10.       0.00 | Network
                        |11.       0.00 | Transportation
                        |12.       0.00 | Travel
                        |13.       0.00 | Work
                        |14. Back
                        |15. Quit
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("9"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount spent on Necessities [0.00, [10.00]]: ",
                        "Enter description for Necessities spend [Costco]: ",
                    ),
                    toInput = listOf("10", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        | 1. Record spending on Costco Visa
                        | 2. Pay Costco Visa bill
                        | 3. View unpaid transactions on Costco Visa
                        | 4. Back
                        | 5. Quit
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount of the charge on Costco Visa: ",
                        "Enter the recipient of the charge: ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("20", "Target", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select a category that some of that money was spent on.  Left to cover: $20.00
                        | 1.       0.00 | Cosmetics
                        | 2.       0.00 | Education
                        | 3.       0.00 | Entertainment
                        | 4.      78.50 | Food
                        | 5.       0.00 | Hobby
                        | 6.       0.00 | Home Upkeep
                        | 7.       0.00 | Housing
                        | 8.       0.00 | Medical
                        | 9.      90.00 | Necessities
                        |10.       0.00 | Network
                        |11.       0.00 | Transportation
                        |12.       0.00 | Travel
                        |13.       0.00 | Work
                        |14. Back
                        |15. Quit
                        |""".trimMargin(),
                        "Enter selection: ",
                        "Enter the amount spent on Necessities [0.00, [20.00]]: ",
                        "Enter description for Necessities spend [Target]: ",
                    ),
                    toInput = listOf("9", "", ""),
                )
            }
            "!pay credit card balance" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        | 1. Record spending on Costco Visa
                        | 2. Pay Costco Visa bill
                        | 3. View unpaid transactions on Costco Visa
                        | 4. Back
                        | 5. Quit
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the total amount of the bill: ",
                        """
                        |Select real account bill was paid from
                        | 1.   4,700.00 | Checking
                        | 2.     198.50 | Wallet
                        | 3. Back
                        | 4. Quit
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                        "Use current time for the bill-pay transaction [Y]? ",
                        "Description of transaction [Costco Visa]: ",
                    ),
                    toInput = listOf("35", "1", "", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select all transactions from this bill.  Amount to be covered: $35.00
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:07 |     -30.00 | Costco
                        | 2. 2024-08-08 19:00:08 |     -20.00 | Target
                        | 3. Record a missing transaction from this bill
                        | 4. Back
                        | 5. Quit
                        |
                    """.trimMargin(),
                        "Select a transaction covered in this bill: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select all transactions from this bill.  Amount to be covered: $5.00
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:08 |     -20.00 | Target
                        | 2. Record a missing transaction from this bill
                        | 3. Back
                        | 4. Quit
                        |""".trimMargin(),
                        "Select a transaction covered in this bill: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "ERROR: this bill payment amount is not large enough to cover that transaction\n",
                        """
                        |Select all transactions from this bill.  Amount to be covered: $5.00
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:08 |     -20.00 | Target
                        | 2. Record a missing transaction from this bill
                        | 3. Back
                        | 4. Quit
                        |""".trimMargin(),
                        "Select a transaction covered in this bill: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount of the charge on Costco Visa: ",
                        "Enter the recipient of the charge: ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("5", "Brausen's", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select a category that some of that money was spent on.  Left to cover: $5.00
                        | 1.       0.00 | Cosmetics
                        | 2.       0.00 | Education
                        | 3.       0.00 | Entertainment
                        | 4.      78.50 | Food
                        | 5.       0.00 | Hobby
                        | 6.       0.00 | Home Upkeep
                        | 7.       0.00 | Housing
                        | 8.       0.00 | Medical
                        | 9.      70.00 | Necessities
                        |10.       0.00 | Network
                        |11.       0.00 | Transportation
                        |12.       0.00 | Travel
                        |13.       0.00 | Work
                        |14. Back
                        |15. Quit
                        |""".trimMargin(),
                        "Enter selection: ",
                        "Enter the amount spent on Necessities [0.00, [5.00]]: ",
                        "Enter description for Necessities spend [Brausen's]: ",
                    ),
                    toInput = listOf("9", "", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select all transactions from this bill.  Amount to be covered: $5.00
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:08 |     -20.00 | Target
                        | 2. 2024-08-08 19:00:10 |      -5.00 | Brausen's
                        | 3. Record a missing transaction from this bill
                        | 4. Back
                        | 5. Quit
                        |""".trimMargin(),
                        "Select a transaction covered in this bill: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Payment recorded!\n",
                        """
                        | 1. Record spending on Costco Visa
                        | 2. Pay Costco Visa bill
                        | 3. View unpaid transactions on Costco Visa
                        | 4. Back
                        | 5. Quit
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
            }
            "!check balances again" {
            }
            "!ensure user can back out of a transaction without saving" {
            }
            "!write a check to pay for credit card and see what we want the transaction to look like" {
            }
            "!if I view a transaction on food that was the result of a check (cleared or not) what's it look like?" {
            }
            "!if I view a transaction on food that was the result of a charge (cleared or not) what's it look like?" {
            }
            "!if I view a transaction on food that was the result of a charge paid by check (cleared or not) what's it look like?" {
            }
        }
    }
}

