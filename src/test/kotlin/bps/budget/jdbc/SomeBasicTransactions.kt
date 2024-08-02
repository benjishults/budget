package bps.budget.jdbc

import bps.budget.BudgetConfigurations
import bps.budget.data.BudgetData
import bps.budget.model.defaultCheckingAccountName
import bps.budget.model.defaultCheckingDraftsAccountName
import bps.budget.model.defaultFoodAccountName
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.transaction.Transaction
import bps.budget.transaction.TransactionItem
import bps.budget.ui.ConsoleUiFunctions
import io.kotest.core.spec.style.FreeSpec
import java.math.BigDecimal
import java.time.OffsetDateTime

class SomeBasicTransactions : FreeSpec(), BasicAccountsTestFixture {

    override val configurations = BudgetConfigurations(sequenceOf("hasGeneralWalletAndFoodJdbc.yml"))
    override val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)

    init {
        useBasicAccounts()

        "with data from config" - {
            val uiFunctions = ConsoleUiFunctions()
            val budgetData = BudgetData(uiFunctions, jdbcDao)
            "record income" {
                val amount = BigDecimal("1000.00")
                val income = Transaction(
                    amount = amount,
                    description = "income",
                    timestamp = OffsetDateTime.now(),
                    categoryItems = listOf(TransactionItem(amount, categoryAccount = budgetData.generalAccount)),
                    realItems = listOf(
                        TransactionItem(
                            amount,
                            realAccount = budgetData.realAccounts.find { it.name == defaultCheckingAccountName }!!,
                        ),
                    ),
                )
                budgetData.commit(income)
                jdbcDao.commit(income)
            }
            "allocate to food" {
                val amount = BigDecimal("300.00")
                val allocate = Transaction(
                    amount = amount,
                    description = "allocate",
                    timestamp = OffsetDateTime.now(),
                    categoryItems = buildList {
                        add(TransactionItem(amount, categoryAccount = budgetData.generalAccount))
                        add(
                            TransactionItem(
                                -amount,
                                categoryAccount = budgetData.categoryAccounts.find { it.name == defaultFoodAccountName }!!,
                            ),
                        )
                    },
                )
                budgetData.commit(allocate)
                jdbcDao.commit(allocate)
            }
            "write a check for food" {
                val amount = BigDecimal("100.00")
                val writeCheck = Transaction(
                    amount = amount,
                    description = "groceries",
                    timestamp = OffsetDateTime.now(),
                    categoryItems = listOf(
                        TransactionItem(
                            amount,
                            categoryAccount = budgetData.categoryAccounts.find { it.name == defaultFoodAccountName }!!,
                        ),
                    ),
                    draftItems = listOf(
                        TransactionItem(
                            -amount,
                            draftAccount = budgetData.draftAccounts.find { it.name == defaultCheckingDraftsAccountName }!!,
                        ),
                    ),
                )
                budgetData.commit(writeCheck)
                jdbcDao.commit(writeCheck)
            }
            "check balances" {
            }
        }

    }

}
