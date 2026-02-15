@file:OptIn(ExperimentalUuidApi::class)

package bps.budget.account

import bps.budget.UserConfiguration
import bps.budget.budgetQuitItem
import bps.budget.consistency.commitTransactionConsistently
import bps.budget.consistency.createCategoryAccountConsistently
import bps.budget.consistency.createChargeAccountConsistently
import bps.budget.income.createInitialBalanceTransaction
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.model.toCurrencyAmountOrNull
import bps.budget.model.toDraftAccount
import bps.budget.model.toRealAccount
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AccountEntity
import bps.budget.persistence.TransactionDao
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.AcceptAnythingStringValidator
import bps.console.inputs.NonNegativeStringValidator
import bps.console.inputs.NotInListStringValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.io.OutPrinter
import bps.console.io.WithIo
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import bps.console.menu.backItem
import bps.console.menu.pushMenu
import bps.console.menu.takeAction
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import java.math.BigDecimal
import kotlin.math.min
import kotlin.uuid.ExperimentalUuidApi

fun WithIo.manageAccountsMenu(
    budgetData: BudgetData,
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    userConfig: UserConfiguration,
    clock: Clock,
) =
    Menu {
        add(
            takeAction({ "Create a New Category" }) {
                createCategory(budgetData, accountDao)
            },
        )
        add(
            takeAction({ "Create a Real Fund" }) {
                createRealFund(budgetData, accountDao, transactionDao, clock)
            },
        )
        add(
            takeAction({ "Add a Credit Card" }) {
                createCreditAccount(budgetData, accountDao)
            },
        )
        add(
            pushMenu({ "Edit Account Details" }) {
                editAccountDetails(budgetData, accountDao, userConfig)
            },
        )
        add(
            pushMenu({ "Deactivate an Account" }) {
                deactivateAccount(budgetData, accountDao, userConfig)
            },
        )
        add(backItem)
        add(budgetQuitItem)
    }

@Suppress("DefaultLocale")
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
        outPrinter.verticalSpace()
        if (SimplePrompt(
                basicPrompt = "Edit the name of account '${account.name}' [Y/n]? ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                validator = AcceptAnythingStringValidator,
                transformer = { it !in listOf("n", "N") },
            )
                .getResult()!!
        ) {
            outPrinter.verticalSpace()
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
                    basicPrompt = "\nRename '${account.name} to '$candidateName'.  Are you sure [y/N]? ",
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
        outPrinter.verticalSpace()
        if (SimplePrompt(
                basicPrompt = "Existing description: '${account.description}'.\nEdit the description of account '${account.name}' [Y/n]? ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                validator = AcceptAnythingStringValidator,
                transformer = { it !in listOf("n", "N") },
            )
                .getResult()!!
        ) {
            outPrinter.verticalSpace()
            val candidateDescription: String? = SimplePromptWithDefault(
                basicPrompt = "Enter the new DESCRIPTION for the account '${account.name}': ",
                defaultValue = account.description,
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
            if (candidateDescription !== null && SimplePromptWithDefault(
                    basicPrompt = "\nChange DESCRIPTION of '${account.name} from\n${account.description}\nto\n$candidateDescription\nAre you sure [y/N]? ",
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
        if (!accountDao.updateAccount(
                id = account.id,
                name = account.name,
                description = account.description,
                budgetId = account.budgetId,
            )
        ) {
            outPrinter.important("Unable to save changes... account not found.")
        } else {
            outPrinter.important("Editing done")
        }
    }

private fun WithIo.createCategory(
    budgetData: BudgetData,
    accountDao: AccountDao,
) {
    outPrinter.verticalSpace()
    val name: String =
        SimplePrompt<String>(
            basicPrompt = "Enter a unique name for the new category: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = NotInListStringValidator(
                accountDao.getAllAccountNamesForBudget(budgetData.id),
                "an existing account name",
            ),
        )
            .getResult()
            ?.trim()
            ?: throw TryAgainAtMostRecentMenuException("Unique name for account not entered.")
    if (name.isNotBlank()) {
        outPrinter.verticalSpace()
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
        createCategoryAccountConsistently(name, description, accountDao, budgetData)
            ?.let {
                outPrinter.important("Category '$name' created")
            }
            ?: outPrinter.important("Unable to save category account..")
    }
}

private fun WithIo.deactivateAccount(
    budgetData: BudgetData,
    accountDao: AccountDao,
    userConfig: UserConfiguration,
) = Menu({ "What kind af account do you want to deactivate?" }) {
    add(
        pushMenu({ "Category Account" }) {
            deactivateCategoryAccountMenu(
                budgetData,
                accountDao,
                userConfig.numberOfItemsInScrollingList,
                outPrinter,
            )
        },
    )
    add(
        pushMenu({ "Real Account" }) {
            deactivateRealAccountMenu(
                budgetData,
                accountDao,
                userConfig.numberOfItemsInScrollingList,
                outPrinter,
            )
        },
    )
    add(
        pushMenu({ "Charge Account" }) {
            deactivateChargeAccountMenu(
                budgetData,
                accountDao,
                userConfig.numberOfItemsInScrollingList,
                outPrinter,
            )
        },
    )
    add(
        pushMenu({ "Draft Account" }) {
            deactivateDraftAccountMenu(
                budgetData,
                accountDao,
                userConfig.numberOfItemsInScrollingList,
                outPrinter,
            )
        },
    )
    add(backItem)
    add(budgetQuitItem)
}

private fun WithIo.createCreditAccount(
    budgetData: BudgetData,
    accountDao: AccountDao,
) {
    outPrinter.verticalSpace()
    val name: String =
        SimplePrompt<String>(
            "Enter a unique name for the new credit card: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = NotInListStringValidator(
                accountDao.getAllAccountNamesForBudget(budgetData.id),
                "an existing account name",
            ),
        )
            .getResult()
            ?.trim()
            ?: throw TryAgainAtMostRecentMenuException("No name entered.")
    if (name.isNotBlank()) {
        outPrinter.verticalSpace()
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
        createChargeAccountConsistently(name, description, accountDao, budgetData)
            ?.let {
                outPrinter.important("New credit card account '$name' created")
            }
            ?: outPrinter.important("Unable to save account..")

    }
}

private fun WithIo.createRealFund(
    budgetData: BudgetData,
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    clock: Clock,
) {
    outPrinter.verticalSpace()
    val name: String =
        SimplePrompt<String>(
            "Enter a unique name for the real account: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = NotInListStringValidator(
                accountDao.getAllAccountNamesForBudget(budgetData.id),
                "an existing account name",
            ),
        )
            .getResult()
            ?.trim()
            ?: throw TryAgainAtMostRecentMenuException("Description for the new account not entered.")
    if (name.isNotBlank()) {
        outPrinter.verticalSpace()
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
        outPrinter.verticalSpace()
        val isDraft: Boolean = SimplePromptWithDefault(
            "Will you write checks on this account [y/N]? ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            defaultValue = false,
        ) { it.trim() in listOf("Y", "y", "true", "yes") }
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("No decision made on whether you are going to write checks on this account.")
        outPrinter.important(
            """
            |If this account came into existence due to some recent income, then make the initial balance $0.00 and then record that income into the account.
            |If this account has been there for some time and you are just now starting to track it in this program, then enter the initial balance below.""".trimMargin(),
        )
        val balance: BigDecimal = SimplePromptWithDefault(
            basicPrompt = "Initial balance on account [0.00]:  (This amount will be added to your General account as well.) ",
            defaultValue = BigDecimal.ZERO.setScale(2),
            additionalValidation = NonNegativeStringValidator,
            inputReader = inputReader,
            outPrinter = outPrinter,
        ) {
            it.toCurrencyAmountOrNull() ?: throw IllegalArgumentException("$it is an invalid account balance")
        }
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("Invalid account balance")
        createRealAccountConsistentlyWithIo(
            name,
            accountDescription,
            balance,
            isDraft,
            transactionDao,
            accountDao,
            budgetData,
            clock,
        )
    }
}

// TODO move the stuff that needs IO back to the call site
fun WithIo.createRealAccountConsistentlyWithIo(
    name: String,
    description: String,
    balance: BigDecimal,
    isDraft: Boolean,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    budgetData: BudgetData,
    clock: Clock,
): RealAccount? =
    try {
        if (isDraft) {
            accountDao
                .createRealAndDraftAccount(
                    name,
                    description,
                    budgetId = budgetData.id,
                )
                .let { (real: AccountEntity, draft: AccountEntity) ->
                    val realAccount = real.toRealAccount()!!
                    budgetData.addRealAccount(realAccount)
                    budgetData.addDraftAccount(draft.toDraftAccount { if (it == realAccount.id) realAccount else null }!!)
                    realAccount
                }
        } else {
            accountDao.createRealAccount(
                name,
                description,
                budgetId = budgetData.id,
            )
                .toRealAccount()!!
                .also {
                    budgetData.addRealAccount(it)
                }
        }
            .also { realAccount: RealAccount ->
                createAndSaveIncomeTransaction(balance, realAccount, budgetData, clock, transactionDao, accountDao)
            }
    } catch (_: Exception) {
        outPrinter.important("Unable to save real account.")
        null
    }

private fun WithIo.createAndSaveIncomeTransaction(
    balance: BigDecimal,
    realAccount: RealAccount,
    budgetData: BudgetData,
    clock: Clock,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
) {
    if (balance > BigDecimal.ZERO) {
        val incomeDescription: String =
            SimplePromptWithDefault(
                "Enter DESCRIPTION of income [initial balance in '${realAccount.name}']: ",
                defaultValue = "initial balance in '${realAccount.name}'",
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
            // NOTE I don't think this is possible when there's a default String value
                ?: throw Error("No description entered.")
        outPrinter("Enter timestamp for '$incomeDescription' transaction\n")
        val timestamp: Instant = getTimestampFromUser(
            timeZone = budgetData.timeZone,
            clock = clock,
        )
            ?.toInstant(budgetData.timeZone)
            ?: throw TryAgainAtMostRecentMenuException("No timestamp entered.")
        createInitialBalanceTransaction(
            incomeDescription,
            timestamp,
            balance,
            budgetData,
            realAccount,
        )
            .let { incomeTransaction: Transaction ->
                commitTransactionConsistently(incomeTransaction, transactionDao, accountDao, budgetData)
                outPrinter.important("Real account '${realAccount.name}' created with balance $$balance")
            }
    }
}

fun deactivateCategoryAccountMenu(
    budgetData: BudgetData,
    accountDao: AccountDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deactivateAccountMenu(
        budgetData,
        accountDao,
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
    accountDao: AccountDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deactivateAccountMenu(
        budgetData,
        accountDao,
        limit,
        outPrinter,
    ) {
        budgetData.realAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun deactivateChargeAccountMenu(
    budgetData: BudgetData,
    accountDao: AccountDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deactivateAccountMenu(
        budgetData,
        accountDao,
        limit,
        outPrinter,
    ) {
        budgetData.chargeAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun deactivateDraftAccountMenu(
    budgetData: BudgetData,
    accountDao: AccountDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deactivateAccountMenu(
        budgetData,
        accountDao,
        limit,
        outPrinter,
    ) {
        budgetData.draftAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

@Suppress("DefaultLocale")
fun <T : Account> deactivateAccountMenu(
    budgetData: BudgetData,
    accountDao: AccountDao,
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
        budgetData.deactivateAccount(account)
        accountDao.deactivateAccount(account.id)
        outPrinter.important("Deactivated account '${account.name}'")
    }
