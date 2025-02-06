package bps.budget.transaction

import bps.console.io.WithIo
import bps.budget.consistency.commitTransactionConsistently
import bps.budget.model.min
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserConfiguration
import bps.budget.model.toCurrencyAmountOrNull
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.InRangeInclusiveStringValidator
import bps.console.inputs.SimplePromptWithDefault
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import java.math.BigDecimal

fun WithIo.chooseRealAccountsThenCategories(
    totalAmount: BigDecimal,
    runningTotal: BigDecimal,
    transactionBuilder: Transaction.Builder,
    description: String,
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    userConfig: UserConfiguration,
): Menu =
    ScrollingSelectionMenu(
        header = {
            """
            Spending $$totalAmount for '$description'
            Select an account that some of that money was spent from.  Left to cover: $$runningTotal
            """.trimIndent()
        },
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.realAccounts,
        labelGenerator = {
            String.format(
                "%,10.2f | %-15s | %s",
                balance +
                        // TODO this can be improved for performance, if needed
                        transactionBuilder
                            .realItemBuilders
                            .fold(BigDecimal.ZERO.setScale(2)) { runningValue, itemBuilder ->
                                if (this == itemBuilder.account)
                                    runningValue + itemBuilder.amount
                                else
                                    runningValue
                            },
                name,
                this.description,
            )
        },
    ) { menuSession: MenuSession, selectedRealAccount: RealAccount ->
        outPrinter.verticalSpace()
        showRecentRelevantTransactions(
            transactionDao = transactionDao,
            account = selectedRealAccount,
            budgetData = budgetData,
            label = "Recent expenditures:",
        ) { transactionItem ->
            budgetData.generalAccount !in
                    transactionItem.transaction(
                        budgetData.id,
                        budgetData.accountIdToAccountMap,
                    )
                        .categoryItems
                        .map { it.account }
        }
        val max = min(
            runningTotal,
            selectedRealAccount.balance +
                    // TODO this can be improved for performance, if needed
                    transactionBuilder
                        .realItemBuilders
                        .fold(BigDecimal.ZERO.setScale(2)) { runningValue, itemBuilder ->
                            if (selectedRealAccount == itemBuilder.account)
                                runningValue + itemBuilder.amount
                            else
                                runningValue
                        },
        )
        outPrinter.verticalSpace()
        val currentAmount: BigDecimal =
            SimplePromptWithDefault(
                "Enter the AMOUNT spent from '${selectedRealAccount.name}' for '$description' [0.01, [$max]]: ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                additionalValidation = InRangeInclusiveStringValidator(BigDecimal("0.01").setScale(2), max),
                defaultValue = max,
            ) {
                // NOTE for SimplePromptWithDefault, the first call to transform might fail.  If it
                //    does, we want to apply the recovery action
                it.toCurrencyAmountOrNull() ?: throw IllegalArgumentException("$it is not a valid amount")
            }
                .getResult()
                ?: throw TryAgainAtMostRecentMenuException("No amount entered")
        if (currentAmount > BigDecimal.ZERO) {
            outPrinter.verticalSpace()
            val currentDescription: String =
                SimplePromptWithDefault(
                    "Enter DESCRIPTION for '${selectedRealAccount.name}' spend [$description]: ",
                    defaultValue = description,
                    inputReader = inputReader,
                    outPrinter = outPrinter,
                )
                    .getResult()
                    ?: throw TryAgainAtMostRecentMenuException("No description entered")
            with(transactionBuilder) {
                with(selectedRealAccount) {
                    addItemBuilderTo(
                        -currentAmount,
                        if (currentDescription == description)
                            null
                        else
                            currentDescription,
                    )
                }
            }
            menuSession.pop()
            if (runningTotal - currentAmount > BigDecimal.ZERO) {
                outPrinter.important("Itemization prepared")
                menuSession.push(
                    chooseRealAccountsThenCategories(
                        totalAmount,
                        runningTotal - currentAmount,
                        transactionBuilder,
                        description,
                        budgetData,
                        transactionDao,
                        userConfig,
                    ),
                )
            } else {
                outPrinter.important("All sources prepared")
                menuSession.push(
                    allocateSpendingItemMenu(
                        totalAmount,
                        transactionBuilder,
                        description,
                        budgetData,
                        transactionDao,
                        userConfig,
                    ),
                )
            }
        } else {
            outPrinter.important("Amount must be positive.")
        }
    }

fun WithIo.allocateSpendingItemMenu(
    runningTotal: BigDecimal,
    transactionBuilder: Transaction.Builder,
    description: String,
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    userConfig: UserConfiguration,
): Menu =
    ScrollingSelectionMenu(
        header = {
            """
            Spending from '${
                transactionBuilder.realItemBuilders.getOrNull(0)?.account?.name
                    ?: transactionBuilder.chargeItemBuilders.getOrNull(0)?.account?.name
                    ?: transactionBuilder.draftItemBuilders.getOrNull(0)?.account?.name
            }': '$description'
            Select a category that some of that money was spent on.  Left to cover: ${"$"}$runningTotal
            """.trimIndent()
        },
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.categoryAccounts - budgetData.generalAccount,
        labelGenerator = {
            String.format(
                "%,10.2f | %-15s | %s",
                balance +
                        transactionBuilder
                            .categoryItemBuilders
                            .fold(BigDecimal.ZERO.setScale(2)) { runningValue, itemBuilder ->
                                if (this == itemBuilder.account)
                                    runningValue + itemBuilder.amount
                                else
                                    runningValue
                            },
                name,
                this.description,
            )
        },
    ) { menuSession: MenuSession, selectedCategoryAccount: CategoryAccount ->
        outPrinter.verticalSpace()
        showRecentRelevantTransactions(
            transactionDao = transactionDao,
            account = selectedCategoryAccount,
            budgetData = budgetData,
            label = "Recent expenditures:",
        ) { transactionItem ->
            val transaction = transactionItem
                .transaction(
                    budgetId = budgetData.id,
                    accountIdToAccountMap = budgetData.accountIdToAccountMap,
                )
            ((transactionBuilder.realItemBuilders +
                    transactionBuilder.chargeItemBuilders)
                .map { it.account }
                .toSet() intersect
                    (transaction.realItems + transaction.chargeItems)
                        .map { it.account }
                        .toSet())
                .isNotEmpty()
        }

        val max = min(
            runningTotal,
            selectedCategoryAccount.balance +
                    transactionBuilder
                        .categoryItemBuilders
                        .fold(BigDecimal.ZERO.setScale(2)) { runningValue, itemBuilder ->
                            if (selectedCategoryAccount == itemBuilder.account)
                                runningValue + itemBuilder.amount
                            else
                                runningValue
                        },
        )
        outPrinter.verticalSpace()
        val categoryAmount: BigDecimal =
            SimplePromptWithDefault(
                "Enter the AMOUNT spent on '${selectedCategoryAccount.name}' for '$description' [0.01, [$max]]: ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                additionalValidation = InRangeInclusiveStringValidator(BigDecimal("0.01").setScale(2), max),
                defaultValue = max,
            ) {
                // NOTE for SimplePromptWithDefault, the first call to transform might fail.  If it
                //    does, we want to apply the recovery action
                it.toCurrencyAmountOrNull() ?: throw IllegalArgumentException("$it is not a valid amount")
            }
                .getResult()
                ?: BigDecimal.ZERO.setScale(2)
        if (categoryAmount > BigDecimal.ZERO) {
            outPrinter.verticalSpace()
            val categoryDescription: String =
                SimplePromptWithDefault(
                    "Enter DESCRIPTION for '${selectedCategoryAccount.name}' spend [$description]: ",
                    defaultValue = description,
                    inputReader = inputReader,
                    outPrinter = outPrinter,
                )
                    .getResult()
                    ?: throw TryAgainAtMostRecentMenuException("No description entered")
            with(transactionBuilder) {
                with(selectedCategoryAccount) {
                    addItemBuilderTo(
                        -categoryAmount,
                        if (categoryDescription === description)
                            null
                        else
                            categoryDescription,
                    )
                }
            }
            menuSession.pop()
            if (runningTotal - categoryAmount > BigDecimal.ZERO) {
                outPrinter.important("Itemization prepared")
                menuSession.push(
                    allocateSpendingItemMenu(
                        runningTotal - categoryAmount,
                        transactionBuilder,
                        description,
                        budgetData,
                        transactionDao,
                        userConfig,
                    ),
                )
            } else {
                val transaction = transactionBuilder.build()
                commitTransactionConsistently(transaction, transactionDao, budgetData)
                outPrinter.important("Spending recorded")
            }
        } else {
            outPrinter.important("Amount must be positive.")
        }
    }
