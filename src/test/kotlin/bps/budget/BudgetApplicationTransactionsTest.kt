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
import kotlinx.datetime.TimeZone
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
            timeZone = TimeZone.of(configurations.user.defaultTimeZone!!),
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
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. View History (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |
                            |Enter the real fund account into which the money is going (e.g., savings).
                            |The same amount of money will be automatically entered into the 'General' account.
                            |
                            |""".trimMargin(),
                        """
                        |Select account receiving the INCOME:
                        | 1.       0.00 | Checking        | Account from which checks clear
                        | 2.       0.00 | Wallet          | Cash on hand
                        | 3. Back (b)
                        | 4. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf("Enter the amount of INCOME into 'Checking': "),
                    toInput = listOf("5000"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter description of income [income into '$defaultCheckingAccountName']: ",
                        "Use current time [Y]? ",
                        """
                            |
                            |Income recorded
                            |
                            |
                        """.trimMargin(),
                        """
                        |Select account receiving the INCOME:
                        | 1.   5,000.00 | Checking        | Account from which checks clear
                        | 2.       0.00 | Wallet          | Cash on hand
                        | 3. Back (b)
                        | 4. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("", "", "2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount of INCOME into 'Wallet': ",
                        "Enter description of income [income into '$defaultWalletAccountName']: ",
                        "Use current time [Y]? ",
                        """
                            |
                            |Income recorded
                            |
                            |
                        """.trimMargin(),
                        """
                                            |Select account receiving the INCOME:
                                            | 1.   5,000.00 | Checking        | Account from which checks clear
                                            | 2.     200.00 | Wallet          | Cash on hand
                                            | 3. Back (b)
                                            | 4. Quit (q)
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
            "delete Cosmetics account" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. View History (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                        """ 1. Create a New Category
 2. Create a Real Fund
 3. Add a Credit Card
 4. Edit Account Details
 5. Deactivate an Account
 6. Back (b)
 7. Quit (q)
""",
                        "Enter selection: ",
                        """What kind af account do you want to deactivate?
 1. Category Account
 2. Real Account
 3. Charge Account
 4. Draft Account
 5. Back (b)
 6. Quit (q)
""",
                        "Enter selection: ",
                        """Select account to deactivate
 1.       0.00 | Cosmetics       | Cosmetics, procedures, pampering, and accessories
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.       0.00 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.       0.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
10.       0.00 | Network         | Mobile plan, routers, internet access
11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
12.       0.00 | Travel          | Travel expenses for vacation
13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
14. Back (b)
15. Quit (q)
""",
                        "Enter selection: ",
                        """
Deactivated account 'Cosmetics'

""",
                        """Select account to deactivate
 1.       0.00 | Education       | Tuition, books, etc.
 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 3.       0.00 | Food            | Food other than what's covered in entertainment
 4.       0.00 | Hobby           | Expenses related to a hobby
 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 8.       0.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
 9.       0.00 | Network         | Mobile plan, routers, internet access
10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
11.       0.00 | Travel          | Travel expenses for vacation
12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
13. Back (b)
14. Quit (q)
""",
                        "Enter selection: ",
                        """What kind af account do you want to deactivate?
 1. Category Account
 2. Real Account
 3. Charge Account
 4. Draft Account
 5. Back (b)
 6. Quit (q)
""",
                        "Enter selection: ",
                        """ 1. Create a New Category
 2. Create a Real Fund
 3. Add a Credit Card
 4. Edit Account Details
 5. Deactivate an Account
 6. Back (b)
 7. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("8", "5", "1", "1", "13", "5", "b"),
                )
            }
            "allocate to food and necessities" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. View History (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Every month or so, you may want to distribute the income from the \"general\" category fund account into the other category fund accounts.\n",
                        "Select account to ALLOCATE money into from '${application.budgetData.generalAccount.name}' [$5,200.00]" + """
 1.       0.00 | Education       | Tuition, books, etc.
 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 3.       0.00 | Food            | Food other than what's covered in entertainment
 4.       0.00 | Hobby           | Expenses related to a hobby
 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 8.       0.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
 9.       0.00 | Network         | Mobile plan, routers, internet access
10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
11.       0.00 | Travel          | Travel expenses for vacation
12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
13. Back (b)
14. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount to ALLOCATE into '$defaultFoodAccountName' [0.01, 5200.00]: ",
                        "Enter description of transaction [allowance into '$defaultFoodAccountName']: ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("300", "", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Allowance recorded

""",
                        "Select account to ALLOCATE money into from '${application.budgetData.generalAccount.name}' [$4,900.00]" + """
 1.       0.00 | Education       | Tuition, books, etc.
 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 3.     300.00 | Food            | Food other than what's covered in entertainment
 4.       0.00 | Hobby           | Expenses related to a hobby
 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 8.       0.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
 9.       0.00 | Network         | Mobile plan, routers, internet access
10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
11.       0.00 | Travel          | Travel expenses for vacation
12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
13. Back (b)
14. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("8"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount to ALLOCATE into '$defaultNecessitiesAccountName' [0.01, 4900.00]: ",
                        "Enter description of transaction [allowance into '$defaultNecessitiesAccountName']: ",
                        "Use current time [Y]? ",
                        """
Allowance recorded

""",
                        "Select account to ALLOCATE money into from '${application.budgetData.generalAccount.name}' [$4,700.00]" + """
 1.       0.00 | Education       | Tuition, books, etc.
 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 3.     300.00 | Food            | Food other than what's covered in entertainment
 4.       0.00 | Hobby           | Expenses related to a hobby
 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 8.     200.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
 9.       0.00 | Network         | Mobile plan, routers, internet access
10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
11.       0.00 | Travel          | Travel expenses for vacation
12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
13. Back (b)
14. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("200", "", "", "13"),
                )
            }
            "view transactions" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   4,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.     300.00 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.     200.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
10.       0.00 | Network         | Mobile plan, routers, internet access
11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
12.       0.00 | Travel          | Travel expenses for vacation
13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
14.   5,000.00 | Checking        | Account from which checks clear
15.     200.00 | Wallet          | Cash on hand
16. Back (b)
17. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |'General' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:03 |    -200.00 |   4,700.00 | allowance into 'Necessities'
                        | 2. 2024-08-08 19:00:02 |    -300.00 |   4,900.00 | allowance into 'Food'
                        | 3. 2024-08-08 19:00:01 |     200.00 |   5,200.00 | income into 'Wallet'
                        | 4. 2024-08-08 19:00:00 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 5. Back (b)
                        | 6. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |2024-08-08 19:00:02
                        |allowance into 'Food'
                        |Category Account | Amount     | Description
                        |Food             |     300.00 |
                        |General          |    -300.00 |
                        |""".trimMargin(),
                        """
                        |'General' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:03 |    -200.00 |   4,700.00 | allowance into 'Necessities'
                        | 2. 2024-08-08 19:00:02 |    -300.00 |   4,900.00 | allowance into 'Food'
                        | 3. 2024-08-08 19:00:01 |     200.00 |   5,200.00 | income into 'Wallet'
                        | 4. 2024-08-08 19:00:00 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 5. Back (b)
                        | 6. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("5"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   4,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.     300.00 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.     200.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
10.       0.00 | Network         | Mobile plan, routers, internet access
11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
12.       0.00 | Travel          | Travel expenses for vacation
13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
14.   5,000.00 | Checking        | Account from which checks clear
15.     200.00 | Wallet          | Cash on hand
16. Back (b)
17. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("b"),
                )
            }
            "record spending" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the total amount spent: ",
                        "Enter description of transaction: ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("1.5", "Pepsi", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Spending $1.50 for 'Pepsi'
                        |Select an account that some of that money was spent from.  Left to cover: $1.50
                        | 1.   5,000.00 | Checking        | Account from which checks clear
                        | 2.     200.00 | Wallet          | Cash on hand
                        | 3. Back (b)
                        | 4. Quit (q)
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount spent from 'Wallet' for 'Pepsi' [0.01, [1.50]]: ",
                        "Enter description for 'Wallet' spend [Pepsi]: ",
                    ),
                    toInput = listOf("", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
All sources prepared

""",
                        """
                        |Spending from 'Wallet': 'Pepsi'
                        |Select a category that some of that money was spent on.  Left to cover: $1.50
                        | 1.       0.00 | Education       | Tuition, books, etc.
                        | 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
                        | 3.     300.00 | Food            | Food other than what's covered in entertainment
                        | 4.       0.00 | Hobby           | Expenses related to a hobby
                        | 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
                        | 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
                        | 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
                        | 8.     200.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
                        | 9.       0.00 | Network         | Mobile plan, routers, internet access
                        |10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
                        |11.       0.00 | Travel          | Travel expenses for vacation
                        |12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
                        |13. Back (b)
                        |14. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                        "Enter the amount spent on 'Food' for 'Pepsi' [0.01, [1.50]]: ",
                        "Enter description for 'Food' spend [Pepsi]: ",
                    ),
                    toInput = listOf("3", "", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Spending recorded

""",
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf(""),
                )
            }
            "write a check to SuperMarket" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("5"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select the checking account to work on
                        | 1.   5,000.00 | Checking Drafts
                        | 2. Back (b)
                        | 3. Quit (q)
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                         | 1. Write a check on 'Checking Drafts'
                         | 2. Record check cleared on 'Checking Drafts'
                         | 3. Back (b)
                         | 4. Quit (q)
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf("Enter the amount of check on 'Checking Drafts' [0.01, 5000.00]: "),
                    toInput = listOf("300"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the recipient of the check on 'Checking Drafts': ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("SuperMarket", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Spending from 'Checking Drafts': 'SuperMarket'
                        |Select a category that some of that money was spent on.  Left to cover: $300.00
                        | 1.       0.00 | Education       | Tuition, books, etc.
                        | 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
                        | 3.     298.50 | Food            | Food other than what's covered in entertainment
                        | 4.       0.00 | Hobby           | Expenses related to a hobby
                        | 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
                        | 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
                        | 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
                        | 8.     200.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
                        | 9.       0.00 | Network         | Mobile plan, routers, internet access
                        |10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
                        |11.       0.00 | Travel          | Travel expenses for vacation
                        |12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
                        |13. Back (b)
                        |14. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount spent on 'Food' for 'SuperMarket' [0.01, [298.50]]: ",
                        "Enter description for 'Food' spend [SuperMarket]: ",
                    ),
                    toInput = listOf("200", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Itemization prepared

""",
                        """
                        |Spending from 'Checking Drafts': 'SuperMarket'
                        |Select a category that some of that money was spent on.  Left to cover: $100.00
                        | 1.       0.00 | Education       | Tuition, books, etc.
                        | 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
                        | 3.      98.50 | Food            | Food other than what's covered in entertainment
                        | 4.       0.00 | Hobby           | Expenses related to a hobby
                        | 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
                        | 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
                        | 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
                        | 8.     200.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
                        | 9.       0.00 | Network         | Mobile plan, routers, internet access
                        |10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
                        |11.       0.00 | Travel          | Travel expenses for vacation
                        |12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
                        |13. Back (b)
                        |14. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("8"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount spent on 'Necessities' for 'SuperMarket' [0.01, [100.00]]: ",
                        "Enter description for 'Necessities' spend [SuperMarket]: ",
                    ),
                    toInput = listOf("100", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Spending recorded

""",
                        """
                         | 1. Write a check on 'Checking Drafts'
                         | 2. Record check cleared on 'Checking Drafts'
                         | 3. Back (b)
                         | 4. Quit (q)
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf(""),
                )
            }
            "check clears" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                         | 1. Write a check on 'Checking Drafts'
                         | 2. Record check cleared on 'Checking Drafts'
                         | 3. Back (b)
                         | 4. Quit (q)
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select the check that cleared on 'Checking Drafts'
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:05 |     300.00 | SuperMarket
                        | 2. Back (b)
                        | 3. Quit (q)
                        |""".trimMargin(),
                        "Select the check that cleared: ",
                        "Did the check clear just now [Y]? ",
                    ),
                    toInput = listOf("1", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select the check that cleared on 'Checking Drafts'
                        |    Time Stamp          | Amount     | Description
                        | 1. Back (b)
                        | 2. Quit (q)
                        |""".trimMargin(),
                        "Select the check that cleared: ",
                    ),
                    toInput = listOf("b"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                         | 1. Write a check on 'Checking Drafts'
                         | 2. Record check cleared on 'Checking Drafts'
                         | 3. Back (b)
                         | 4. Quit (q)
                         |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("b"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select the checking account to work on
                        | 1.   4,700.00 | Checking Drafts
                        | 2. Back (b)
                        | 3. Quit (q)
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
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   4,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.      98.50 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.     100.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
10.       0.00 | Network         | Mobile plan, routers, internet access
11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
12.       0.00 | Travel          | Travel expenses for vacation
13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
14.   4,700.00 | Checking        | Account from which checks clear
15.     198.50 | Wallet          | Cash on hand
16. Back (b)
17. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("14"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |'Checking' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:06 |    -300.00 |   4,700.00 | SuperMarket
                        | 2. 2024-08-08 19:00:00 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 3. Back (b)
                        | 4. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("1"),
                )
                // TODO https://github.com/benjishults/budget/issues/14
//                validateInteraction(
//                    expectedOutputs = listOf(
//                        """
//                        |2024-08-08 19:00:06
//                        |SuperMarket
//                        |Category Account | Amount     | Description
//                        |Food             |    -200.00 |
//                        |Necessities      |    -100.00 |
//                        |     Real Items: | Amount     | Description
//                        |Checking         |    -300.00 | SuperMarket
//                        |""".trimMargin(),
//                        """
//                        |'Checking' Account Transactions
//                        |    Time Stamp          | Amount     | Description
//                        | 1. 2024-08-08 19:00:00 |   5,000.00 | income into '$defaultCheckingAccountName'
//                        | 2. 2024-08-08 19:00:06 |    -300.00 | SuperMarket
//                        | 3. Back (b)
//                        | 4. Quit (q)
//                        |""".trimMargin(),
//                        "Select transaction for details: ",
//                    ),
//                    toInput = listOf("3"),
//                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |2024-08-08 19:00:06
                        |SuperMarket
                        |     Real Items: | Amount     | Description
                        |Checking         |    -300.00 | SuperMarket
                        |    Draft Items: | Amount     | Description
                        |Checking Drafts  |    -300.00 | SuperMarket
                        |""".trimMargin(),
                        """
                        |'Checking' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:06 |    -300.00 |   4,700.00 | SuperMarket
                        | 2. 2024-08-08 19:00:00 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 3. Back (b)
                        | 4. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   4,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.      98.50 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.     100.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
10.       0.00 | Network         | Mobile plan, routers, internet access
11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
12.       0.00 | Travel          | Travel expenses for vacation
13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
14.   4,700.00 | Checking        | Account from which checks clear
15.     198.50 | Wallet          | Cash on hand
16. Back (b)
17. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("16"),
                )
            }
            "create a credit card account" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
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
                         | 4. Edit Account Details
                         | 5. Deactivate an Account
                         | 6. Back (b)
                         | 7. Quit (q)
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
New credit card account 'Costco Visa' created

""",
                        """
                         | 1. Create a New Category
                         | 2. Create a Real Fund
                         | 3. Add a Credit Card
                         | 4. Edit Account Details
                         | 5. Deactivate an Account
                         | 6. Back (b)
                         | 7. Quit (q)
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("b"),
                )
            }
            "spend using credit card" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
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
                        | 2. Back (b)
                        | 3. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        | 1. Record spending on 'Costco Visa'
                        | 2. Pay 'Costco Visa' bill
                        | 3. View unpaid transactions on 'Costco Visa'
                        | 4. Back (b)
                        | 5. Quit (q)
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount of the charge on 'Costco Visa': ",
                        "Enter the recipient of the charge on 'Costco Visa': ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("30", "Costco", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Spending from 'Costco Visa': 'Costco'
                        |Select a category that some of that money was spent on.  Left to cover: $30.00
                        | 1.       0.00 | Education       | Tuition, books, etc.
                        | 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
                        | 3.      98.50 | Food            | Food other than what's covered in entertainment
                        | 4.       0.00 | Hobby           | Expenses related to a hobby
                        | 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
                        | 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
                        | 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
                        | 8.     100.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
                        | 9.       0.00 | Network         | Mobile plan, routers, internet access
                        |10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
                        |11.       0.00 | Travel          | Travel expenses for vacation
                        |12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
                        |13. Back (b)
                        |14. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount spent on 'Food' for 'Costco' [0.01, [30.00]]: ",
                        "Enter description for 'Food' spend [Costco]: ",
                    ),
                    toInput = listOf("20", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Itemization prepared

""",
                        """
                        |Spending from 'Costco Visa': 'Costco'
                        |Select a category that some of that money was spent on.  Left to cover: $10.00
                        | 1.       0.00 | Education       | Tuition, books, etc.
                        | 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
                        | 3.      78.50 | Food            | Food other than what's covered in entertainment
                        | 4.       0.00 | Hobby           | Expenses related to a hobby
                        | 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
                        | 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
                        | 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
                        | 8.     100.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
                        | 9.       0.00 | Network         | Mobile plan, routers, internet access
                        |10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
                        |11.       0.00 | Travel          | Travel expenses for vacation
                        |12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
                        |13. Back (b)
                        |14. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("8"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount spent on 'Necessities' for 'Costco' [0.01, [10.00]]: ",
                        "Enter description for 'Necessities' spend [Costco]: ",
                    ),
                    toInput = listOf("10", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Spending recorded

""",
                        """
                        | 1. Record spending on 'Costco Visa'
                        | 2. Pay 'Costco Visa' bill
                        | 3. View unpaid transactions on 'Costco Visa'
                        | 4. Back (b)
                        | 5. Quit (q)
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount of the charge on 'Costco Visa': ",
                        "Enter the recipient of the charge on 'Costco Visa': ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("20", "Target", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Spending from 'Costco Visa': 'Target'
                        |Select a category that some of that money was spent on.  Left to cover: $20.00
                        | 1.       0.00 | Education       | Tuition, books, etc.
                        | 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
                        | 3.      78.50 | Food            | Food other than what's covered in entertainment
                        | 4.       0.00 | Hobby           | Expenses related to a hobby
                        | 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
                        | 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
                        | 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
                        | 8.      90.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
                        | 9.       0.00 | Network         | Mobile plan, routers, internet access
                        |10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
                        |11.       0.00 | Travel          | Travel expenses for vacation
                        |12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
                        |13. Back (b)
                        |14. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                        "Enter the amount spent on 'Necessities' for 'Target' [0.01, [20.00]]: ",
                        "Enter description for 'Necessities' spend [Target]: ",
                    ),
                    toInput = listOf("8", "", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Spending recorded

""",
                        """
                        | 1. Record spending on 'Costco Visa'
                        | 2. Pay 'Costco Visa' bill
                        | 3. View unpaid transactions on 'Costco Visa'
                        | 4. Back (b)
                        | 5. Quit (q)
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select a credit card
                        | 1.     -50.00 | Costco Visa
                        | 2. Back (b)
                        | 3. Quit (q)
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
            }
            "check balances before paying credit card" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   4,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.      78.50 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.      70.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
10.       0.00 | Network         | Mobile plan, routers, internet access
11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
12.       0.00 | Travel          | Travel expenses for vacation
13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
14.   4,700.00 | Checking        | Account from which checks clear
15.     198.50 | Wallet          | Cash on hand
16.     -50.00 | Costco Visa     | Costco Visa
17. Back (b)
18. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |'Food' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:07 |     -20.00 |      78.50 | Costco
                        | 2. 2024-08-08 19:00:05 |    -200.00 |      98.50 | SuperMarket
                        | 3. 2024-08-08 19:00:04 |      -1.50 |     298.50 | Pepsi
                        | 4. 2024-08-08 19:00:02 |     300.00 |     300.00 | allowance into 'Food'
                        | 5. Back (b)
                        | 6. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |2024-08-08 19:00:07
                        |Costco
                        |Category Account | Amount     | Description
                        |Food             |     -20.00 |
                        |Necessities      |     -10.00 |
                        |Credit Card Items: | Amount     | Description
                        |Costco Visa      |     -30.00 | Costco
                        |""".trimMargin(),
                        """
                        |'$defaultFoodAccountName' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:07 |     -20.00 |      78.50 | Costco
                        | 2. 2024-08-08 19:00:05 |    -200.00 |      98.50 | SuperMarket
                        | 3. 2024-08-08 19:00:04 |      -1.50 |     298.50 | Pepsi
                        | 4. 2024-08-08 19:00:02 |     300.00 |     300.00 | allowance into 'Food'
                        | 5. Back (b)
                        | 6. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("5"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   4,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.      78.50 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.      70.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
10.       0.00 | Network         | Mobile plan, routers, internet access
11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
12.       0.00 | Travel          | Travel expenses for vacation
13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
14.   4,700.00 | Checking        | Account from which checks clear
15.     198.50 | Wallet          | Cash on hand
16.     -50.00 | Costco Visa     | Costco Visa
17. Back (b)
18. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("17"),
                )
            }
            "pay credit card balance" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("6"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select a credit card
                        | 1.     -50.00 | Costco Visa
                        | 2. Back (b)
                        | 3. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        | 1. Record spending on 'Costco Visa'
                        | 2. Pay 'Costco Visa' bill
                        | 3. View unpaid transactions on 'Costco Visa'
                        | 4. Back (b)
                        | 5. Quit (q)
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the total amount of the bill being paid on 'Costco Visa': ",
                        """
                        |Select real account bill on 'Costco Visa' was paid from
                        | 1.   4,700.00 | Checking
                        | 2.     198.50 | Wallet
                        | 3. Back (b)
                        | 4. Quit (q)
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                        "Use current time for the bill-pay transaction [Y]? ",
                        "Description of transaction [pay 'Costco Visa' bill]: ",
                    ),
                    toInput = listOf("35", "1", "", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select all transactions from this 'Costco Visa' bill.  Amount to be covered: $35.00
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:08 |     -20.00 | Target
                        | 2. 2024-08-08 19:00:07 |     -30.00 | Costco
                        | 3. Record a missing transaction from this 'Costco Visa' bill
                        | 4. Back (b)
                        | 5. Quit (q)
                        |
                    """.trimMargin(),
                        "Select a transaction covered in this bill: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Item prepared

""",
                        """
                        |Select all transactions from this 'Costco Visa' bill.  Amount to be covered: $5.00
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:08 |     -20.00 | Target
                        | 2. Record a missing transaction from this 'Costco Visa' bill
                        | 3. Back (b)
                        | 4. Quit (q)
                        |""".trimMargin(),
                        "Select a transaction covered in this bill: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |
                            |ERROR: this bill payment amount is not large enough to cover that transaction
                            |
                            |
""".trimMargin(),
                        """
                        |Select all transactions from this 'Costco Visa' bill.  Amount to be covered: $5.00
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:08 |     -20.00 | Target
                        | 2. Record a missing transaction from this 'Costco Visa' bill
                        | 3. Back (b)
                        | 4. Quit (q)
                        |""".trimMargin(),
                        "Select a transaction covered in this bill: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount of the charge on 'Costco Visa': ",
                        "Enter the recipient of the charge on 'Costco Visa': ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("5", "Brausen's", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Spending from 'Costco Visa': 'Brausen's'
                        |Select a category that some of that money was spent on.  Left to cover: $5.00
                        | 1.       0.00 | Education       | Tuition, books, etc.
                        | 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
                        | 3.      78.50 | Food            | Food other than what's covered in entertainment
                        | 4.       0.00 | Hobby           | Expenses related to a hobby
                        | 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
                        | 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
                        | 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
                        | 8.      70.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
                        | 9.       0.00 | Network         | Mobile plan, routers, internet access
                        |10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
                        |11.       0.00 | Travel          | Travel expenses for vacation
                        |12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
                        |13. Back (b)
                        |14. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                        "Enter the amount spent on 'Necessities' for 'Brausen's' [0.01, [5.00]]: ",
                        "Enter description for 'Necessities' spend [Brausen's]: ",
                    ),
                    toInput = listOf("8", "", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Spending recorded

""",
                        """
                        |Select all transactions from this 'Costco Visa' bill.  Amount to be covered: $5.00
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:10 |      -5.00 | Brausen's
                        | 2. 2024-08-08 19:00:08 |     -20.00 | Target
                        | 3. Record a missing transaction from this 'Costco Visa' bill
                        | 4. Back (b)
                        | 5. Quit (q)
                        |""".trimMargin(),
                        "Select a transaction covered in this bill: ",
                        """
                            |
                            |Payment recorded!
                            |
                            |""".trimMargin(),
                        """
                        | 1. Record spending on 'Costco Visa'
                        | 2. Pay 'Costco Visa' bill
                        | 3. View unpaid transactions on 'Costco Visa'
                        | 4. Back (b)
                        | 5. Quit (q)
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1", "b"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select a credit card
                        | 1.     -20.00 | Costco Visa
                        | 2. Back (b)
                        | 3. Quit (q)
                        |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
            }
            "check balances again" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   4,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.      78.50 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.      65.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
10.       0.00 | Network         | Mobile plan, routers, internet access
11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
12.       0.00 | Travel          | Travel expenses for vacation
13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
14.   4,665.00 | Checking        | Account from which checks clear
15.     198.50 | Wallet          | Cash on hand
16.     -20.00 | Costco Visa     | Costco Visa
17. Back (b)
18. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("14"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |'Checking' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:09 |     -35.00 |   4,665.00 | pay 'Costco Visa' bill
                        | 2. 2024-08-08 19:00:06 |    -300.00 |   4,700.00 | SuperMarket
                        | 3. 2024-08-08 19:00:00 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 4. Back (b)
                        | 5. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("2"),
                )
                // TODO https://github.com/benjishults/budget/issues/14
//                validateInteraction(
//                    expectedOutputs = listOf(
//                        """
//                        |2024-08-08 19:00:06
//                        |SuperMarket
//                        |Category Account | Amount     | Description
//                        |Food             |    -200.00 |
//                        |Necessities      |    -100.00 |
//                        |     Real Items: | Amount     | Description
//                        |Checking         |    -300.00 | SuperMarket
//                        |""".trimMargin(),
//                        """
//                        |'Checking' Account Transactions
//                        |    Time Stamp          | Amount     | Description
//                        | 1. 2024-08-08 19:00:00 |   5,000.00 | income into '$defaultCheckingAccountName'
//                        | 2. 2024-08-08 19:00:06 |    -300.00 | SuperMarket
//                        | 3. 2024-08-08 19:00:09 |     -35.00 | pay 'Costco Visa' bill
//                        | 4. Back (b)
//                        | 5. Quit (q)
//                        |""".trimMargin(),
//                        "Select transaction for details: ",
//                    ),
//                    toInput = listOf("4"),
//                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |2024-08-08 19:00:06
                        |SuperMarket
                        |     Real Items: | Amount     | Description
                        |Checking         |    -300.00 | SuperMarket
                        |    Draft Items: | Amount     | Description
                        |Checking Drafts  |    -300.00 | SuperMarket
                        |""".trimMargin(),
                        """
                        |'Checking' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:09 |     -35.00 |   4,665.00 | pay 'Costco Visa' bill
                        | 2. 2024-08-08 19:00:06 |    -300.00 |   4,700.00 | SuperMarket
                        | 3. 2024-08-08 19:00:00 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 4. Back (b)
                        | 5. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   4,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.      78.50 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.      65.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
10.       0.00 | Network         | Mobile plan, routers, internet access
11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
12.       0.00 | Travel          | Travel expenses for vacation
13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
14.   4,665.00 | Checking        | Account from which checks clear
15.     198.50 | Wallet          | Cash on hand
16.     -20.00 | Costco Visa     | Costco Visa
17. Back (b)
18. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("b"),
                )
            }
            "add a real account with a balance" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
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
                         | 4. Edit Account Details
                         | 5. Deactivate an Account
                         | 6. Back (b)
                         | 7. Quit (q)
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter a unique name for the real account: ",
                        "Enter a description for the real account: ",
                        "Will you write checks on this account [y/N]? ",
                        "Initial balance on account [0.00]:  (This amount will be added to your General account as well.) ",
                        "Enter description of income [initial balance in 'Savings']: ",
                        "Enter timestamp for 'initial balance in 'Savings'' transaction\n",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf(
                        "Savings",
                        "Savings account at My Bank",
                        "",
                        "1000",
                        "",
                        "",
                    ),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Real account 'Savings' created with balance ${'$'}1000.00

""",
                        """
                         | 1. Create a New Category
                         | 2. Create a Real Fund
                         | 3. Add a Credit Card
                         | 4. Edit Account Details
                         | 5. Deactivate an Account
                         | 6. Back (b)
                         | 7. Quit (q)
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("b"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   5,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.      78.50 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.      65.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
10.       0.00 | Network         | Mobile plan, routers, internet access
11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
12.       0.00 | Travel          | Travel expenses for vacation
13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
14.   4,665.00 | Checking        | Account from which checks clear
15.   1,000.00 | Savings         | Savings account at My Bank
16.     198.50 | Wallet          | Cash on hand
17.     -20.00 | Costco Visa     | Costco Visa
18. Back (b)
19. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |'General' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:11 |   1,000.00 |   5,700.00 | initial balance in 'Savings'
                        | 2. 2024-08-08 19:00:03 |    -200.00 |   4,700.00 | allowance into 'Necessities'
                        | 3. 2024-08-08 19:00:02 |    -300.00 |   4,900.00 | allowance into 'Food'
                        | 4. 2024-08-08 19:00:01 |     200.00 |   5,200.00 | income into 'Wallet'
                        | 5. 2024-08-08 19:00:00 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 6. Back (b)
                        | 7. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("6"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to view history
 1.   5,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.      78.50 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.      65.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
10.       0.00 | Network         | Mobile plan, routers, internet access
11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
12.       0.00 | Travel          | Travel expenses for vacation
13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
14.   4,665.00 | Checking        | Account from which checks clear
15.   1,000.00 | Savings         | Savings account at My Bank
16.     198.50 | Wallet          | Cash on hand
17.     -20.00 | Costco Visa     | Costco Visa
18. Back (b)
19. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("18"),
                )
            }
            "transfer from Savings to Checking" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. $viewHistory (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("x"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                |Select account to TRANSFER money FROM
                | 1.   4,665.00 | Checking        | Account from which checks clear
                | 2.   1,000.00 | Savings         | Savings account at My Bank
                | 3.     198.50 | Wallet          | Cash on hand
                | 4.       0.00 | Education       | Tuition, books, etc.
                | 5.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
                | 6.      78.50 | Food            | Food other than what's covered in entertainment
                | 7.       0.00 | Hobby           | Expenses related to a hobby
                | 8.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
                | 9.       0.00 | Housing         | Rent, mortgage, property tax, insurance
                |10.       0.00 | Medical         | Medicine, supplies, insurance, etc.
                |11.      65.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
                |12.       0.00 | Network         | Mobile plan, routers, internet access
                |13.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
                |14.       0.00 | Travel          | Travel expenses for vacation
                |15.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
                |16. Back (b)
                |17. Quit (q)
                |
            """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                |Select account to TRANSFER money TO (from 'Savings')
                | 1.   4,665.00 | Checking        | Account from which checks clear
                | 2.     198.50 | Wallet          | Cash on hand
                | 3. Back (b)
                | 4. Quit (q)
                |
            """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the amount to TRANSFER from 'Savings' into 'Checking' [0.01, 1000.00]: ",
                        "Enter description of transaction [transfer from 'Savings' into 'Checking']: ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("500", "", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Transfer recorded

""",
                        """
                |Select account to TRANSFER money TO (from 'Savings')
                | 1.   5,165.00 | Checking        | Account from which checks clear
                | 2.     198.50 | Wallet          | Cash on hand
                | 3. Back (b)
                | 4. Quit (q)
                |
            """.trimMargin(),
                        "Enter selection: ",
                        """
                |Select account to TRANSFER money FROM
                | 1.   5,165.00 | Checking        | Account from which checks clear
                | 2.     500.00 | Savings         | Savings account at My Bank
                | 3.     198.50 | Wallet          | Cash on hand
                | 4.       0.00 | Education       | Tuition, books, etc.
                | 5.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
                | 6.      78.50 | Food            | Food other than what's covered in entertainment
                | 7.       0.00 | Hobby           | Expenses related to a hobby
                | 8.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
                | 9.       0.00 | Housing         | Rent, mortgage, property tax, insurance
                |10.       0.00 | Medical         | Medicine, supplies, insurance, etc.
                |11.      65.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
                |12.       0.00 | Network         | Mobile plan, routers, internet access
                |13.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
                |14.       0.00 | Travel          | Travel expenses for vacation
                |15.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
                |16. Back (b)
                |17. Quit (q)
                |
            """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("b", "b"),
                )
            }
            "!ensure user can back out of a transaction without saving" - {
                "should be asked to confirm if transaction is in progress" {}
                "should NOT be asked to confirm if transaction is in progress" {}
                "credit card payment" {}
                "transfer" {}
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

