package bps.budget

import bps.budget.model.AuthenticatedUser
import bps.budget.model.BudgetData
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AnalyticsDao
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserBudgetDao
import bps.budget.persistence.jdbc.JdbcAccountDao
import bps.budget.persistence.jdbc.JdbcAnalyticsDao
import bps.budget.persistence.jdbc.JdbcTransactionDao
import bps.budget.persistence.jdbc.JdbcUserBudgetDao
import bps.budget.ui.UiFacade
import bps.console.app.MenuApplicationWithQuit
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.io.WithIo
import bps.jdbc.HikariYamlConfig
import bps.jdbc.JdbcConfig
import bps.jdbc.configureDataSource
import kotlinx.datetime.Clock
import javax.sql.DataSource
import kotlin.uuid.ExperimentalUuidApi

const val recordIncomeLabel = "Record Income"
const val makeAllowancesLabel = "Make Allowances"
const val writeOrClearChecksLabel = "Write or Clear Checks"
const val useOrPayCreditCardsLabel = "Use or Pay Credit Cards"
const val transferLabel = "Transfer Money"
const val manageAccountsLabel = "Manage Accounts"
const val recordSpendingLabel = "Record Spending"
const val manageTransactionsLabel = "Manage Transactions"
const val userSettingsLabel = "User Settings"

@OptIn(ExperimentalUuidApi::class)
class BudgetApplication private constructor(
    budgetName: String,
    userConfiguration: UserConfiguration,
    override val inputReader: InputReader,
    override val outPrinter: OutPrinter,
    uiFacade: UiFacade,
    val initializingBudgetDao: InitializingBudgetDao,
    val cliBudgetDao: CliBudgetDao,
    val accountDao: AccountDao,
    val transactionDao: TransactionDao,
    val analyticsDao: AnalyticsDao,
    val userBudgetDao: UserBudgetDao,
    clock: Clock,
) : AutoCloseable, WithIo {

//    constructor(
//        uiFacade: UiFacade,
//        configurations: BudgetConfigurations,
//        inputReader: InputReader = DefaultInputReader,
//        outPrinter: OutPrinter = DefaultOutPrinter,
//        clock: Clock = Clock.System,
//        dataSource: DataSource = configureDataSource(configurations.persistence.jdbc!!, configurations.hikari),
//    ) : this(
//        budgetName = configurations.budget.name,
//        userConfiguration = configurations.user,
//        uiFacade = uiFacade,
//        outPrinter = outPrinter,
//        inputReader = inputReader,
//        initializingBudgetDao = JdbcInitializingBudgetDao(
//            configurations.budget.name,
//            dataSource,
//        ),
//        cliBudgetDao = JdbcCliBudgetDao(configurations.budget.name, dataSource),
//        accountDao = JdbcAccountDao(dataSource),
//        transactionDao = JdbcTransactionDao(dataSource),
//        analyticsDao = JdbcAnalyticsDao(dataSource),
//        userBudgetDao = JdbcUserBudgetDao(dataSource),
//        clock = clock,
//    )

    constructor(
        uiFacade: UiFacade,
        budgetName: String,
        userConfiguration: UserConfiguration,
        jdbcConfig: JdbcConfig,
        hikari: HikariYamlConfig = HikariYamlConfig(),
        inputReader: InputReader = DefaultInputReader,
        outPrinter: OutPrinter = DefaultOutPrinter,
        clock: Clock = Clock.System,
        dataSource: DataSource = configureDataSource(jdbcConfig, hikari),
    ) : this(
        budgetName = budgetName,
        userConfiguration = userConfiguration,
        uiFacade = uiFacade,
        outPrinter = outPrinter,
        inputReader = inputReader,
        initializingBudgetDao = JdbcInitializingBudgetDao(
            budgetName,
            dataSource,
        ),
        cliBudgetDao = JdbcCliBudgetDao(budgetName, dataSource),
        accountDao = JdbcAccountDao(dataSource),
        transactionDao = JdbcTransactionDao(dataSource),
        analyticsDao = JdbcAnalyticsDao(dataSource),
        userBudgetDao = JdbcUserBudgetDao(dataSource),
        clock = clock,
    )

    init {
        initializingBudgetDao.ensureTablesAndIndexes()
    }

    val authenticatedUser: AuthenticatedUser = uiFacade.login(userBudgetDao, userConfiguration.defaultLogin)
    val budgetData: BudgetData = loadOrBuildBudgetData(
        authenticatedUser = authenticatedUser,
        uiFacade = uiFacade,
        initializingBudgetDao = initializingBudgetDao,
        cliBudgetDao = cliBudgetDao,
        accountDao = accountDao,
        budgetName = budgetName,
        clock = clock,
        userBudgetDao = userBudgetDao,
    )

    val menuApplicationWithQuit =
        MenuApplicationWithQuit(
            topLevelMenu = budgetMenu(
                budgetData = budgetData,
                accountDao = accountDao,
                transactionDao = transactionDao,
                analyticsDao = analyticsDao,
                userBudgetDao = userBudgetDao,
                userConfig = userConfiguration,
                userId = authenticatedUser.id,
                clock = clock,
            ),
            inputReader = inputReader,
            outPrinter = outPrinter,
        )

    fun run() {
        menuApplicationWithQuit.runApplication()
    }

    override fun close() {
        if (!budgetData.validate())
            outPrinter.important("Budget Data was invalid on exit!")
        initializingBudgetDao.close()
        menuApplicationWithQuit.close()
    }

}
