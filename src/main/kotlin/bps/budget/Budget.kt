@file:JvmName("Budget")

package bps.budget

import bps.budget.allowance.makeAllowancesSelectionMenu
import bps.budget.auth.User
import bps.budget.charge.creditCardMenu
import bps.budget.checking.checksMenu
import bps.budget.customize.customizeMenu
import bps.budget.income.recordIncomeSelectionMenu
import bps.budget.model.BudgetData
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.persistence.buildBudgetDao
import bps.budget.persistence.getBudgetNameFromPersistenceConfig
import bps.budget.persistence.loadOrBuildBudgetData
import bps.budget.spend.recordSpendingMenu
import bps.budget.transaction.viewHistoryMenu
import bps.budget.ui.ConsoleUiFacade
import bps.budget.ui.UiFacade
import bps.config.convertToPath
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import bps.console.menu.MenuApplicationWithQuit
import bps.console.menu.pushMenu
import bps.console.menu.quitItem
import bps.console.menu.takeAction
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
    inputReader: InputReader,
    outPrinter: OutPrinter,
    uiFacade: UiFacade,
    val budgetDao: BudgetDao,
    clock: Clock,
    configurations: BudgetConfigurations,
) : AutoCloseable {

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

    val user: User = uiFacade.login(budgetDao, configurations.user)
    val budgetData: BudgetData = loadOrBuildBudgetData(
        user = user,
        uiFacade = uiFacade,
        budgetDao = budgetDao,
        budgetName = getBudgetNameFromPersistenceConfig(configurations.persistence) ?: uiFacade.getBudgetName(),
    )

    private val menuApplicationWithQuit =
        MenuApplicationWithQuit(
            WithIo(inputReader, outPrinter)
                .budgetMenu(budgetData, budgetDao, configurations.user, user, clock),
            inputReader,
            outPrinter,
        )

    fun run() {
        menuApplicationWithQuit.run()
    }

    override fun close() {
        budgetDao.save(budgetData, user)
        budgetDao.close()
        menuApplicationWithQuit.close()
    }

}

fun WithIo.budgetMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    user: User,
    clock: Clock,
): Menu =
    Menu("Budget!") {
        add(
            takeActionAndPush(
                label = recordIncome,
                shortcut = "i",
                to = { recordIncomeSelectionMenu(budgetData, budgetDao, userConfig, clock) },
            ) {
                outPrinter(
                    """
            |Enter the real fund account into which the money is going (e.g., savings).
            |The same amount of money will be automatically entered into the '${budgetData.generalAccount.name}' account.
            |""".trimMargin(),
                )
            },
        )
        add(
            takeActionAndPush(
                label = makeAllowances,
                shortcut = "a",
                to = { makeAllowancesSelectionMenu(budgetData, budgetDao, userConfig, clock) },
            ) {
                outPrinter(
                    "Every month or so, you may want to distribute the income from the \"general\" category fund account into the other category fund accounts.\n",
                )
            },
        )
        add(
            pushMenu(recordSpending, "s") {
                recordSpendingMenu(budgetData, budgetDao, userConfig, clock)
            },
        )
        add(
            pushMenu(viewHistory, "v") {
                viewHistoryMenu(budgetData, budgetDao, userConfig)
            },
        )
        add(
            pushMenu(writeOrClearChecks, "ch") {
                checksMenu(budgetData, budgetDao, userConfig, clock)
            },
        )
        add(
            pushMenu(useOrPayCreditCards, "cr") {
                creditCardMenu(budgetData, budgetDao, userConfig, clock)
            },
        )
        add(
            takeAction(transfer, "x") {
                outPrinter(
                    """
            |The user should be able to record transfers between read fund accounts
            |(e.g., a cash withdrawal is a transfer from savings to pocket) and transfers between category fund accounts
            |(e.g., when a big expenditure comes up under entertainment, you may need to transfer money from the school account.)""".trimMargin(),
                )
            },
        )
        add(pushMenu(setup, "m", { customizeMenu(budgetData, budgetDao, user, userConfig, clock) }))
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
