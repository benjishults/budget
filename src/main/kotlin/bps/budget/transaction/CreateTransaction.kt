package bps.budget.transaction

import bps.budget.WithIo
import bps.budget.min
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.console.inputs.SimplePromptWithDefault
import bps.console.menu.Menu
import bps.console.menu.MenuSession
import bps.console.menu.ScrollingSelectionMenu
import java.math.BigDecimal

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
            transactionBuilder.realItemBuilders.getOrNull(0)?.realAccount?.name
                ?: transactionBuilder.chargeItemBuilders.getOrNull(0)?.chargeAccount?.name
                ?: transactionBuilder.draftItemBuilders.getOrNull(0)?.draftAccount?.name
        }': '$description'
            Select a category that some of that money was spent on.  Left to cover: ${"$"}$runningTotal
            """.trimIndent(),
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.categoryAccounts - budgetData.generalAccount,
        labelGenerator = {
            String.format(
                "%,10.2f | %s",
                balance +
                        transactionBuilder
                            .categoryItemBuilders
                            .fold(BigDecimal.ZERO.setScale(2)) { runningValue, itemBuilder ->
                                if (this == itemBuilder.categoryAccount)
                                    runningValue + itemBuilder.amount!!
                                else
                                    runningValue
                            },
                name,
            )
        },
    ) { menuSession: MenuSession, selectedCategoryAccount: CategoryAccount ->
        val max = min(
            runningTotal,
            selectedCategoryAccount.balance +
                    transactionBuilder
                        .categoryItemBuilders
                        .fold(BigDecimal.ZERO.setScale(2)) { runningValue, itemBuilder ->
                            if (selectedCategoryAccount == itemBuilder.categoryAccount)
                                runningValue + itemBuilder.amount!!
                            else
                                runningValue
                        },
        )
        val categoryAmount: BigDecimal =
            SimplePromptWithDefault<BigDecimal>(
                "Enter the amount spent on '${selectedCategoryAccount.name}' for '$description' [0.00, [$max]]: ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                defaultValue = max,
            ) {
                (it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2))
                    .let { entry: BigDecimal ->
                        if (entry < BigDecimal.ZERO || entry > max)
                            BigDecimal.ZERO.setScale(2)
                        else
                            entry
                    }
            }
                .getResult()
        if (categoryAmount > BigDecimal.ZERO) {
            val categoryDescription =
                SimplePromptWithDefault(
                    "Enter description for '${selectedCategoryAccount.name}' spend [$description]: ",
                    defaultValue = description,
                    inputReader = inputReader,
                    outPrinter = outPrinter,
                )
                    .getResult()
            transactionBuilder.categoryItemBuilders.add(
                Transaction.ItemBuilder(
                    amount = -categoryAmount,
                    description = if (categoryDescription == description) null else categoryDescription,
                    categoryAccount = selectedCategoryAccount,
                ),
            )
            menuSession.pop()
            if (runningTotal - categoryAmount > BigDecimal.ZERO) {
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
                budgetDao.commit(transaction, budgetData.id)
            }
        }
    }
