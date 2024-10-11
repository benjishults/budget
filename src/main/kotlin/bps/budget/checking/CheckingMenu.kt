package bps.budget.checking

import bps.budget.WithIo
import bps.budget.model.BudgetData
import bps.budget.model.DraftAccount
import bps.budget.model.DraftStatus
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.budget.transaction.ViewTransactionsMenu
import bps.budget.transaction.createTransactionItemMenu
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
        header = "Select the checking account",
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.draftAccounts,
        labelGenerator = { String.format("%,10.2f | %s", realCompanion.balance - balance, name) },
    ) { menuSession, draftAccount: DraftAccount ->
        menuSession.push(
            Menu {
                add(
                    takeAction("Write a check on ${draftAccount.name}") {
                        // TODO enter check number if checking account
                        // NOTE this is why we have separate draft accounts -- to easily know the real vs draft balance
                        val max = draftAccount.realCompanion.balance - draftAccount.balance
                        val min = BigDecimal.ZERO.setScale(2)
                        val amount: BigDecimal =
                            SimplePromptWithDefault<BigDecimal>(
                                "Enter the amount of check on ${draftAccount.name} [$min, $max]: ",
                                inputReader = inputReader,
                                outPrinter = outPrinter,
                                defaultValue = min,
                                additionalValidation = { input: String ->
                                    input
                                        .toCurrencyAmountOrNull()
                                        ?.let {
                                            it in min..max
                                        }
                                        ?: false
                                },
                            ) {
                                it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2)
                            }
                                .getResult()
                        if (amount > BigDecimal.ZERO) {
                            val description: String =
                                SimplePrompt<String>(
                                    "Enter the recipient of the check: ",
                                    inputReader = inputReader,
                                    outPrinter = outPrinter,
                                )
                                    .getResult()
                            val timestamp: Instant =
                                getTimestampFromUser(
                                    timeZone = budgetData.timeZone,
                                    clock = clock,
                                )
                            val transactionBuilder: Transaction.Builder =
                                Transaction.Builder(description, timestamp)
                                    .apply {
                                        draftItemBuilders.add(
                                            Transaction.ItemBuilder(
                                                amount = amount,
                                                description = description,
                                                draftAccount = draftAccount,
                                                draftStatus = DraftStatus.outstanding,
                                            ),
                                        )
                                    }
                            menuSession.push(
                                createTransactionItemMenu(
                                    amount,
                                    transactionBuilder,
                                    description,
                                    budgetData,
                                    budgetDao,
                                    userConfig,
                                ),
                            )
                        }
                    },
                )
                add(
                    pushMenu("Record check cleared") {
                        ViewTransactionsMenu(
                            filter = { it.draftStatus === DraftStatus.outstanding },
                            header = "Select the check that cleared",
                            prompt = "Select the check that cleared: ",
                            account = draftAccount,
                            budgetDao = budgetDao,
                            budgetData = budgetData,
                            limit = userConfig.numberOfItemsInScrollingList,
                            outPrinter = outPrinter,
                        ) { _, draftTransactionItem ->
                            val timestamp: Instant =
                                getTimestampFromUser(
                                    "Did the check clear just now [Y]? ",
                                    budgetData.timeZone,
                                    clock,
                                )
                            val clearingTransaction =
                                Transaction.Builder(
                                    draftTransactionItem.transaction.description,
                                    timestamp,
                                )
                                    .apply {
                                        draftItemBuilders.add(
                                            Transaction.ItemBuilder(
                                                amount = -draftTransactionItem.amount,
                                                description = description,
                                                draftAccount = draftAccount,
                                                draftStatus = DraftStatus.clearing,
                                            ),
                                        )
                                        realItemBuilders.add(
                                            Transaction.ItemBuilder(
                                                amount = -draftTransactionItem.amount,
                                                description = description,
                                                realAccount = draftTransactionItem.draftAccount!!.realCompanion,
                                            ),
                                        )
                                    }
                                    .build()
                            budgetData.commit(clearingTransaction)
                            budgetDao.clearCheck(
                                draftTransactionItem,
                                clearingTransaction,
                                budgetData.id,
                            )
                        }
                    },
                )
                add(backItem)
                add(quitItem)
            },
        )
    }
