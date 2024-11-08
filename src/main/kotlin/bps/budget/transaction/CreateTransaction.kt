package bps.budget.transaction

import bps.budget.WithIo
import bps.budget.min
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.InRangeInclusiveSimpleEntryValidator
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
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
): Menu =
    ScrollingSelectionMenu(
        header = """
            Spending $$totalAmount for '$description'
            Select an account that some of that money was spent from.  Left to cover: $$runningTotal
            """.trimIndent(),
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
        val currentAmount: BigDecimal =
            SimplePromptWithDefault(
                "Enter the amount spent from '${selectedRealAccount.name}' for '$description' [0.01, [$max]]: ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                additionalValidation = InRangeInclusiveSimpleEntryValidator(BigDecimal("0.01").setScale(2), max),
                defaultValue = max,
            ) {
                it.toCurrencyAmountOrNull()!!
            }
                .getResult()
                ?: throw TryAgainAtMostRecentMenuException("No amount entered")
        if (currentAmount > BigDecimal.ZERO) {
            val currentDescription: String =
                SimplePromptWithDefault(
                    "Enter description for '${selectedRealAccount.name}' spend [$description]: ",
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
                        budgetDao,
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
                        budgetDao,
                        userConfig,
                    ),
                )
            }
        }
    }

fun WithIo.allocateSpendingItemMenu(
    runningTotal: BigDecimal,
    transactionBuilder: Transaction.Builder,
    description: String,
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
): Menu =
    ScrollingSelectionMenu(
        header = """
            Spending from '${
            transactionBuilder.realItemBuilders.getOrNull(0)?.account?.name
                ?: transactionBuilder.chargeItemBuilders.getOrNull(0)?.account?.name
                ?: transactionBuilder.draftItemBuilders.getOrNull(0)?.account?.name
        }': '$description'
            Select a category that some of that money was spent on.  Left to cover: ${"$"}$runningTotal
            """.trimIndent(),
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
        val categoryAmount: BigDecimal =
            SimplePromptWithDefault(
                "Enter the amount spent on '${selectedCategoryAccount.name}' for '$description' [0.01, [$max]]: ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                additionalValidation = InRangeInclusiveSimpleEntryValidator(BigDecimal("0.01").setScale(2), max),
                defaultValue = max,
            ) {
                it.toCurrencyAmountOrNull()!!
            }
                .getResult()
                ?: throw TryAgainAtMostRecentMenuException("No amount entered")
        if (categoryAmount > BigDecimal.ZERO) {
            val categoryDescription: String =
                SimplePromptWithDefault(
                    "Enter description for '${selectedCategoryAccount.name}' spend [$description]: ",
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
                        budgetDao,
                        userConfig,
                    ),
                )
            } else {
                val transaction = transactionBuilder.build()
                budgetData.commit(transaction)
                budgetDao.transactionDao.commit(transaction, budgetData.id)
                outPrinter.important("Spending recorded")
            }
        }
    }
