package bps.budget

import bps.budget.auth.AuthenticatedUser
import bps.budget.model.BudgetData
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.buildBudgetDao
import bps.budget.persistence.getBudgetNameFromPersistenceConfig
import bps.budget.persistence.loadOrBuildBudgetData
import bps.budget.ui.UiFacade
import bps.console.app.MenuApplicationWithQuit
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import kotlinx.datetime.Clock
import java.math.BigDecimal

const val recordIncomeLabel = "Record Income"
const val makeAllowancesLabel = "Make Allowances"
const val writeOrClearChecksLabel = "Write or Clear Checks"
const val useOrPayCreditCardsLabel = "Use or Pay Credit Cards"
const val transferLabel = "Transfer Money"
const val manageAccountsLabel = "Manage Accounts"
const val managePreferencesLabel = "Settings"
const val recordSpendingLabel = "Record Spending"
const val manageTransactionsLabel = "Manage Transactions"

class BudgetApplication private constructor(
    override val inputReader: InputReader,
    override val outPrinter: OutPrinter,
    uiFacade: UiFacade,
    val budgetDao: BudgetDao,
    clock: Clock,
    configurations: BudgetConfigurations,
) : AutoCloseable, WithIo {

    constructor(
        uiFacade: UiFacade,
        configurations: BudgetConfigurations,
        inputReader: InputReader = DefaultInputReader,
        outPrinter: OutPrinter = DefaultOutPrinter,
        clock: Clock = Clock.System,
    ) : this(
        inputReader,
        outPrinter,
        uiFacade,
        buildBudgetDao(configurations.persistence),
        clock,
        configurations,
    )

    init {
        budgetDao.prepForFirstLoad()
    }

    val authenticatedUser: AuthenticatedUser = uiFacade.login(budgetDao.userBudgetDao, configurations.user)
    val budgetData: BudgetData = loadOrBuildBudgetData(
        authenticatedUser = authenticatedUser,
        uiFacade = uiFacade,
        budgetDao = budgetDao,
        budgetName = getBudgetNameFromPersistenceConfig(configurations.persistence) ?: uiFacade.getBudgetName(),
    )

    private val menuApplicationWithQuit =
        MenuApplicationWithQuit(
            budgetMenu(budgetData, budgetDao, configurations.user, clock),
            inputReader,
            outPrinter,
        )

    fun run() {
        menuApplicationWithQuit.run()
    }

    override fun close() {
        if (!budgetData.validate())
            outPrinter.important("Budget Data was invalid on exit!")
        budgetDao.close()
        menuApplicationWithQuit.close()
    }

}
