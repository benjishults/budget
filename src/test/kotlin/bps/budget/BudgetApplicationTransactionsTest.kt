package bps.budget

import bps.budget.auth.AuthenticatedUser
import bps.budget.jdbc.BasicAccountsJdbcTestFixture
import bps.budget.model.BudgetData
import bps.budget.model.defaultCheckingAccountName
import bps.budget.model.defaultFoodAccountName
import bps.budget.model.defaultMedicalAccountName
import bps.budget.model.defaultNecessitiesAccountName
import bps.budget.model.defaultWalletAccountName
import bps.budget.persistence.getBudgetNameFromPersistenceConfig
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.ui.ConsoleUiFacade
import bps.console.ComplexConsoleIoTestFixture
import bps.kotlin.WithMockClock
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.math.BigDecimal
import java.util.UUID

class BudgetApplicationTransactionsTest : FreeSpec(),
    BasicAccountsJdbcTestFixture,
    WithMockClock,
    // NOTE for debugging
//    ComplexConsoleIoTestFixture by ComplexConsoleIoTestFixture(90_000, true) {
    ComplexConsoleIoTestFixture by ComplexConsoleIoTestFixture(1500, true) {

    override val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)

    init {
        System.setProperty("kotest.assertions.collection.print.size", "1000")
        System.setProperty("kotest.assertions.collection.enumerate.size", "1000")

        val clock = produceSecondTickingClock(Instant.parse("2024-08-08T23:59:59.500Z"))

        val budgetId: UUID = UUID.fromString("89bc165a-ee70-43a4-b637-2774bcfc3ea4")
        val userId = UUID.fromString("f0f209c8-1b1e-43b3-8799-2dba58524d02")

        clearInputsAndOutputsBeforeEach()
        createBasicAccountsBeforeSpec(
            budgetId = budgetId,
            budgetName = getBudgetNameFromPersistenceConfig(configurations.persistence)!!,
            authenticatedUser = AuthenticatedUser(userId, configurations.user.defaultLogin!!),
            timeZone = TimeZone.of("America/Chicago"),
            clock = clock,
        )
        resetBalancesAndTransactionAfterSpec(budgetId)
        closeJdbcAfterSpec()
        stopApplicationAfterSpec()

        val uiFunctions = ConsoleUiFacade(inputReader, outPrinter)

        "run application with data from DB" - {
            val application = BudgetApplication(
                uiFunctions,
                configurations,
                inputReader,
                outPrinter,
                clock,
            )
            startApplicationForTesting(application.menuApplicationWithQuit)
            "record income" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
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
                        |Select account receiving the INCOME
                        |    Account         |    Balance |    Average |        Max |        Min | Description
                        |    Total Income    |        N/A |        N/A |        N/A |        N/A | Total Monthly Income
                        | 1. Checking        |       0.00 |        N/A |        N/A |        N/A | Account from which checks clear
                        | 2. Wallet          |       0.00 |        N/A |        N/A |        N/A | Cash on hand
                        | 3. Back (b)
                        | 4. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf("Enter the AMOUNT of INCOME into 'Checking': "),
                    toInput = listOf("5000"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter DESCRIPTION of income [income into '$defaultCheckingAccountName']: ",
                        "Use current time [Y]? ",
                        """
                            |
                            |Income recorded
                            |
                            |
                        """.trimMargin(),
                        """
                        |Select account receiving the INCOME
                        |    Account         |    Balance |    Average |        Max |        Min | Description
                        |    Total Income    |        N/A |        N/A |        N/A |        N/A | Total Monthly Income
                        | 1. Checking        |   5,000.00 |        N/A |        N/A |        N/A | Account from which checks clear
                        | 2. Wallet          |       0.00 |        N/A |        N/A |        N/A | Cash on hand
                        | 3. Back (b)
                        | 4. Quit (q)
                        |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("", "", "2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the AMOUNT of INCOME into 'Wallet': ",
                        "Enter DESCRIPTION of income [income into '$defaultWalletAccountName']: ",
                        "Use current time [Y]? ",
                        """
                            |
                            |Income recorded
                            |
                            |
                        """.trimMargin(),
                        """
                                            |Select account receiving the INCOME
                                            |    Account         |    Balance |    Average |        Max |        Min | Description
                                            |    Total Income    |        N/A |        N/A |        N/A |        N/A | Total Monthly Income
                                            | 1. Checking        |   5,000.00 |        N/A |        N/A |        N/A | Account from which checks clear
                                            | 2. Wallet          |     200.00 |        N/A |        N/A |        N/A | Cash on hand
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
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
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
            "allocate to food, necessities, and medical" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "\nEvery month or so, you may want to distribute the income from the \"general\" category fund account into the other category fund accounts.\n\n\n",
                        "Select account to ALLOCATE money into from '${application.budgetData.generalAccount.name}' [$5,200.00]" + """
    Account         |    Balance |    Average |        Max |        Min | Description
    Total Spend     |        N/A |        N/A |        N/A |        N/A | Total Monthly Expenditures
 1. Education       |       0.00 |        N/A |        N/A |        N/A | Tuition, books, etc.
 2. Entertainment   |       0.00 |        N/A |        N/A |        N/A | Games, books, subscriptions, going out for food or fun
 3. Food            |       0.00 |        N/A |        N/A |        N/A | Food other than what's covered in entertainment
 4. Hobby           |       0.00 |        N/A |        N/A |        N/A | Expenses related to a hobby
 5. Home Upkeep     |       0.00 |        N/A |        N/A |        N/A | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 6. Housing         |       0.00 |        N/A |        N/A |        N/A | Rent, mortgage, property tax, insurance
 7. Medical         |       0.00 |        N/A |        N/A |        N/A | Medicine, supplies, insurance, etc.
 8. Necessities     |       0.00 |        N/A |        N/A |        N/A | Energy, water, cleaning supplies, soap, tooth brushes, etc.
 9. Network         |       0.00 |        N/A |        N/A |        N/A | Mobile plan, routers, internet access
10. Transportation  |       0.00 |        N/A |        N/A |        N/A | Fares, vehicle payments, insurance, fuel, up-keep, etc.
11. Travel          |       0.00 |        N/A |        N/A |        N/A | Travel expenses for vacation
12. Work            |       0.00 |        N/A |        N/A |        N/A | Work-related expenses (possibly to be reimbursed)
13. Back (b)
14. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the AMOUNT to ALLOCATE into '$defaultFoodAccountName' [0.01, 5200.00]: ",
                        "Enter DESCRIPTION of transaction [allowance into '$defaultFoodAccountName']: ",
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
    Account         |    Balance |    Average |        Max |        Min | Description
    Total Spend     |        N/A |        N/A |        N/A |        N/A | Total Monthly Expenditures
 1. Education       |       0.00 |        N/A |        N/A |        N/A | Tuition, books, etc.
 2. Entertainment   |       0.00 |        N/A |        N/A |        N/A | Games, books, subscriptions, going out for food or fun
 3. Food            |     300.00 |        N/A |        N/A |        N/A | Food other than what's covered in entertainment
 4. Hobby           |       0.00 |        N/A |        N/A |        N/A | Expenses related to a hobby
 5. Home Upkeep     |       0.00 |        N/A |        N/A |        N/A | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 6. Housing         |       0.00 |        N/A |        N/A |        N/A | Rent, mortgage, property tax, insurance
 7. Medical         |       0.00 |        N/A |        N/A |        N/A | Medicine, supplies, insurance, etc.
 8. Necessities     |       0.00 |        N/A |        N/A |        N/A | Energy, water, cleaning supplies, soap, tooth brushes, etc.
 9. Network         |       0.00 |        N/A |        N/A |        N/A | Mobile plan, routers, internet access
10. Transportation  |       0.00 |        N/A |        N/A |        N/A | Fares, vehicle payments, insurance, fuel, up-keep, etc.
11. Travel          |       0.00 |        N/A |        N/A |        N/A | Travel expenses for vacation
12. Work            |       0.00 |        N/A |        N/A |        N/A | Work-related expenses (possibly to be reimbursed)
13. Back (b)
14. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("8"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the AMOUNT to ALLOCATE into '$defaultNecessitiesAccountName' [0.01, 4900.00]: ",
                        "Enter DESCRIPTION of transaction [allowance into '$defaultNecessitiesAccountName']: ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("200", "", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Allowance recorded

""",
                        "Select account to ALLOCATE money into from '${application.budgetData.generalAccount.name}' [$4,700.00]" + """
    Account         |    Balance |    Average |        Max |        Min | Description
    Total Spend     |        N/A |        N/A |        N/A |        N/A | Total Monthly Expenditures
 1. Education       |       0.00 |        N/A |        N/A |        N/A | Tuition, books, etc.
 2. Entertainment   |       0.00 |        N/A |        N/A |        N/A | Games, books, subscriptions, going out for food or fun
 3. Food            |     300.00 |        N/A |        N/A |        N/A | Food other than what's covered in entertainment
 4. Hobby           |       0.00 |        N/A |        N/A |        N/A | Expenses related to a hobby
 5. Home Upkeep     |       0.00 |        N/A |        N/A |        N/A | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 6. Housing         |       0.00 |        N/A |        N/A |        N/A | Rent, mortgage, property tax, insurance
 7. Medical         |       0.00 |        N/A |        N/A |        N/A | Medicine, supplies, insurance, etc.
 8. Necessities     |     200.00 |        N/A |        N/A |        N/A | Energy, water, cleaning supplies, soap, tooth brushes, etc.
 9. Network         |       0.00 |        N/A |        N/A |        N/A | Mobile plan, routers, internet access
10. Transportation  |       0.00 |        N/A |        N/A |        N/A | Fares, vehicle payments, insurance, fuel, up-keep, etc.
11. Travel          |       0.00 |        N/A |        N/A |        N/A | Travel expenses for vacation
12. Work            |       0.00 |        N/A |        N/A |        N/A | Work-related expenses (possibly to be reimbursed)
13. Back (b)
14. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("7"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the AMOUNT to ALLOCATE into '$defaultMedicalAccountName' [0.01, 4700.00]: ",
                        "Enter DESCRIPTION of transaction [allowance into '$defaultMedicalAccountName']: ",
                        "Use current time [Y]? ",
                        """
Allowance recorded

""",
                        """Select account to ALLOCATE money into from '${application.budgetData.generalAccount.name}' [$4,500.00]
    Account         |    Balance |    Average |        Max |        Min | Description
    Total Spend     |        N/A |        N/A |        N/A |        N/A | Total Monthly Expenditures
 1. Education       |       0.00 |        N/A |        N/A |        N/A | Tuition, books, etc.
 2. Entertainment   |       0.00 |        N/A |        N/A |        N/A | Games, books, subscriptions, going out for food or fun
 3. Food            |     300.00 |        N/A |        N/A |        N/A | Food other than what's covered in entertainment
 4. Hobby           |       0.00 |        N/A |        N/A |        N/A | Expenses related to a hobby
 5. Home Upkeep     |       0.00 |        N/A |        N/A |        N/A | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 6. Housing         |       0.00 |        N/A |        N/A |        N/A | Rent, mortgage, property tax, insurance
 7. Medical         |     200.00 |        N/A |        N/A |        N/A | Medicine, supplies, insurance, etc.
 8. Necessities     |     200.00 |        N/A |        N/A |        N/A | Energy, water, cleaning supplies, soap, tooth brushes, etc.
 9. Network         |       0.00 |        N/A |        N/A |        N/A | Mobile plan, routers, internet access
10. Transportation  |       0.00 |        N/A |        N/A |        N/A | Fares, vehicle payments, insurance, fuel, up-keep, etc.
11. Travel          |       0.00 |        N/A |        N/A |        N/A | Travel expenses for vacation
12. Work            |       0.00 |        N/A |        N/A |        N/A | Work-related expenses (possibly to be reimbursed)
13. Back (b)
14. Quit (q)
""",
                        "Enter selection: ",
                        "Recent allowances:\n",
                        "2024-08-08 19:00:04 |     300.00 | allowance into 'Food'\n",
                        "Enter the AMOUNT to ALLOCATE into '$defaultFoodAccountName' [0.01, 4500.00]: ",
                        """
                            |
                            |Amount must be between 0.01 and 4500.00
                            |
                            |
                        """.trimMargin(),
                        "Try again? [Y/n]: ",
                        """
                            |
                            |No amount entered.
                            |
                            |
                        """.trimMargin(),
                        """Select account to ALLOCATE money into from '${application.budgetData.generalAccount.name}' [$4,500.00]
    Account         |    Balance |    Average |        Max |        Min | Description
    Total Spend     |        N/A |        N/A |        N/A |        N/A | Total Monthly Expenditures
 1. Education       |       0.00 |        N/A |        N/A |        N/A | Tuition, books, etc.
 2. Entertainment   |       0.00 |        N/A |        N/A |        N/A | Games, books, subscriptions, going out for food or fun
 3. Food            |     300.00 |        N/A |        N/A |        N/A | Food other than what's covered in entertainment
 4. Hobby           |       0.00 |        N/A |        N/A |        N/A | Expenses related to a hobby
 5. Home Upkeep     |       0.00 |        N/A |        N/A |        N/A | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 6. Housing         |       0.00 |        N/A |        N/A |        N/A | Rent, mortgage, property tax, insurance
 7. Medical         |     200.00 |        N/A |        N/A |        N/A | Medicine, supplies, insurance, etc.
 8. Necessities     |     200.00 |        N/A |        N/A |        N/A | Energy, water, cleaning supplies, soap, tooth brushes, etc.
 9. Network         |       0.00 |        N/A |        N/A |        N/A | Mobile plan, routers, internet access
10. Transportation  |       0.00 |        N/A |        N/A |        N/A | Fares, vehicle payments, insurance, fuel, up-keep, etc.
11. Travel          |       0.00 |        N/A |        N/A |        N/A | Travel expenses for vacation
12. Work            |       0.00 |        N/A |        N/A |        N/A | Work-related expenses (possibly to be reimbursed)
13. Back (b)
14. Quit (q)
""",
                        "Enter selection: ",
                    ),
                    toInput = listOf("200", "", "", "3", "b", "n", "13"),
                )
            }
            "view transactions and delete medical allowance" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to manage transactions
 1.   4,500.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.     300.00 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.     200.00 | Medical         | Medicine, supplies, insurance, etc.
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
                        | 1. 2024-08-08 19:00:06 |    -200.00 |   4,500.00 | allowance into 'Medical'
                        | 2. 2024-08-08 19:00:05 |    -200.00 |   4,700.00 | allowance into 'Necessities'
                        | 3. 2024-08-08 19:00:04 |    -300.00 |   4,900.00 | allowance into 'Food'
                        | 4. 2024-08-08 19:00:02 |     200.00 |   5,200.00 | income into 'Wallet'
                        | 5. 2024-08-08 19:00:01 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 6. Delete a transaction (d)
                        | 7. Back (b)
                        | 8. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |2024-08-08 19:00:04
                        |allowance into 'Food'
                        |Category         | Amount     | Description
                        |Food             |     300.00 |
                        |General          |    -300.00 |
                        |""".trimMargin(),
                        """
                        |'General' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:06 |    -200.00 |   4,500.00 | allowance into 'Medical'
                        | 2. 2024-08-08 19:00:05 |    -200.00 |   4,700.00 | allowance into 'Necessities'
                        | 3. 2024-08-08 19:00:04 |    -300.00 |   4,900.00 | allowance into 'Food'
                        | 4. 2024-08-08 19:00:02 |     200.00 |   5,200.00 | income into 'Wallet'
                        | 5. 2024-08-08 19:00:01 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 6. Delete a transaction (d)
                        | 7. Back (b)
                        | 8. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("d"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Choose a transaction to DELETE
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:06 |    -200.00 |   4,500.00 | allowance into 'Medical'
                        | 2. 2024-08-08 19:00:05 |    -200.00 |   4,700.00 | allowance into 'Necessities'
                        | 3. 2024-08-08 19:00:04 |    -300.00 |   4,900.00 | allowance into 'Food'
                        | 4. 2024-08-08 19:00:02 |     200.00 |   5,200.00 | income into 'Wallet'
                        | 5. 2024-08-08 19:00:01 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 6. Back (b)
                        | 7. Quit (q)
                        |""".trimMargin(),
                        "Select a transaction to DELETE: ",
                        """2024-08-08 19:00:06
allowance into 'Medical'
Category         | Amount     | Description
General          |    -200.00 |
Medical          |     200.00 |
""",
                        "Are you sure you want to DELETE that transaction? [y/N]: ",
                        """
                            |
                            |Transaction deleted
                            |
                            |
                        """.trimMargin(),
                        """
                        |Choose a transaction to DELETE
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:05 |    -200.00 |       0.00 | allowance into 'Necessities'
                        | 2. 2024-08-08 19:00:04 |    -300.00 |     200.00 | allowance into 'Food'
                        | 3. 2024-08-08 19:00:02 |     200.00 |     500.00 | income into 'Wallet'
                        | 4. 2024-08-08 19:00:01 |   5,000.00 |     300.00 | income into 'Checking'
                        | 5. Back (b)
                        | 6. Quit (q)
                        |""".trimMargin(),
                        "Select a transaction to DELETE: ",
                        """
                            |'General' Account Transactions
                            |    Time Stamp          | Amount     | Balance    | Description
                            | 1. 2024-08-08 19:00:05 |    -200.00 |       0.00 | allowance into 'Necessities'
                            | 2. 2024-08-08 19:00:04 |    -300.00 |     200.00 | allowance into 'Food'
                            | 3. 2024-08-08 19:00:02 |     200.00 |     500.00 | income into 'Wallet'
                            | 4. 2024-08-08 19:00:01 |   5,000.00 |     300.00 | income into 'Checking'
                            | 5. Delete a transaction (d)
                            | 6. Back (b)
                            | 7. Quit (q)
                            |
                        """.trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("1", "y", "b", "b"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to manage transactions
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
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the total AMOUNT spent: ",
                        "Enter DESCRIPTION of transaction: ",
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
                        "Enter the AMOUNT spent from 'Wallet' for 'Pepsi' [0.01, [1.50]]: ",
                        "Enter DESCRIPTION for 'Wallet' spend [Pepsi]: ",
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
                        "Enter the AMOUNT spent on 'Food' for 'Pepsi' [0.01, [1.50]]: ",
                        "Enter DESCRIPTION for 'Food' spend [Pepsi]: ",
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
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf(""),
                )
            }
            "write a check to SuperMarket and another to delete" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("5"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select the checking account to work on
                        | 1.   5,000.00 | Checking
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
                         | 1. Write a check on 'Checking'
                         | 2. Record check cleared on 'Checking'
                         | 3. Delete a check written on 'Checking'
                         | 4. Back (b)
                         | 5. Quit (q)
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf("Enter the AMOUNT of check on 'Checking' [0.01, 5000.00]: "),
                    toInput = listOf("300"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the RECIPIENT of the check on 'Checking': ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("SuperMarket", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Spending from 'Checking': 'SuperMarket'
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
                        "Enter the AMOUNT spent on 'Food' for 'SuperMarket' [0.01, [298.50]]: ",
                        "Enter DESCRIPTION for 'Food' spend [SuperMarket]: ",
                    ),
                    toInput = listOf("200", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Itemization prepared

""",
                        """
                        |Spending from 'Checking': 'SuperMarket'
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
                        "Enter the AMOUNT spent on 'Necessities' for 'SuperMarket' [0.01, [100.00]]: ",
                        "Enter DESCRIPTION for 'Necessities' spend [SuperMarket]: ",
                    ),
                    toInput = listOf("100", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Spending recorded

""",
                        """
                         | 1. Write a check on 'Checking'
                         | 2. Record check cleared on 'Checking'
                         | 3. Delete a check written on 'Checking'
                         | 4. Back (b)
                         | 5. Quit (q)
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Recent checks:\n",
                        "2024-08-08 19:00:08 |     300.00 | SuperMarket\n",
                        "Enter the AMOUNT of check on 'Checking' [0.01, 4700.00]: ",
                    ),
                    toInput = listOf("25"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        "Enter the RECIPIENT of the check on 'Checking': ",
                        "Use current time [Y]? ",
                    ),
                    toInput = listOf("SuperMarket2", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Spending from 'Checking': 'SuperMarket2'
                        |Select a category that some of that money was spent on.  Left to cover: $25.00
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
                        "Enter the AMOUNT spent on 'Food' for 'SuperMarket2' [0.01, [25.00]]: ",
                        "Enter DESCRIPTION for 'Food' spend [SuperMarket2]: ",
                    ),
                    toInput = listOf("", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Spending recorded

""",
                        """
                         | 1. Write a check on 'Checking'
                         | 2. Record check cleared on 'Checking'
                         | 3. Delete a check written on 'Checking'
                         | 4. Back (b)
                         | 5. Quit (q)
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("3"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select the check to DELETE on 'Checking'
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:09 |      25.00 | SuperMarket2
                        | 2. 2024-08-08 19:00:08 |     300.00 | SuperMarket
                        | 3. Back (b)
                        | 4. Quit (q)
                        |""".trimMargin(),
                        "Select the check to DELETE: ",
                        """2024-08-08 19:00:09
SuperMarket2
Category         | Amount     | Description
Food             |     -25.00 |
Draft            | Amount     | Description
Checking         |      25.00 | SuperMarket2
""",
                        "Are you sure you want to DELETE that check? [y/N]: ",
                    ),
                    toInput = listOf("1", "y"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Check deleted

""",
                        """Select the check to DELETE on 'Checking'
    Time Stamp          | Amount     | Description
 1. 2024-08-08 19:00:08 |     300.00 | SuperMarket
 2. Back (b)
 3. Quit (q)
""",
                        "Select the check to DELETE: ",
                        """
                         | 1. Write a check on 'Checking'
                         | 2. Record check cleared on 'Checking'
                         | 3. Delete a check written on 'Checking'
                         | 4. Back (b)
                         | 5. Quit (q)
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("b", ""),
                )
            }
            "check clears" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                         | 1. Write a check on 'Checking'
                         | 2. Record check cleared on 'Checking'
                         | 3. Delete a check written on 'Checking'
                         | 4. Back (b)
                         | 5. Quit (q)
                         |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("2"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |Select the check that CLEARED on 'Checking'
                        |    Time Stamp          | Amount     | Description
                        | 1. 2024-08-08 19:00:08 |     300.00 | SuperMarket
                        | 2. Back (b)
                        | 3. Quit (q)
                        |""".trimMargin(),
                        "Select the check that CLEARED: ",
                        "Did the check clear just now [Y]? ",
                    ),
                    toInput = listOf("1", ""),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |
                            |Cleared check recorded
                            |
                            |
                        """.trimMargin(),
                        """
                        |Select the check that CLEARED on 'Checking'
                        |    Time Stamp          | Amount     | Description
                        | 1. Back (b)
                        | 2. Quit (q)
                        |""".trimMargin(),
                        "Select the check that CLEARED: ",
                    ),
                    toInput = listOf("b"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                         | 1. Write a check on 'Checking'
                         | 2. Record check cleared on 'Checking'
                         | 3. Delete a check written on 'Checking'
                         | 4. Back (b)
                         | 5. Quit (q)
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
                        | 1.   4,700.00 | Checking
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
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to manage transactions
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
                        | 1. 2024-08-08 19:00:10 |    -300.00 |   4,700.00 | SuperMarket
                        | 2. 2024-08-08 19:00:01 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 3. Delete a transaction (d)
                        | 4. Back (b)
                        | 5. Quit (q)
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
                        |2024-08-08 19:00:10
                        |SuperMarket
                        |Real             | Amount     | Description
                        |Checking         |    -300.00 | SuperMarket
                        |Draft            | Amount     | Description
                        |Checking         |    -300.00 | SuperMarket
                        |""".trimMargin(),
                        """
                        |'Checking' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:10 |    -300.00 |   4,700.00 | SuperMarket
                        | 2. 2024-08-08 19:00:01 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 3. Delete a transaction (d)
                        | 4. Back (b)
                        | 5. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("b"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to manage transactions
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
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
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
                        "Enter a DESCRIPTION for the new credit card: ",
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
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
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
                        "Enter the AMOUNT of the charge on 'Costco Visa': ",
                        "Enter the RECIPIENT of the charge on 'Costco Visa': ",
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
                        "Enter the AMOUNT spent on 'Food' for 'Costco' [0.01, [30.00]]: ",
                        "Enter DESCRIPTION for 'Food' spend [Costco]: ",
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
                        "Enter the AMOUNT spent on 'Necessities' for 'Costco' [0.01, [10.00]]: ",
                        "Enter DESCRIPTION for 'Necessities' spend [Costco]: ",
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
                        "Recent expenditures:\n",
                        "2024-08-08 19:00:11 |     -30.00 | Costco\n",
                        "Enter the AMOUNT of the charge on 'Costco Visa': ",
                        "Enter the RECIPIENT of the charge on 'Costco Visa': ",
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
                        "Recent expenditures:\n",
                        "2024-08-08 19:00:11 |     -10.00 | Costco\n",
                        "Enter the AMOUNT spent on 'Necessities' for 'Target' [0.01, [20.00]]: ",
                        "Enter DESCRIPTION for 'Necessities' spend [Target]: ",
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
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to manage transactions
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
                        | 1. 2024-08-08 19:00:11 |     -20.00 |      78.50 | Costco
                        | 2. 2024-08-08 19:00:08 |    -200.00 |      98.50 | SuperMarket
                        | 3. 2024-08-08 19:00:07 |      -1.50 |     298.50 | Pepsi
                        | 4. 2024-08-08 19:00:04 |     300.00 |     300.00 | allowance into 'Food'
                        | 5. Delete a transaction (d)
                        | 6. Back (b)
                        | 7. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("1"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                        |2024-08-08 19:00:11
                        |Costco
                        |Category         | Amount     | Description
                        |Food             |     -20.00 |
                        |Necessities      |     -10.00 |
                        |Credit Card      | Amount     | Description
                        |Costco Visa      |     -30.00 | Costco
                        |""".trimMargin(),
                        """
                        |'$defaultFoodAccountName' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:11 |     -20.00 |      78.50 | Costco
                        | 2. 2024-08-08 19:00:08 |    -200.00 |      98.50 | SuperMarket
                        | 3. 2024-08-08 19:00:07 |      -1.50 |     298.50 | Pepsi
                        | 4. 2024-08-08 19:00:04 |     300.00 |     300.00 | allowance into 'Food'
                        | 5. Delete a transaction (d)
                        | 6. Back (b)
                        | 7. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("b"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to manage transactions
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
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
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
                        "Enter the total AMOUNT of the bill being paid on 'Costco Visa': ",
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
                        | 1. 2024-08-08 19:00:12 |     -20.00 | Target
                        | 2. 2024-08-08 19:00:11 |     -30.00 | Costco
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
                        | 1. 2024-08-08 19:00:12 |     -20.00 | Target
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
                        | 1. 2024-08-08 19:00:12 |     -20.00 | Target
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
                        "Recent expenditures:\n",
                        "2024-08-08 19:00:12 |     -20.00 | Target\n",
                        "2024-08-08 19:00:11 |     -30.00 | Costco\n",
                        "Enter the AMOUNT of the charge on 'Costco Visa': ",
                        "Enter the RECIPIENT of the charge on 'Costco Visa': ",
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
                        "Recent expenditures:\n",
                        "2024-08-08 19:00:12 |     -20.00 | Target\n",
                        "2024-08-08 19:00:11 |     -10.00 | Costco\n",
                        "Enter the AMOUNT spent on 'Necessities' for 'Brausen's' [0.01, [5.00]]: ",
                        "Enter DESCRIPTION for 'Necessities' spend [Brausen's]: ",
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
                        | 1. 2024-08-08 19:00:14 |      -5.00 | Brausen's
                        | 2. 2024-08-08 19:00:12 |     -20.00 | Target
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
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to manage transactions
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
                        | 1. 2024-08-08 19:00:13 |     -35.00 |   4,665.00 | pay 'Costco Visa' bill
                        | 2. 2024-08-08 19:00:10 |    -300.00 |   4,700.00 | SuperMarket
                        | 3. 2024-08-08 19:00:01 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 4. Delete a transaction (d)
                        | 5. Back (b)
                        | 6. Quit (q)
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
//                        | Real            | Amount     | Description
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
                        |2024-08-08 19:00:10
                        |SuperMarket
                        |Real             | Amount     | Description
                        |Checking         |    -300.00 | SuperMarket
                        |Draft            | Amount     | Description
                        |Checking         |    -300.00 | SuperMarket
                        |""".trimMargin(),
                        """
                        |'Checking' Account Transactions
                        |    Time Stamp          | Amount     | Balance    | Description
                        | 1. 2024-08-08 19:00:13 |     -35.00 |   4,665.00 | pay 'Costco Visa' bill
                        | 2. 2024-08-08 19:00:10 |    -300.00 |   4,700.00 | SuperMarket
                        | 3. 2024-08-08 19:00:01 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 4. Delete a transaction (d)
                        | 5. Back (b)
                        | 6. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("b"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to manage transactions
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
            "add a real account with a balance and edit details" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
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
                        "Enter a DESCRIPTION for the real account: ",
                        "Will you write checks on this account [y/N]? ",

                        """
            |
            |If this account came into existence due to some recent income, then make the initial balance $0.00 and then record that income into the account.
            |If this account has been there for some time and you are just now starting to track it in this program, then enter the initial balance below.
            |
            |""".trimMargin(),

                        "Initial balance on account [0.00]:  (This amount will be added to your General account as well.) ",
                        "Enter DESCRIPTION of income [initial balance in 'Savings']: ",
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
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Select an account to edit
                            | 1.       0.00 | Education       | Tuition, books, etc.
                            | 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
                            | 3.      78.50 | Food            | Food other than what's covered in entertainment
                            | 4.       0.00 | Hobby           | Expenses related to a hobby
                            | 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
                            | 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
                            | 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
                            | 8.      65.00 | Necessities     | Energy, water, cleaning supplies, soap, tooth brushes, etc.
                            | 9.       0.00 | Network         | Mobile plan, routers, internet access
                            |10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
                            |11.       0.00 | Travel          | Travel expenses for vacation
                            |12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
                            |13.   4,665.00 | Checking        | Account from which checks clear
                            |14.   1,000.00 | Savings         | Savings account at My Bank
                            |15.     198.50 | Wallet          | Cash on hand
                            |16.     -20.00 | Costco Visa     | Costco Visa
                            |17.   5,700.00 | General         | Income is automatically deposited here and allowances are made from here
                            |18. Back (b)
                            |19. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                        "Edit the name of account 'Necessities' [Y/n]? ",
                        """Existing description: 'Energy, water, cleaning supplies, soap, tooth brushes, etc.'.
Edit the description of account 'Necessities' [Y/n]? """,
                        "Enter the new DESCRIPTION for the account 'Necessities': ",
                        """
                            |
                            |Change DESCRIPTION of 'Necessities from
                            |Energy, water, cleaning supplies, soap, tooth brushes, etc.
                            |to
                            |Cleaning supplies, soap, tooth brushes, etc.
                            |Are you sure [y/N]? """.trimMargin(),
                    ),
                    toInput = listOf("8", "n", "y", "Cleaning supplies, soap, tooth brushes, etc.", "y"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
Editing done

""",
                        """Select an account to edit
 1.       0.00 | Education       | Tuition, books, etc.
 2.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 3.      78.50 | Food            | Food other than what's covered in entertainment
 4.       0.00 | Hobby           | Expenses related to a hobby
 5.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 6.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 7.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 8.      65.00 | Necessities     | Cleaning supplies, soap, tooth brushes, etc.
 9.       0.00 | Network         | Mobile plan, routers, internet access
10.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
11.       0.00 | Travel          | Travel expenses for vacation
12.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
13.   4,665.00 | Checking        | Account from which checks clear
14.   1,000.00 | Savings         | Savings account at My Bank
15.     198.50 | Wallet          | Cash on hand
16.     -20.00 | Costco Visa     | Costco Visa
17.   5,700.00 | General         | Income is automatically deposited here and allowances are made from here
18. Back (b)
19. Quit (q)
""",
                        "Enter selection: ",
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
                    toInput = listOf("b", "b"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to manage transactions
 1.   5,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.      78.50 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.      65.00 | Necessities     | Cleaning supplies, soap, tooth brushes, etc.
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
                        | 1. 2024-08-08 19:00:15 |   1,000.00 |   5,700.00 | initial balance in 'Savings'
                        | 2. 2024-08-08 19:00:05 |    -200.00 |   4,700.00 | allowance into 'Necessities'
                        | 3. 2024-08-08 19:00:04 |    -300.00 |   4,900.00 | allowance into 'Food'
                        | 4. 2024-08-08 19:00:02 |     200.00 |   5,200.00 | income into 'Wallet'
                        | 5. 2024-08-08 19:00:01 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 6. Delete a transaction (d)
                        | 7. Back (b)
                        | 8. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                    ),
                    toInput = listOf("b"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to manage transactions
 1.   5,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.      78.50 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.      65.00 | Necessities     | Cleaning supplies, soap, tooth brushes, etc.
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
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
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
                |11.      65.00 | Necessities     | Cleaning supplies, soap, tooth brushes, etc.
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
                        "Recent transactions\n",
                        "2024-08-08 19:00:15 |   1,000.00 | initial balance in 'Savings'\n",
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
                        "Recent transactions\n",
                        "2024-08-08 19:00:13 |     -35.00 | pay 'Costco Visa' bill\n",
                        "2024-08-08 19:00:10 |    -300.00 | SuperMarket\n",
                        "2024-08-08 19:00:01 |   5,000.00 | income into 'Checking'\n",
                        "Enter the AMOUNT to TRANSFER from 'Savings' into 'Checking' [0.01, 1000.00]: ",
                        "Enter DESCRIPTION of transaction [transfer from 'Savings' into 'Checking']: ",
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
                |11.      65.00 | Necessities     | Cleaning supplies, soap, tooth brushes, etc.
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
            "change time-zone and observe timestamps presented an hour off" {
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. Record Income (i)
                            | 2. Make Allowances (a)
                            | 3. Record Spending (s)
                            | 4. Manage Transactions (t)
                            | 5. Write or Clear Checks (ch)
                            | 6. Use or Pay Credit Cards (cr)
                            | 7. Transfer Money (x)
                            | 8. Manage Accounts (m)
                            | 9. User Settings (u)
                            |10. Quit (q)
                            |
                        """.trimMargin(),
                        "Enter selection: ",
                        """
                       | 1. Change Time-Zone From America/Chicago
                       | 2. Change Analytics Start Date From 2024-09-01
                       | 3. Back (b)
                       | 4. Quit (q)
                       |
                    """.trimMargin(),
                        "Enter selection: ",
                        """
                |
                |The Time-Zone is the time-zone in which dates will be presented.
                |When entering a time-zone, it's best to use a format like "Europe/Paris" or "America/New_York" though some other
                |formats are accepted.
                |
                |
                        """.trimMargin(),
                        "Enter new desired time-zone for dates to be presented in [America/Chicago]: ",
                        """
                            |
                            |Time-Zone set to America/New_York
                            |
                            |
                        """.trimMargin(),
                        """
                       | 1. Change Time-Zone From America/New_York
                       | 2. Change Analytics Start Date From 2024-09-01
                       | 3. Back (b)
                       | 4. Quit (q)
                       |
                    """.trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("u", "1", "America/New_York", "b"),
                )
                // TODO look at transactions and see that the dates are displayed differently
                validateInteraction(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. $userSettingsLabel (u)
                            |10. Quit (q)
                            |""".trimMargin(),
                        "Enter selection: ",
                    ),
                    toInput = listOf("4"),
                )
                validateInteraction(
                    expectedOutputs = listOf(
                        """Select account to manage transactions
 1.   5,700.00 | General         | Income is automatically deposited here and allowances are made from here
 2.       0.00 | Education       | Tuition, books, etc.
 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
 4.      78.50 | Food            | Food other than what's covered in entertainment
 5.       0.00 | Hobby           | Expenses related to a hobby
 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
 9.      65.00 | Necessities     | Cleaning supplies, soap, tooth brushes, etc.
10.       0.00 | Network         | Mobile plan, routers, internet access
11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
12.       0.00 | Travel          | Travel expenses for vacation
13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
14.   5,165.00 | Checking        | Account from which checks clear
15.     500.00 | Savings         | Savings account at My Bank
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
                        | 1. 2024-08-08 20:00:15 |   1,000.00 |   5,700.00 | initial balance in 'Savings'
                        | 2. 2024-08-08 20:00:05 |    -200.00 |   4,700.00 | allowance into 'Necessities'
                        | 3. 2024-08-08 20:00:04 |    -300.00 |   4,900.00 | allowance into 'Food'
                        | 4. 2024-08-08 20:00:02 |     200.00 |   5,200.00 | income into 'Wallet'
                        | 5. 2024-08-08 20:00:01 |   5,000.00 |   5,000.00 | income into 'Checking'
                        | 6. Delete a transaction (d)
                        | 7. Back (b)
                        | 8. Quit (q)
                        |""".trimMargin(),
                        "Select transaction for details: ",
                        """|Select account to manage transactions
| 1.   5,700.00 | General         | Income is automatically deposited here and allowances are made from here
| 2.       0.00 | Education       | Tuition, books, etc.
| 3.       0.00 | Entertainment   | Games, books, subscriptions, going out for food or fun
| 4.      78.50 | Food            | Food other than what's covered in entertainment
| 5.       0.00 | Hobby           | Expenses related to a hobby
| 6.       0.00 | Home Upkeep     | Upkeep: association fees, furnace filters, appliances, repairs, lawn care
| 7.       0.00 | Housing         | Rent, mortgage, property tax, insurance
| 8.       0.00 | Medical         | Medicine, supplies, insurance, etc.
| 9.      65.00 | Necessities     | Cleaning supplies, soap, tooth brushes, etc.
|10.       0.00 | Network         | Mobile plan, routers, internet access
|11.       0.00 | Transportation  | Fares, vehicle payments, insurance, fuel, up-keep, etc.
|12.       0.00 | Travel          | Travel expenses for vacation
|13.       0.00 | Work            | Work-related expenses (possibly to be reimbursed)
|14.   5,165.00 | Checking        | Account from which checks clear
|15.     500.00 | Savings         | Savings account at My Bank
|16.     198.50 | Wallet          | Cash on hand
|17.     -20.00 | Costco Visa     | Costco Visa
|18. Back (b)
|19. Quit (q)
|""".trimMargin(),
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
            "test quitting" {
                validateFinalOutput(
                    expectedOutputs = listOf(
                        """
                            |Budget!
                            | 1. Record Income (i)
                            | 2. Make Allowances (a)
                            | 3. Record Spending (s)
                            | 4. Manage Transactions (t)
                            | 5. Write or Clear Checks (ch)
                            | 6. Use or Pay Credit Cards (cr)
                            | 7. Transfer Money (x)
                            | 8. Manage Accounts (m)
                            | 9. User Settings (u)
                            |10. Quit (q)
                            |
                        """.trimMargin(),
                        "Enter selection: ",
                        """
                            |
                            |Quitting
                            |
                            |Consider running the backup if you are storing the data locally.
                            |
                            |
                    """.trimMargin(),
                    ),
                    toInput = listOf("q"),
                )
            }
        }
    }
}

