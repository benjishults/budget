package bps.budget.checking

import bps.budget.budgetQuitItem
import bps.console.io.WithIo
import bps.budget.consistency.clearCheckConsistently
import bps.budget.consistency.deleteTransactionConsistently
import bps.budget.model.BudgetData
import bps.budget.model.DraftAccount
import bps.budget.model.DraftStatus
import bps.budget.model.Transaction
import bps.budget.model.toCurrencyAmountOrNull
import bps.budget.persistence.AccountDao
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserConfiguration
import bps.budget.transaction.ViewTransactionsWithoutBalancesMenu
import bps.budget.transaction.allocateSpendingItemMenu
import bps.budget.transaction.showRecentRelevantTransactions
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.InRangeInclusiveStringValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import bps.console.menu.backItem
import bps.console.menu.pushMenu
import bps.console.menu.quitItem
import bps.console.menu.takeAction
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import java.math.BigDecimal

fun WithIo.checksMenu(
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu =
    ScrollingSelectionMenu(
        header = { "Select the checking account to work on" },
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.draftAccounts,
        labelGenerator = { String.format("%,10.2f | %s", realCompanion.balance - balance, name) },
    ) { menuSession, draftAccount: DraftAccount ->
        menuSession.push(
            Menu {
                add(
                    takeAction({ "Write a check on '${draftAccount.name}'" }) {
                        writeCheckOnAccount(transactionDao, draftAccount, budgetData, clock, menuSession, userConfig)
                    },
                )
                add(
                    pushMenu({ "Record check cleared on '${draftAccount.name}'" }) {
                        recordCheckClearedOnAccount(draftAccount, transactionDao, budgetData, userConfig, clock)
                    },
                )
                add(
                    pushMenu({ "Delete a check written on '${draftAccount.name}'" }) {
                        deleteCheckOnAccount(
                            transactionDao = transactionDao,
                            userConfig = userConfig,
                            accountDao = accountDao,
                            draftAccount = draftAccount,
                            budgetData = budgetData,
                        )
                    },
                )
                add(backItem)
                add(budgetQuitItem)
            },
        )
    }

fun WithIo.deleteCheckOnAccount(
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    draftAccount: DraftAccount,
    budgetData: BudgetData,
    userConfig: UserConfiguration,
) = ViewTransactionsWithoutBalancesMenu(
    filter = { transaction -> transaction.item.draftStatus === DraftStatus.outstanding },
    header = { "Select the check to DELETE on '${draftAccount.name}'" },
    prompt = { "Select the check to DELETE: " },
    account = draftAccount,
    transactionDao = transactionDao,
    budgetId = budgetData.id,
    accountIdToAccountMap = budgetData.accountIdToAccountMap,
    timeZone = budgetData.timeZone,
    limit = userConfig.numberOfItemsInScrollingList,
    outPrinter = outPrinter,
) { _, draftTransactionItem: TransactionDao.ExtendedTransactionItem<DraftAccount> ->
    outPrinter.verticalSpace()
    deleteTransactionConsistently(
        transactionItem = draftTransactionItem,
        transactionDao = transactionDao,
        accountDao = accountDao,
        budgetData = budgetData,
        promptInitial = "Are you sure you want to DELETE that check?",
        successMessage = "Check deleted",
    )
}

fun WithIo.recordCheckClearedOnAccount(
    draftAccount: DraftAccount,
    transactionDao: TransactionDao,
    budgetData: BudgetData,
    userConfig: UserConfiguration,
    clock: Clock,
) = ViewTransactionsWithoutBalancesMenu(
    filter = { transaction -> transaction.item.draftStatus === DraftStatus.outstanding },
    header = { "Select the check that CLEARED on '${draftAccount.name}'" },
    prompt = { "Select the check that CLEARED: " },
    account = draftAccount,
    transactionDao = transactionDao,
    budgetId = budgetData.id,
    accountIdToAccountMap = budgetData.accountIdToAccountMap,
    timeZone = budgetData.timeZone,
    limit = userConfig.numberOfItemsInScrollingList,
    outPrinter = outPrinter,
) { _, draftTransactionItem: TransactionDao.ExtendedTransactionItem<DraftAccount> ->
    outPrinter.verticalSpace()
    val timestamp: Instant =
        getTimestampFromUser(
            queryForNow = "Did the check clear just now [Y]? ",
            timeZone = budgetData.timeZone,
            clock = clock,
        )
            ?.toInstant(budgetData.timeZone)
            ?: throw TryAgainAtMostRecentMenuException("No timestamp entered.")
    clearCheckConsistently(draftTransactionItem, timestamp, draftAccount, transactionDao, budgetData)
    outPrinter.important("Cleared check recorded")
}

fun WithIo.writeCheckOnAccount(
    transactionDao: TransactionDao,
    draftAccount: DraftAccount,
    budgetData: BudgetData,
    clock: Clock,
    menuSession: MenuSession,
    userConfig: UserConfiguration,
) {
    outPrinter.verticalSpace()
    showRecentRelevantTransactions(
        transactionDao = transactionDao,
        account = draftAccount,
        budgetData = budgetData,
        label = "Recent checks:",
    )

    // TODO enter check number if checking account
    // NOTE this is why we have separate draft accounts -- to easily know the real vs draft balance
    val max = draftAccount.realCompanion.balance - draftAccount.balance
    val min = BigDecimal("0.01").setScale(2)
    outPrinter.verticalSpace()
    val amount: BigDecimal =
        SimplePrompt<BigDecimal>(
            "Enter the AMOUNT of check on '${draftAccount.name}' [$min, $max]: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = InRangeInclusiveStringValidator(min, max),
        ) {
            it.toCurrencyAmountOrNull()
                ?: throw IllegalArgumentException("$it is not a valid amount")
        }
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
    if (amount > BigDecimal.ZERO) {
        outPrinter.verticalSpace()
        val description: String =
            SimplePrompt<String>(
                "Enter the RECIPIENT of the check on '${draftAccount.name}': ",
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
                ?: throw TryAgainAtMostRecentMenuException("No description entered.")
        outPrinter.verticalSpace()
        val timestamp: Instant =
            getTimestampFromUser(
                timeZone = budgetData.timeZone,
                clock = clock,
            )
                ?.toInstant(budgetData.timeZone)
                ?: throw TryAgainAtMostRecentMenuException("No timestamp entered.")
        val transactionBuilder: Transaction.Builder =
            Transaction.Builder(
                description = description,
                timestamp = timestamp,
                type = Transaction.Type.expense,
            )
                .apply {
                    with(draftAccount) {
                        addItemBuilderTo(amount, description, DraftStatus.outstanding)
                    }
                }
        menuSession.push(
            allocateSpendingItemMenu(
                amount,
                transactionBuilder,
                description,
                budgetData,
                transactionDao,
                userConfig,
            ),
        )
    } else {
        outPrinter.important("Amount must be positive.")
    }
}
