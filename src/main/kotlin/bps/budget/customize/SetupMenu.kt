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
import bps.budget.persistence.AccountDao
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.AcceptAnythingStringValidator
import bps.console.inputs.NonNegativeStringValidator
import bps.console.inputs.NotInListStringValidator
import bps.console.inputs.StringValidator
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
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.min

data class DistinctNameValidator(val existingAccounts: List<Account>) : StringValidator {
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
                createCategory(budgetData, budgetDao, user)
            },
        )
        add(
            takeAction("Create a Real Fund") {
                createRealFund(budgetData, budgetDao, clock, user)
            },
        )
        add(
            takeAction("Add a Credit Card") {
                createCreditAccount(budgetData, budgetDao, user)
            },
        )
        add(
            pushMenu("Edit Account Details") {
                editAccountDetails(budgetData, budgetDao.accountDao, userConfig)
            },
        )
        add(
            pushMenu("Deactivate an Account") {
                deactivateAccount(budgetData, budgetDao, userConfig)
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

fun WithIo.editAccountDetails(
    budgetData: BudgetData,
    accountDao: AccountDao,
    userConfiguration: UserConfiguration,
): Menu =
    ScrollingSelectionMenu(
        header = { "Select an account to edit" },
        limit = userConfiguration.numberOfItemsInScrollingList,
        baseList = (budgetData.categoryAccounts - budgetData.generalAccount) + budgetData.realAccounts + budgetData.chargeAccounts + budgetData.generalAccount,
        labelGenerator = { String.format("%,10.2f | %-15s | %s", balance, name, description) },
    ) { _: MenuSession, account: Account ->
        if (SimplePrompt(
                basicPrompt = "Edit the name of account '${account.name}' [Y/n]? ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                validator = AcceptAnythingStringValidator,
                transformer = { it !in listOf("n", "N") },
            )
                .getResult()!!
        ) {
            val candidateName: String? = SimplePrompt<String>(
                basicPrompt = "Enter the new name for the account '${account.name}': ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                validator = NotInListStringValidator(
                    accountDao.getAllAccountNamesForBudget(budgetData.id),
                    "an existing account name",
                ),
            )
                .getResult()
            if (candidateName !== null && SimplePromptWithDefault(
                    basicPrompt = "Rename '${account.name} to '$candidateName'.  Are you sure [y/N]? ",
                    defaultValue = false,
                    inputReader = inputReader,
                    outPrinter = outPrinter,
                    transformer = { it in listOf("y", "Y") },
                )
                    .getResult()!!
            ) {
                account.name = candidateName
            }
        }
        if (SimplePrompt(
                basicPrompt = "Existing description: `${account.description}`.\nEdit the description of account '${account.name}' [Y/n]? ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                validator = AcceptAnythingStringValidator,
                transformer = { it !in listOf("n", "N") },
            )
                .getResult()!!
        ) {
            val candidateDescription: String? = SimplePromptWithDefault(
                basicPrompt = "Enter the new DESCRIPTION for the account '${account.name}': ",
                defaultValue = account.description,
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
            if (candidateDescription !== null && SimplePromptWithDefault(
                    basicPrompt = "Change DESCRIPTION of '${account.name} from\n${account.description}\nto\n$candidateDescription\nAre you sure [y/N]? ",
                    defaultValue = false,
                    inputReader = inputReader,
                    outPrinter = outPrinter,
                    transformer = { it in listOf("y", "Y") },
                )
                    .getResult()!!
            ) {
                account.description = candidateDescription
            }
        }
        if (!accountDao.updateAccount(account)) {
            outPrinter.important("Unable to save changes... account not found.")
        } else {
            outPrinter.important("Editing done")
        }
    }

private fun WithIo.createCategory(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    user: User,
) {
    val name: String =
        SimplePrompt<String>(
            "Enter a unique name for the new category: ",
            inputReader,
            outPrinter,
            validator = NotInListStringValidator(
                budgetDao.accountDao.getAllAccountNamesForBudget(budgetData.id),
                "an existing account name",
            ),
        )
            .getResult()
            ?.trim()
            ?: throw TryAgainAtMostRecentMenuException("Unique name for account not entered.")
    if (name.isNotBlank()) {
        val description: String =
            SimplePromptWithDefault(
                "Enter a DESCRIPTION for the new category: ",
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
}

private fun WithIo.deactivateAccount(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
) = Menu({ "What kind af account do you want to deactivate?" }) {
    add(
        pushMenu("Category Account") {
            deactivateCategoryAccountMenu(
                budgetData,
                budgetDao,
                userConfig.numberOfItemsInScrollingList,
                outPrinter,
            )
        },
    )
    add(
        pushMenu("Real Account") {
            deactivateRealAccountMenu(
                budgetData,
                budgetDao,
                userConfig.numberOfItemsInScrollingList,
                outPrinter,
            )
        },
    )
    add(
        pushMenu("Charge Account") {
            deactivateChargeAccountMenu(
                budgetData,
                budgetDao,
                userConfig.numberOfItemsInScrollingList,
                outPrinter,
            )
        },
    )
    add(
        pushMenu("Draft Account") {
            deactivateDraftAccountMenu(
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

private fun WithIo.createCreditAccount(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    user: User,
) {
    val name: String =
        SimplePrompt<String>(
            "Enter a unique name for the new credit card: ",
            inputReader,
            outPrinter,
            validator = NotInListStringValidator(
                budgetDao.accountDao.getAllAccountNamesForBudget(budgetData.id),
                "an existing account name",
            ),
        )
            .getResult()
            ?.trim()
            ?: throw TryAgainAtMostRecentMenuException("No name entered.")
    if (name.isNotBlank()) {
        val description: String =
            SimplePromptWithDefault(
                "Enter a DESCRIPTION for the new credit card: ",
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
}

private fun WithIo.createRealFund(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    clock: Clock,
    user: User,
) {
    val name: String =
        SimplePrompt<String>(
            "Enter a unique name for the real account: ",
            inputReader,
            outPrinter,
            validator = NotInListStringValidator(
                budgetDao.accountDao.getAllAccountNamesForBudget(budgetData.id),
                "an existing account name",
            ),
        )
            .getResult()
            ?.trim()
            ?: throw TryAgainAtMostRecentMenuException("Description for the new account not entered.")
    if (name.isNotBlank()) {
        val accountDescription: String =
            SimplePromptWithDefault(
                "Enter a DESCRIPTION for the real account: ",
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
            additionalValidation = NonNegativeStringValidator,
            inputReader = inputReader,
            outPrinter = outPrinter,
        ) {
            it.toCurrencyAmountOrNull() ?: throw TryAgainAtMostRecentMenuException("Invalid account balance")
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
                            "Enter DESCRIPTION of income [initial balance in '${realAccount.name}']: ",
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
                } else {
                    outPrinter.important("Balance must be positive.")
                    null
                }
            budgetDao.save(budgetData, user)
            if (incomeTransaction != null) {
                budgetData.commit(incomeTransaction)
                budgetDao.transactionDao.commit(incomeTransaction, budgetData.id)
                outPrinter.important("Real account '${realAccount.name}' created with balance $$balance")
            }
        } catch (ex: Exception) {
            budgetDao.save(budgetData, user)
            outPrinter.important("Saved account '${realAccount.name}' with zero balance.")
        }
    }
}

fun deactivateCategoryAccountMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deactivateAccountMenu(
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

fun deactivateRealAccountMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deactivateAccountMenu(
        budgetData,
        budgetDao,
        limit,
        outPrinter,
    ) {
        budgetData.realAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun deactivateChargeAccountMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deactivateAccountMenu(
        budgetData,
        budgetDao,
        limit,
        outPrinter,
    ) {
        budgetData.chargeAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun deactivateDraftAccountMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deactivateAccountMenu(
        budgetData,
        budgetDao,
        limit,
        outPrinter,
    ) {
        budgetData.draftAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun <T : Account> deactivateAccountMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    limit: Int,
    outPrinter: OutPrinter,
    deleteFrom: () -> List<T>,
): Menu =
    ScrollingSelectionMenu(
        header = { "Select account to deactivate" },
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
        budgetDao.accountDao.deactivateAccount(account)
        outPrinter.important("Deactivated account '${account.name}'")
    }
