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
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.NonNegativeSimpleEntryValidator
import bps.console.inputs.SimpleEntryValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import bps.console.menu.backItem
import bps.console.menu.pushMenu
import bps.console.menu.quitItem
import bps.console.menu.takeAction
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal
import kotlin.math.min

// FIXME this needs to be unique even among deactivated accounts
data class DistinctNameValidator(val existingAccounts: List<Account>) : SimpleEntryValidator {
    override val errorMessage: String = "Name must be unique"

    override fun invoke(input: String): Boolean =
        existingAccounts.none { account ->
            account.name == input
        }
}

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
                        validator = DistinctNameValidator(budgetData.categoryAccounts),
                    )
                        .getResult()
                        ?.trim()
                        ?: throw TryAgainAtMostRecentMenuException("Unique name for account not entered.")
                if (name.isNotBlank()) {
                    val description: String =
                        SimplePromptWithDefault(
                            "Enter a description for the new category: ",
                            inputReader = inputReader,
                            outPrinter = outPrinter,
                            defaultValue = name,
                        )
                            .getResult()
                            ?.trim()
                            ?: throw TryAgainAtMostRecentMenuException("Description for the new category not entered.")
                    budgetData.addCategoryAccount(CategoryAccount(name, description, budgetId = budgetData.id))
                    budgetDao.save(budgetData, user)
                    outPrinter.important("Category '$name' created")
                }
            },
        )
        add(
            pushMenu("Deactivate an Account") {
                Menu("What kind af account do you want to deactivate?") {
                    add(
                        pushMenu("Category Account") {
                            deleteCategoryAccountMenu(
                                budgetData,
                                budgetDao,
                                userConfig.numberOfItemsInScrollingList,
                                outPrinter,
                            )
                        },
                    )
                    add(
                        pushMenu("Real Account") {
                            deleteRealAccountMenu(
                                budgetData,
                                budgetDao,
                                userConfig.numberOfItemsInScrollingList,
                                outPrinter,
                            )
                        },
                    )
                    add(
                        pushMenu("Charge Account") {
                            deleteChargeAccountMenu(
                                budgetData,
                                budgetDao,
                                userConfig.numberOfItemsInScrollingList,
                                outPrinter,
                            )
                        },
                    )
                    add(
                        pushMenu("Draft Account") {
                            deleteDraftAccountMenu(
                                budgetData,
                                budgetDao,
                                userConfig.numberOfItemsInScrollingList,
                                outPrinter,
                            )
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
                        validator = DistinctNameValidator(budgetData.realAccounts),
                    )
                        .getResult()
                        ?.trim()
                        ?: throw TryAgainAtMostRecentMenuException("Description for the new account not entered.")
                if (name.isNotBlank()) {
                    val accountDescription: String =
                        SimplePromptWithDefault(
                            "Enter a description for the real account: ",
                            inputReader = inputReader,
                            outPrinter = outPrinter,
                            defaultValue = name,
                        )
                            .getResult()
                            ?.trim()
                            ?: throw TryAgainAtMostRecentMenuException("Description for the new account not entered.")
                    val isDraft: Boolean = SimplePromptWithDefault(
                        "Will you write checks on this account [y/N]? ",
                        inputReader = inputReader,
                        outPrinter = outPrinter,
                        defaultValue = false,
                    ) { it.trim() in listOf("Y", "y", "true", "yes") }
                        .getResult()
                        ?: throw TryAgainAtMostRecentMenuException("No decision made on whether you are going to write checks on this account.")
                    val balance: BigDecimal = SimplePromptWithDefault(
                        basicPrompt = "Initial balance on account [0.00]:  (This amount will be added to your General account as well.) ",
                        defaultValue = BigDecimal.ZERO.setScale(2),
                        additionalValidation = NonNegativeSimpleEntryValidator,
                        inputReader = inputReader,
                        outPrinter = outPrinter,
                    ) {
                        it.toCurrencyAmountOrNull()!!
                    }
                        .getResult()
                        ?: throw TryAgainAtMostRecentMenuException("Invalid account balance")
                    val realAccount = RealAccount(name, accountDescription, budgetId = budgetData.id)
                    budgetData.addRealAccount(realAccount)
                    if (isDraft)
                        budgetData.addDraftAccount(
                            DraftAccount(
                                name,
                                accountDescription,
                                realCompanion = realAccount,
                                budgetId = budgetData.id,
                            ),
                        )
                    try {
                        val incomeTransaction: Transaction? =
                            if (balance > BigDecimal.ZERO) {
                                val incomeDescription: String =
                                    SimplePromptWithDefault(
                                        "Enter description of income [initial balance in '${realAccount.name}']: ",
                                        defaultValue = "initial balance in '${realAccount.name}'",
                                        inputReader = inputReader,
                                        outPrinter = outPrinter,
                                    )
                                        .getResult()
                                        ?: throw TryAgainAtMostRecentMenuException("No description entered.")
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
                            outPrinter.important("Real account '${realAccount.name}' created with balance $$balance")
                        }
                    } catch (ex: Exception) {
                        budgetDao.save(budgetData, user)
                        outPrinter.important("Saved account '${realAccount.name}' with zero balance.")
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
                        validator = DistinctNameValidator(budgetData.chargeAccounts),
                    )
                        .getResult()
                        ?.trim()
                        ?: throw TryAgainAtMostRecentMenuException("No name entered.")
                if (name.isNotBlank()) {
                    val description: String =
                        SimplePromptWithDefault(
                            "Enter a description for the new credit card: ",
                            inputReader = inputReader,
                            outPrinter = outPrinter,
                            defaultValue = name,
                        )
                            .getResult()
                            ?.trim()
                            ?: throw TryAgainAtMostRecentMenuException("No description entered.")
                    budgetData.addChargeAccount(ChargeAccount(name, description, budgetId = budgetData.id))
                    budgetDao.save(budgetData, user)
                    outPrinter.important("New credit card account '$name' created")
                }
            },
        )
        // TODO https://github.com/benjishults/budget/issues/6
//        add(
//            pushMenu("Edit an Account") {
//                ScrollingSelectionMenu(
//                    header = "Select Account to Edit",
//                    limit = userConfig.numberOfItemsInScrollingList,
//                    labelGenerator = { String.format("%,10.2f | %-15s | %s", balance, name, description) },
//                    baseList = (budgetData.categoryAccounts - budgetData.generalAccount) + budgetData.realAccounts + budgetData.chargeAccounts,
//                ) { menuSession: MenuSession, account: Account -> }
//            },
//        )
        add(backItem)
        add(quitItem)
    }

fun deleteCategoryAccountMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deleteAccountMenu(
        budgetData,
        budgetDao,
        limit,
        outPrinter,
    ) {
        (budgetData.categoryAccounts - budgetData.generalAccount)
            .filter {
                it.balance == BigDecimal.ZERO.setScale(2)
            }
    }

fun deleteRealAccountMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deleteAccountMenu(
        budgetData,
        budgetDao,
        limit,
        outPrinter,
    ) {
        budgetData.realAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun deleteChargeAccountMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deleteAccountMenu(
        budgetData,
        budgetDao,
        limit,
        outPrinter,
    ) {
        budgetData.chargeAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun deleteDraftAccountMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deleteAccountMenu(
        budgetData,
        budgetDao,
        limit,
        outPrinter,
    ) {
        budgetData.draftAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun <T : Account> deleteAccountMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    limit: Int,
    outPrinter: OutPrinter,
    deleteFrom: () -> List<T>,
): Menu =
    ScrollingSelectionMenu(
        header = "Select account to deactivate",
        limit = limit,
        itemListGenerator = { lim, offset ->
            val baseList = deleteFrom()
            baseList.subList(
                offset,
                min(baseList.size, offset + lim),
            )
        },
        labelGenerator = { String.format("%,10.2f | %-15s | %s", balance, name, description) },
    ) { _: MenuSession, account: T ->
        budgetData.deleteAccount(account)
        budgetDao.deactivateAccount(account)
        outPrinter.important("Deactivated account '${account.name}'")
    }
