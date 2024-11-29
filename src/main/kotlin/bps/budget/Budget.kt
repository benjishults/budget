@file:JvmName("Budget")

package bps.budget

import bps.budget.allowance.makeAllowancesSelectionMenu
import bps.budget.auth.AuthenticatedUser
import bps.budget.charge.creditCardMenu
import bps.budget.checking.checksMenu
import bps.budget.customize.manageAccountsMenu
import bps.budget.income.recordIncomeSelectionMenu
import bps.budget.model.BudgetData
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.persistence.buildBudgetDao
import bps.budget.persistence.getBudgetNameFromPersistenceConfig
import bps.budget.persistence.loadOrBuildBudgetData
import bps.budget.spend.recordSpendingMenu
import bps.budget.transaction.manageTransactions
import bps.budget.transfer.transferMenu
import bps.budget.ui.ConsoleUiFacade
import bps.budget.ui.UiFacade
import bps.config.convertToPath
import bps.console.app.MenuApplicationWithQuit
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import bps.console.menu.pushMenu
import bps.console.menu.quitItem
import bps.console.menu.takeActionAndPush
import kotlinx.datetime.Clock
import java.math.BigDecimal

fun main() {
    val configurations =
        BudgetConfigurations(sequenceOf("budget.yml", convertToPath("~/.config/bps-budget/budget.yml")))
    val uiFunctions = ConsoleUiFacade()

    BudgetApplication(
        uiFunctions,
        configurations,
        uiFunctions.inputReader,
        uiFunctions.outPrinter,
    )
        .use() {
            it.run()
        }
}

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
            budgetMenu(budgetData, budgetDao, configurations.user, authenticatedUser, clock),
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

fun WithIo.budgetMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    authenticatedUser: AuthenticatedUser,
    clock: Clock,
): Menu =
    Menu({ "Budget!" }) {
        add(
            takeActionAndPush(
                label = recordIncomeLabel,
                shortcut = "i",
                to = { recordIncomeSelectionMenu(budgetData, budgetDao.transactionDao, userConfig, clock) },
            ) {
                outPrinter.important(
                    """
            |Enter the real fund account into which the money is going (e.g., savings).
            |The same amount of money will be automatically entered into the '${budgetData.generalAccount.name}' account.
                    """.trimMargin(),
                )
            },
        )
        add(
            takeActionAndPush(
                label = makeAllowancesLabel,
                shortcut = "a",
                to = { makeAllowancesSelectionMenu(budgetData, budgetDao.transactionDao, userConfig, clock) },
            ) {
                outPrinter(
                    "Every month or so, you may want to distribute the income from the \"general\" category fund account into the other category fund accounts.\n",
                )
            },
        )
        add(
            pushMenu(recordSpendingLabel, "s") {
                recordSpendingMenu(budgetData, budgetDao.transactionDao, userConfig, clock)
            },
        )
        add(
            pushMenu(manageTransactionsLabel, "v") {
                manageTransactions(budgetData, budgetDao.transactionDao, budgetDao.accountDao, userConfig)
            },
        )
        add(
            pushMenu(writeOrClearChecksLabel, "ch") {
                checksMenu(budgetData, budgetDao.transactionDao, userConfig, clock)
            },
        )
        add(
            pushMenu(useOrPayCreditCardsLabel, "cr") {
                creditCardMenu(budgetData, budgetDao.transactionDao, userConfig, clock)
            },
        )
        add(
            pushMenu(transferLabel, "x") {
                transferMenu(budgetData, budgetDao.transactionDao, userConfig, clock)
            },
        )
        add(
            pushMenu(manageAccountsLabel, "m") {
                manageAccountsMenu(budgetData, budgetDao, userConfig, clock)
            },
        )
        add(quitItem)
    }

fun String.toCurrencyAmountOrNull(): BigDecimal? =
    try {
        BigDecimal(this).setScale(2)
    } catch (e: NumberFormatException) {
        null
    }

fun min(a: BigDecimal, b: BigDecimal): BigDecimal =
    if ((a <= b)) a else b
