package bps.budget.charge

import bps.budget.WithIo
import bps.budget.model.BudgetData
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftStatus
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.budget.transaction.ViewTransactionsMenu
import bps.budget.transaction.allocateSpendingItemMenu
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

fun WithIo.creditCardMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu =
    ScrollingSelectionMenu(
        header = "Select a credit card",
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.chargeAccounts,
        // TODO do we want to incorporate credit limits to determine the balance and max
        labelGenerator = { String.format("%,10.2f | %s", balance, name) },
    ) { menuSession, chargeAccount: ChargeAccount ->
        menuSession.push(
            Menu {
                add(
                    takeAction("Record spending on '${chargeAccount.name}'") {
                        spendOnACreditCard(
                            budgetData,
                            clock,
                            budgetDao,
                            userConfig,
                            menuSession,
                            chargeAccount,
                        )
                    },
                )
                add(
                    takeAction("Pay '${chargeAccount.name}' bill") {
                        payCreditCardBill(
                            menuSession,
                            userConfig,
                            budgetData,
                            clock,
                            chargeAccount,
                            budgetDao,
                        )
                    },
                )
                add(
                    pushMenu("View unpaid transactions on '${chargeAccount.name}'") {
                        ViewTransactionsMenu(
                            account = chargeAccount,
                            budgetDao = budgetDao,
                            budgetData = budgetData,
                            limit = userConfig.numberOfItemsInScrollingList,
                            filter = { it.draftStatus === DraftStatus.outstanding },
                            header = "Unpaid transactions on '${chargeAccount.name}'",
                            prompt = "Select transaction to view details: ",
                            outPrinter = outPrinter,
                            extraItems = listOf(), // TODO toggle cleared/outstanding
                        )
                    },
                )
                add(backItem)
                add(quitItem)
            },
        )
    }

private fun WithIo.payCreditCardBill(
    menuSession: MenuSession,
    userConfig: UserConfiguration,
    budgetData: BudgetData,
    clock: Clock,
    chargeAccount: ChargeAccount,
    budgetDao: BudgetDao,
) {
    val amountOfBill: BigDecimal =
        SimplePrompt(
            basicPrompt = "Enter the total amount of the bill being paid on '${chargeAccount.name}': ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validate = {
                it
                    .toCurrencyAmountOrNull()
                    ?.let { amount ->
                        amount >= BigDecimal.ZERO
                    }
                    ?: false
            },
        ) { it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO }
            .getResult()
    if (amountOfBill > BigDecimal.ZERO) {
        menuSession.push(
            ScrollingSelectionMenu(
                header = "Select real account bill on '${chargeAccount.name}' was paid from",
                limit = userConfig.numberOfItemsInScrollingList,
                baseList = budgetData.realAccounts,
                labelGenerator = { String.format("%,10.2f | %s", balance, name) },
            ) { _: MenuSession, selectedRealAccount: RealAccount ->
                val timestamp: Instant =
                    getTimestampFromUser(
                        "Use current time for the bill-pay transaction [Y]? ",
                        budgetData.timeZone,
                        clock,
                    )
                val description: String =
                    SimplePromptWithDefault(
                        "Description of transaction [pay '${chargeAccount.name}' bill]: ",
                        inputReader = inputReader,
                        outPrinter = outPrinter,
                        defaultValue = "pay '${chargeAccount.name}' bill",
                    )
                        .getResult()
                val billPayTransaction: Transaction =
                    Transaction.Builder(description, timestamp)
                        .apply {
                            realItemBuilders.add(
                                Transaction.ItemBuilder(
                                    amount = -amountOfBill,
                                    description = description,
                                    realAccount = selectedRealAccount,
                                ),
                            )
                            chargeItemBuilders.add(
                                Transaction.ItemBuilder(
                                    amount = amountOfBill,
                                    description = description,
                                    chargeAccount = chargeAccount,
                                    draftStatus = DraftStatus.clearing,
                                ),
                            )
                        }
                        .build()
                //
                menuSession.push(
                    selectOrCreateChargeTransactionsForBill(
                        amountOfBill = amountOfBill,
                        billPayTransaction = billPayTransaction,
                        chargeAccount = chargeAccount,
                        selectedItems = emptyList(),
                        budgetData = budgetData,
                        budgetDao = budgetDao,
                        userConfig = userConfig,
                        menuSession = menuSession,
                        clock = clock,
                    ),
                )
            },
        )

    }
}

// TODO would be nice to display the already-selected transaction items as well
// TODO some folks might like to be able to pay an amount that isn't related to transactions on the card
private fun WithIo.selectOrCreateChargeTransactionsForBill(
    amountOfBill: BigDecimal,
    billPayTransaction: Transaction,
    chargeAccount: ChargeAccount,
    selectedItems: List<Transaction.Item>,
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    menuSession: MenuSession,
    clock: Clock,
): Menu = ViewTransactionsMenu(
    filter = { it.draftStatus === DraftStatus.outstanding && it !in selectedItems },
    header = "Select all transactions from this '${chargeAccount.name}' bill.  Amount to be covered: $${
        amountOfBill +
                selectedItems.fold(BigDecimal.ZERO) { sum, item ->
                    sum + item.amount
                }
    }",
    prompt = "Select a transaction covered in this bill: ",
    extraItems = listOf(
        takeAction("Record a missing transaction from this '${chargeAccount.name}' bill") {
            spendOnACreditCard(
                budgetData,
                clock,
                budgetDao,
                userConfig,
                menuSession,
                chargeAccount,
            )
        },
    ),
    account = chargeAccount,
    budgetDao = budgetDao,
    budgetData = budgetData,
    limit = userConfig.numberOfItemsInScrollingList,
    outPrinter = outPrinter,
) { _, chargeTransactionItem ->
    val allSelectedItems: List<Transaction.Item> = selectedItems + chargeTransactionItem
    // FIXME if the selected amount is greater than allowed, then give a "denied" message
    //       ... or don't show such items in the first place
    val remainingToBeCovered: BigDecimal =
        amountOfBill +
                allSelectedItems
                    .fold(BigDecimal.ZERO.setScale(2)) { sum, item ->
                        sum + item.amount
                    }
    when {
        remainingToBeCovered == BigDecimal.ZERO.setScale(2) -> {
            menuSession.pop()
            outPrinter.important("Payment recorded!")
            budgetData.commit(billPayTransaction)
            budgetDao.commitCreditCardPayment(
                allSelectedItems,
                billPayTransaction,
                budgetData.id,
            )
        }
        remainingToBeCovered < BigDecimal.ZERO -> {
            outPrinter.important("ERROR: this bill payment amount is not large enough to cover that transaction")
        }
        else -> {
            menuSession.pop()
            menuSession.push(
                selectOrCreateChargeTransactionsForBill(
                    amountOfBill = amountOfBill,
                    billPayTransaction = billPayTransaction,
                    chargeAccount = chargeAccount,
                    selectedItems = allSelectedItems,
                    budgetData = budgetData,
                    budgetDao = budgetDao,
                    userConfig = userConfig,
                    menuSession = menuSession,
                    clock = clock,
                ),
            )
        }
    }
}

private fun WithIo.spendOnACreditCard(
    budgetData: BudgetData,
    clock: Clock,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    menuSession: MenuSession,
    chargeAccount: ChargeAccount,
) {
    // TODO enter check number if checking account
    // NOTE this is why we have separate draft accounts -- to easily know the real vs draft balance
    val amount: BigDecimal =
        SimplePrompt<BigDecimal>(
            "Enter the amount of the charge on '${chargeAccount.name}': ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validate = { input: String ->
                input
                    .toCurrencyAmountOrNull()
                    ?.let {
                        it >= BigDecimal.ZERO
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
                "Enter the recipient of the charge on '${chargeAccount.name}': ",
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
        val timestamp: Instant =
            getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
        val transactionBuilder: Transaction.Builder =
            Transaction.Builder(description, timestamp)
                .apply {
                    chargeItemBuilders.add(
                        Transaction.ItemBuilder(
                            amount = -amount,
                            description = description,
                            chargeAccount = chargeAccount,
                            draftStatus = DraftStatus.outstanding,
                        ),
                    )
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
    }
}
