@file:OptIn(ExperimentalUuidApi::class)

package bps.budget.charge

import bps.budget.UserConfiguration
import bps.budget.budgetQuitItem
import bps.budget.consistency.commitCreditCardPaymentConsistently
import bps.budget.model.BudgetData
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftStatus
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.model.TransactionType
import bps.budget.model.toCurrencyAmountOrNull
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AccountTransactionEntity
import bps.budget.persistence.TransactionDao
import bps.budget.transaction.ViewTransactionFixture
import bps.budget.transaction.ViewTransactionsWithoutBalancesMenu
import bps.budget.transaction.allocateSpendingItemMenu
import bps.budget.transaction.showRecentRelevantTransactions
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.NonNegativeStringValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
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
import kotlin.uuid.ExperimentalUuidApi

fun WithIo.creditCardMenu(
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu =
    ScrollingSelectionMenu(
        header = { "Select a credit card" },
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.chargeAccounts,
        // TODO do we want to incorporate credit limits to determine the balance and max
        labelGenerator = { String.format("%,10.2f | %s", balance, name) },
    ) { menuSession, chargeAccount: ChargeAccount ->
        menuSession.push(
            Menu {
                add(
                    // NOTE these might ought to be takeActionAndPush.  The intermediateAction could collect the initial data and pass it on.
                    takeAction({ "Record spending on '${chargeAccount.name}'" }) {
                        spendOnACreditCard(
                            budgetData,
                            clock,
                            transactionDao,
                            accountDao,
                            userConfig,
                            menuSession,
                            chargeAccount,
                        )
                    },
                )
                add(
                    // NOTE these might ought to be takeActionAndPush.  The intermediateAction could collect the initial data and pass it on.
                    takeAction({ "Pay '${chargeAccount.name}' bill" }) {
                        payCreditCardBill(
                            menuSession,
                            userConfig,
                            budgetData,
                            clock,
                            chargeAccount,
                            transactionDao,
                            accountDao,
                        )
                    },
                )
                add(
                    pushMenu({ "View unpaid transactions on '${chargeAccount.name}'" }) {
                        ViewTransactionsWithoutBalancesMenu(
                            account = chargeAccount,
                            transactionDao = transactionDao,
                            budgetId = budgetData.id,
                            accountIdToAccountMap = { budgetData.getAccountByIdOrNull(it) },
                            timeZone = budgetData.timeZone,
                            limit = userConfig.numberOfItemsInScrollingList,
                            filter = { it.draftStatus === DraftStatus.outstanding.name },
                            header = { "Unpaid transactions on '${chargeAccount.name}'" },
                            prompt = { "Select transaction to view details: " },
                            outPrinter = outPrinter,
                            extraItems = listOf(), // TODO toggle cleared/outstanding
                        ) { _, extendedTransactionItem: AccountTransactionEntity ->
                            with(ViewTransactionFixture) {
                                outPrinter.verticalSpace()
                                outPrinter.showTransactionDetailsAction(
                                    transactionDao.getTransactionOrNull(
                                        extendedTransactionItem.transactionId,
                                        extendedTransactionItem.budgetId,
                                    )!!,
                                    budgetData.timeZone,
                                ) { budgetData.getAccountByIdOrNull(it) }
                            }
                        }
                    },
                )
                add(backItem)
                add(budgetQuitItem)
            },
        )
    }

private fun WithIo.payCreditCardBill(
    menuSession: MenuSession,
    userConfig: UserConfiguration,
    budgetData: BudgetData,
    clock: Clock,
    chargeAccount: ChargeAccount,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
) {
    outPrinter.verticalSpace()
    val amountOfBill: BigDecimal =
        SimplePrompt(
            basicPrompt = "Enter the total AMOUNT of the bill being paid on '${chargeAccount.name}': ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = NonNegativeStringValidator,
        ) {
            // NOTE in SimplePrompt, this is only called if the validator passes and in this case, the validator
            //    guarantees that this is not null
            it.toCurrencyAmountOrNull()!!
        }
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
    if (amountOfBill > BigDecimal.ZERO) {
        menuSession.push(
            ScrollingSelectionMenu(
                header = { "Select real account bill on '${chargeAccount.name}' was paid from" },
                limit = userConfig.numberOfItemsInScrollingList,
                baseList = budgetData.realAccounts,
                labelGenerator = { String.format("%,10.2f | %s", balance, name) },
            ) { _: MenuSession, selectedRealAccount: RealAccount ->
                outPrinter.verticalSpace()
                val timestamp: Instant =
                    getTimestampFromUser(
                        queryForNow = "Use current time for the bill-pay transaction [Y]? ",
                        timeZone = budgetData.timeZone,
                        clock = clock,
                    )
                        ?.toInstant(budgetData.timeZone)
                        ?: throw TryAgainAtMostRecentMenuException("No timestamp entered.")
                outPrinter.verticalSpace()
                val description: String =
                    SimplePromptWithDefault(
                        "Description of transaction [pay '${chargeAccount.name}' bill]: ",
                        inputReader = inputReader,
                        outPrinter = outPrinter,
                        defaultValue = "pay '${chargeAccount.name}' bill",
                    )
                        .getResult()
                        ?: throw TryAgainAtMostRecentMenuException("No description entered.")
                val billPayTransaction: Transaction =
                    Transaction.Builder(
                        description = description,
                        timestamp = timestamp,
                        transactionType = TransactionType.clearing.name,
                    )
                        .apply {
                            with(selectedRealAccount) {
                                addItemBuilderTo(-amountOfBill, description)
                            }
                            with(chargeAccount) {
                                addItemBuilderTo(amountOfBill, description, DraftStatus.clearing)
                            }
                        }
                        .build()
                //
                menuSession.push(
                    selectOrCreateChargeTransactionsForBill(
                        amountOfBill = amountOfBill,
                        billPayTransaction = billPayTransaction,
                        chargeAccount = chargeAccount,
                        budgetData = budgetData,
                        transactionDao = transactionDao,
                        userConfig = userConfig,
                        menuSession = menuSession,
                        clock = clock,
                        accountDao = accountDao,
                    ),
                )
            },
        )

    } else {
        outPrinter.important("Amount must be positive.")
    }
}

// TODO would be nice to display the already-selected transaction items as well
// TODO some folks might like to be able to pay an amount that isn't related to transactions on the card
private fun WithIo.selectOrCreateChargeTransactionsForBill(
    amountOfBill: BigDecimal,
    billPayTransaction: Transaction,
    chargeAccount: ChargeAccount,
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    userConfig: UserConfiguration,
    menuSession: MenuSession,
    clock: Clock,
): Menu = selectOrCreateChargeTransactionsForBillHelper(
    amountOfBill = amountOfBill,
    runningTotal = amountOfBill,
    billPayTransaction = billPayTransaction,
    chargeAccount = chargeAccount,
    selectedChargeItems = emptyList(),
    budgetData = budgetData,
    transactionDao = transactionDao,
    accountDao = accountDao,
    userConfig = userConfig,
    menuSession = menuSession,
    clock = clock,
)

private fun WithIo.selectOrCreateChargeTransactionsForBillHelper(
    amountOfBill: BigDecimal,
    runningTotal: BigDecimal,
    billPayTransaction: Transaction,
    chargeAccount: ChargeAccount,
    selectedChargeItems: List<AccountTransactionEntity>,
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    userConfig: UserConfiguration,
    menuSession: MenuSession,
    clock: Clock,
): Menu = ViewTransactionsWithoutBalancesMenu(
    account = chargeAccount,
    transactionDao = transactionDao,
    budgetId = budgetData.id,
    accountIdToAccountMap = { budgetData.getAccountByIdOrNull(it) },
    timeZone = budgetData.timeZone,
    header = {
        "Select all transactions from this '${chargeAccount.name}' bill.  Amount to be covered: $${
            amountOfBill +
                    selectedChargeItems
                        .fold(BigDecimal.ZERO) { sum, item ->
                            sum + item.amount
                        }
        }"
    },
    prompt = { "Select a transaction covered in this bill: " },
    limit = userConfig.numberOfItemsInScrollingList,
    filter = { it.draftStatus === DraftStatus.outstanding.name && it !in selectedChargeItems },
    outPrinter = outPrinter,
    extraItems = listOf(
        takeAction({ "Record a missing transaction from this '${chargeAccount.name}' bill" }) {
            spendOnACreditCard(
                budgetData,
                clock,
                transactionDao,
                accountDao,
                userConfig,
                menuSession,
                chargeAccount,
            )
        },
    ),
) { _: MenuSession, chargeTransactionItem: AccountTransactionEntity ->
    val allSelectedChargeItems: List<AccountTransactionEntity> =
        selectedChargeItems + chargeTransactionItem
    val remainingToBeCovered: BigDecimal = runningTotal + chargeTransactionItem.amount
    when {
        remainingToBeCovered == BigDecimal.ZERO.setScale(2) -> {
            // TODO might want to make this a prompt for approval since, with a credit, this could possibly stop too early
            menuSession.pop()
            commitCreditCardPaymentConsistently(
                billPayTransaction,
                allSelectedChargeItems.map { it.transactionId },
                transactionDao,
                accountDao,
                budgetData,
            )
            outPrinter.important("Payment recorded!")
            menuSession.pop()
        }
        remainingToBeCovered < BigDecimal.ZERO -> {
            // TODO what happens if there's just a credit?
            outPrinter.important("ERROR: this bill payment amount is not large enough to cover that transaction")
        }
        else -> {
            outPrinter.important("Item prepared")
            menuSession.pop()
            menuSession.push(
                selectOrCreateChargeTransactionsForBillHelper(
                    amountOfBill = amountOfBill,
                    runningTotal = remainingToBeCovered,
                    billPayTransaction = billPayTransaction,
                    chargeAccount = chargeAccount,
                    selectedChargeItems = allSelectedChargeItems,
                    budgetData = budgetData,
                    transactionDao = transactionDao,
                    userConfig = userConfig,
                    menuSession = menuSession,
                    clock = clock,
                    accountDao = accountDao,
                ),
            )
        }
    }
}

private fun WithIo.spendOnACreditCard(
    budgetData: BudgetData,
    clock: Clock,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    userConfig: UserConfiguration,
    menuSession: MenuSession,
    chargeAccount: ChargeAccount,
) {
    outPrinter.verticalSpace()
    showRecentRelevantTransactions(
        transactionDao = transactionDao,
        account = chargeAccount,
        budgetData = budgetData,
        label = "Recent expenditures:",
    )
    // TODO enter check number if checking account
    // NOTE this is why we have separate draft accounts -- to easily know the real vs draft balance
    outPrinter.verticalSpace()
    val amount: BigDecimal =
        SimplePrompt<BigDecimal>(
            "Enter the AMOUNT of the charge on '${chargeAccount.name}': ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = NonNegativeStringValidator,
        ) {
            // NOTE in SimplePrompt, this is only called if the validator passes and in this case, the validator
            //    guarantees that this is not null
            it.toCurrencyAmountOrNull()!!
        }
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
    if (amount > BigDecimal.ZERO) {
        outPrinter.verticalSpace()
        val description: String =
            SimplePrompt<String>(
                "Enter the RECIPIENT of the charge on '${chargeAccount.name}': ",
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
                ?: throw TryAgainAtMostRecentMenuException("No description entered.")
        outPrinter.verticalSpace()
        val timestamp: Instant = getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
            ?.toInstant(budgetData.timeZone)
            ?: throw TryAgainAtMostRecentMenuException("No timestamp entered.")
        val transactionBuilder: Transaction.Builder =
            Transaction.Builder(
                description = description,
                timestamp = timestamp,
                transactionType = TransactionType.expense.name,
            )
                .apply {
                    with(chargeAccount) {
                        addItemBuilderTo(-amount, description, DraftStatus.outstanding)
                    }
                }
        menuSession.push(
            allocateSpendingItemMenu(
                amount,
                transactionBuilder,
                description,
                budgetData,
                transactionDao,
                accountDao,
                userConfig,
            ),
        )
    } else {
        outPrinter.important("Amount must be positive.")
    }
}
