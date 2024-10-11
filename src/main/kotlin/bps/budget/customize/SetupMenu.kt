package bps.budget.customize

import bps.budget.WithIo
import bps.budget.auth.User
import bps.budget.income.createIncomeTransaction
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.menu.Menu
import bps.console.menu.MenuSession
import bps.console.menu.ScrollingSelectionMenu
import bps.console.menu.backItem
import bps.console.menu.pushMenu
import bps.console.menu.quitItem
import bps.console.menu.takeAction
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal
import kotlin.math.min

fun WithIo.customizeMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    user: User,
    userConfig: UserConfiguration,
    clock: Clock,
) =
    Menu {
        add(
            takeAction("Create a New Category") {
                val name: String =
                    SimplePrompt<String>(
                        "Enter a unique name for the new category: ",
                        inputReader,
                        outPrinter,
                        validate = { input ->
                            budgetData
                                .categoryAccounts
                                .none { account ->
                                    account.name == input
                                }
                        },
                    )
                        .getResult()
                        .trim()
                if (name.isNotBlank()) {
                    val description: String =
                        SimplePromptWithDefault(
                            "Enter a description for the new category: ",
                            inputReader = inputReader,
                            outPrinter = outPrinter,
                            defaultValue = name,
                        )
                            .getResult()
                            .trim()
                    budgetData.addCategoryAccount(CategoryAccount(name, description))
                    budgetDao.save(budgetData, user)
                }
            },
        )
        add(
            pushMenu("Deactivate an Account") {
                Menu("What kind af account do you want to delete?") {
                    add(
                        pushMenu("Category Account") {
                            budgetData.deleteCategoryAccountMenu(userConfig)
                        },
                    )
                    add(
                        pushMenu("Real Account") {
                            budgetData.deleteRealAccountMenu(userConfig)
                        },
                    )
                    add(
                        pushMenu("Charge Account") {
                            budgetData.deleteChargeAccountMenu(userConfig)
                        },
                    )
                    add(
                        pushMenu("Draft Account") {
                            budgetData.deleteDraftAccountMenu(userConfig)
                        },
                    )
                    add(backItem)
                    add(quitItem)
                }
            },
        )
        add(
            takeAction("Create a Real Fund") {
                val name: String =
                    SimplePrompt<String>(
                        "Enter a unique name for the real account: ",
                        inputReader,
                        outPrinter,
                        validate = { input ->
                            budgetData
                                .realAccounts
                                .none { account ->
                                    account.name == input
                                }
                        },
                    )
                        .getResult()
                        .trim()
                if (name.isNotBlank()) {
                    val accountDescription: String =
                        SimplePromptWithDefault(
                            "Enter a description for the real account: ",
                            inputReader = inputReader,
                            outPrinter = outPrinter,
                            defaultValue = name,
                        )
                            .getResult()
                            .trim()
                    val isDraft: Boolean = SimplePromptWithDefault(
                        "Will you write checks on this account [y/N]? ",
                        inputReader = inputReader,
                        outPrinter = outPrinter,
                        defaultValue = false,
                    ) { it.trim() in listOf("Y", "y", "true", "yes") }
                        .getResult()
                    val balance: BigDecimal = SimplePromptWithDefault(
                        basicPrompt = "Initial balance on account [0.00]:  (This amount will be added to your General account as well.) ",
                        defaultValue = BigDecimal.ZERO.setScale(2),
                        additionalValidation = { !it.trim().startsWith("-") },
                        inputReader = inputReader,
                        outPrinter = outPrinter,
                    ) {
                        it.toCurrencyAmountOrNull()
                    }
                        .getResult()
                        ?: BigDecimal.ZERO.setScale(2)
                    val realAccount = RealAccount(name, accountDescription)
                    budgetData.addRealAccount(realAccount)
                    if (isDraft)
                        budgetData.addDraftAccount(
                            DraftAccount(
                                name,
                                accountDescription,
                                realCompanion = realAccount,
                            ),
                        )
                    try {
                        val incomeTransaction: Transaction? =
                            if (balance >= BigDecimal.ZERO.setScale(2)) {
                                val incomeDescription: String =
                                    SimplePromptWithDefault(
                                        "Enter description of income [initial balance in '${realAccount.name}']: ",
                                        defaultValue = "initial balance in '${realAccount.name}'",
                                        inputReader = inputReader,
                                        outPrinter = outPrinter,
                                    )
                                        .getResult()
                                outPrinter("Enter timestamp for '$incomeDescription' transaction\n")
                                val timestamp: Instant = getTimestampFromUser(
                                    timeZone = budgetData.timeZone,
                                    clock = clock,
                                )
                                createIncomeTransaction(
                                    incomeDescription,
                                    timestamp,
                                    balance,
                                    budgetData,
                                    realAccount,
                                )
                            } else
                                null
                        budgetDao.save(budgetData, user)
                        if (incomeTransaction != null) {
                            budgetData.commit(incomeTransaction)
                            budgetDao.commit(incomeTransaction, budgetData.id)
                        }
                    } catch (ex: Exception) {
                        budgetDao.save(budgetData, user)
                        outPrinter("\nSaved account with zero balance.\n\n")
                    }
                }
            },
        )
        add(
            takeAction("Add a Credit Card") {
                val name: String =
                    SimplePrompt<String>(
                        "Enter a unique name for the new credit card: ",
                        inputReader,
                        outPrinter,
                        validate = { input ->
                            budgetData
                                .chargeAccounts
                                .none { account ->
                                    account.name == input
                                }
                        },
                    )
                        .getResult()
                        .trim()
                if (name.isNotBlank()) {
                    val description: String =
                        SimplePromptWithDefault(
                            "Enter a description for the new credit card: ",
                            inputReader = inputReader,
                            outPrinter = outPrinter,
                            defaultValue = name,
                        )
                            .getResult()
                            .trim()
                    budgetData.addChargeAccount(ChargeAccount(name, description))
                    budgetDao.save(budgetData, user)
                }
            },
        )
        add(backItem)
        add(quitItem)
    }

fun BudgetData.deleteCategoryAccountMenu(userConfig: UserConfiguration): Menu =
    deleteAccountMenu(
        userConfig,
        deleter = { deleteCategoryAccount(it) },
    ) {
        (categoryAccounts - generalAccount).filter {
            it.balance == BigDecimal.ZERO.setScale(2)
        }
    }

fun BudgetData.deleteRealAccountMenu(userConfig: UserConfiguration): Menu =
    deleteAccountMenu(
        userConfig,
        deleter = { deleteRealAccount(it) },
    ) {
        realAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun BudgetData.deleteChargeAccountMenu(userConfig: UserConfiguration): Menu =
    deleteAccountMenu(
        userConfig,
        deleter = { deleteChargeAccount(it) },
    ) {
        chargeAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun BudgetData.deleteDraftAccountMenu(userConfig: UserConfiguration): Menu =
    deleteAccountMenu(
        userConfig,
        deleter = { deleteDraftAccount(it) },
    ) {
        draftAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun <T : Account> deleteAccountMenu(
    userConfig: UserConfiguration,
    deleter: (T) -> Unit,
    deleteFrom: () -> List<T>,
): Menu =
    ScrollingSelectionMenu(
        header = "Select account to delete",
        limit = userConfig.numberOfItemsInScrollingList,
        itemListGenerator = { limit, offset ->
            val baseList = deleteFrom()
            baseList.subList(
                offset,
                min(baseList.size, offset + limit),
            )
        },
        labelGenerator = { String.format("%,10.2f | %s", balance, name) },
    ) { _: MenuSession, selectedAccount: T ->
        deleter(selectedAccount)
    }
