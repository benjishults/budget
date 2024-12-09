package bps.budget.consistency

import bps.console.io.WithIo
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.budget.income.createIncomeTransaction
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.ChargeAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.AccountDao
import bps.budget.persistence.TransactionDao
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal

fun createCategoryAccountConsistently(
    name: String,
    description: String,
    accountDao: AccountDao,
    budgetData: BudgetData,
): CategoryAccount? =
    accountDao
        .createCategoryAccountOrNull(name, description, budgetId = budgetData.id)
        ?.also { categoryAccount: CategoryAccount ->
            budgetData.addCategoryAccount(categoryAccount)
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
    if (isDraft) {
        accountDao
            .createRealAndDraftAccountOrNull(
                name,
                description,
                budgetId = budgetData.id,
            )
            ?.let { (real, draft) ->
                budgetData.addRealAccount(real)
                budgetData.addDraftAccount(draft)
                real
            }
    } else {
        accountDao.createRealAccountOrNull(
            name,
            description,
            budgetId = budgetData.id,
        )
            ?.also {
                budgetData.addRealAccount(it)
            }
    }
        ?.also { realAccount: RealAccount ->
            createAndSaveIncomeTransaction(balance, realAccount, budgetData, clock, transactionDao)
        }
        ?: run {
            outPrinter.important("Unable to save real account.")
            null
        }

private fun WithIo.createAndSaveIncomeTransaction(
    balance: BigDecimal,
    realAccount: RealAccount,
    budgetData: BudgetData,
    clock: Clock,
    transactionDao: TransactionDao,
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
            ?: throw TryAgainAtMostRecentMenuException("No timestamp entered.")
        createIncomeTransaction(
            incomeDescription,
            timestamp,
            balance,
            budgetData,
            realAccount,
        )
            .let { incomeTransaction: Transaction ->
                budgetData.commit(incomeTransaction)
                transactionDao.commit(incomeTransaction, budgetData.id)
                outPrinter.important("Real account '${realAccount.name}' created with balance $$balance")
            }
    }
}

fun createChargeAccountConsistently(
    name: String,
    description: String,
    accountDao: AccountDao,
    budgetData: BudgetData,
): ChargeAccount? =
    accountDao.createChargeAccountOrNull(name, description, budgetId = budgetData.id)
        ?.also { chargeAccount: ChargeAccount ->
            budgetData.addChargeAccount(chargeAccount)
        }

