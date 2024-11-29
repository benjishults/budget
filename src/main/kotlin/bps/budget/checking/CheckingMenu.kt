package bps.budget.checking

import bps.budget.WithIo
import bps.budget.model.BudgetData
import bps.budget.model.DraftAccount
import bps.budget.model.DraftStatus
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.budget.transaction.ViewTransactionsWithoutBalancesMenu
import bps.budget.transaction.allocateSpendingItemMenu
import bps.budget.transaction.showRecentRelevantTransactions
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
import java.math.BigDecimal

fun WithIo.checksMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
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
                    takeAction("Write a check on '${draftAccount.name}'") {
                        showRecentRelevantTransactions(
                            transactionDao = budgetDao.transactionDao,
                            account = draftAccount,
                            budgetData = budgetData,
                            label = "Recent checks:",
                        )

                        // TODO enter check number if checking account
                        // NOTE this is why we have separate draft accounts -- to easily know the real vs draft balance
                        val max = draftAccount.realCompanion.balance - draftAccount.balance
                        val min = BigDecimal("0.01").setScale(2)
                        val amount: BigDecimal =
                            SimplePromptWithDefault<BigDecimal>(
                                "Enter the AMOUNT of check on '${draftAccount.name}' [$min, $max]: ",
                                inputReader = inputReader,
                                outPrinter = outPrinter,
                                defaultValue = min,
                                additionalValidation = InRangeInclusiveStringValidator(min, max),
                            ) {
                                // NOTE for SimplePromptWithDefault, the first call to transform might fail.  If it
                                //    does, we want to apply the recovery action
                                it.toCurrencyAmountOrNull()
                                    ?: throw IllegalArgumentException("$it is not a valid amount")
                            }
                                .getResult()
                                ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
                        if (amount > BigDecimal.ZERO) {
                            val description: String =
                                SimplePrompt<String>(
                                    "Enter the RECIPIENT of the check on '${draftAccount.name}': ",
                                    inputReader = inputReader,
                                    outPrinter = outPrinter,
                                )
                                    .getResult()
                                    ?: throw TryAgainAtMostRecentMenuException("No description entered.")
                            val timestamp: Instant =
                                getTimestampFromUser(
                                    timeZone = budgetData.timeZone,
                                    clock = clock,
                                )
                            val transactionBuilder: Transaction.Builder =
                                Transaction.Builder(description, timestamp)
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
                                    budgetDao,
                                    userConfig,
                                ),
                            )
                        } else {
                            outPrinter.important("Amount must be positive.")
                        }
                    },
                )
                add(
                    pushMenu("Record check cleared on '${draftAccount.name}'") {
                        ViewTransactionsWithoutBalancesMenu(
                            filter = { transaction -> transaction.item.draftStatus === DraftStatus.outstanding },
                            header = { "Select the check that cleared on '${draftAccount.name}'" },
                            prompt = { "Select the check that cleared: " },
                            account = draftAccount,
                            budgetDao = budgetDao,
                            budgetId = budgetData.id,
                            accountIdToAccountMap = budgetData.accountIdToAccountMap,
                            timeZone = budgetData.timeZone,
                            limit = userConfig.numberOfItemsInScrollingList,
                            outPrinter = outPrinter,
                        ) { _, draftTransactionItem: TransactionDao.ExtendedTransactionItem<DraftAccount> ->
                            val timestamp: Instant =
                                getTimestampFromUser(
                                    "Did the check clear just now [Y]? ",
                                    budgetData.timeZone,
                                    clock,
                                )
                            val clearingTransaction =
                                Transaction.Builder(
                                    draftTransactionItem.transactionDescription,
                                    timestamp,
                                )
                                    .apply {
                                        with(draftAccount) {
                                            addItemBuilderTo(
                                                -draftTransactionItem.amount,
                                                this@apply.description,
                                                DraftStatus.clearing,
                                            )
                                        }
                                        with(draftTransactionItem.account.realCompanion) {
                                            addItemBuilderTo(-draftTransactionItem.amount, this@apply.description)
                                        }
                                    }
                                    .build()
                            budgetData.commit(clearingTransaction)
                            budgetDao.transactionDao.clearCheck(
                                draftTransactionItem
                                    .item
                                    .build(
                                        draftTransactionItem.transaction(
                                            budgetId = budgetData.id,
                                            accountIdToAccountMap = budgetData.accountIdToAccountMap,
                                        ),
                                    ),
                                clearingTransaction,
                                budgetData.id,
                            )
                            outPrinter.important("Cleared check recorded")
                        }
                    },
                )
                add(backItem)
                add(quitItem)
            },
        )
    }
