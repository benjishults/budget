package bps.budget.jdbc

import bps.budget.BudgetConfigurations
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.model.TransactionItem
import bps.budget.model.defaultCheckingAccountName
import bps.budget.model.defaultCheckingDraftsAccountName
import bps.budget.model.defaultFoodAccountName
import bps.budget.model.defaultGeneralAccountName
import bps.budget.model.defaultNecessitiesAccountName
import bps.budget.model.defaultWalletAccountName
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.ui.ConsoleUiFunctions
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
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
                        add(TransactionItem(-amount, categoryAccount = budgetData.generalAccount))
                        add(
                            TransactionItem(
                                amount,
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
                            -amount,
                            categoryAccount = budgetData.categoryAccounts.find { it.name == defaultFoodAccountName }!!,
                        ),
                    ),
                    draftItems = listOf(
                        TransactionItem(
                            amount,
                            draftAccount = budgetData.draftAccounts.find { it.name == defaultCheckingDraftsAccountName }!!,
                        ),
                    ),
                )
                budgetData.commit(writeCheck)
                jdbcDao.commit(writeCheck)
            }
            "check balances after writing check" {
                budgetData.realAccounts.forEach { realAccount: RealAccount ->
                    when (realAccount.name) {
                        defaultCheckingAccountName -> {
                            realAccount.balance shouldBe BigDecimal("1000.00")
                        }
                        defaultWalletAccountName ->
                            realAccount.balance shouldBe BigDecimal.ZERO.setScale(2)
                        else ->
                            fail("unexpected real account")
                    }
                }
                budgetData.categoryAccounts.forEach { it: CategoryAccount ->
                    when (it.name) {
                        defaultGeneralAccountName -> it.balance shouldBe BigDecimal("700.00")
                        defaultFoodAccountName -> it.balance shouldBe BigDecimal("200.00")
                        defaultNecessitiesAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        else -> fail("unexpected category account")
                    }
                }
                budgetData.draftAccounts.forEach { it: DraftAccount ->
                    when (it.name) {
                        defaultCheckingDraftsAccountName -> it.balance shouldBe BigDecimal("100.00")
                        else -> fail("unexpected draft account")
                    }
                }
            }
            "check clears" {
                val amount = BigDecimal("100.00")
                val writeCheck = Transaction(
                    amount = amount,
                    description = "groceries",
                    timestamp = OffsetDateTime.now(),
                    realItems = listOf(
                        TransactionItem(
                            -amount,
                            realAccount = budgetData.realAccounts.find { it.name == defaultCheckingAccountName }!!,
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
            "check balances after check clears" {
                checkBalancesAfterCheckClears(budgetData)
            }
            "check balances in DB" {
                checkBalancesAfterCheckClears(jdbcDao.load())
            }
        }

    }

    private fun checkBalancesAfterCheckClears(budgetData: BudgetData) {
        budgetData.realAccounts.size shouldBe 2
        budgetData.realAccounts.forEach { realAccount: RealAccount ->
            when (realAccount.name) {
                defaultCheckingAccountName -> {
                    realAccount.balance shouldBe BigDecimal("900.00")
                }
                defaultWalletAccountName ->
                    realAccount.balance shouldBe BigDecimal.ZERO.setScale(2)
                else ->
                    fail("unexpected real account")
            }
        }
        budgetData.categoryAccounts.size shouldBe 3
        budgetData.categoryAccounts.forEach { it: CategoryAccount ->
            when (it.name) {
                defaultGeneralAccountName -> it.balance shouldBe BigDecimal("700.00")
                defaultFoodAccountName -> it.balance shouldBe BigDecimal("200.00")
                defaultNecessitiesAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                else -> fail("unexpected category account")
            }
        }
        budgetData.draftAccounts.size shouldBe 1
        budgetData.draftAccounts.forEach { it: DraftAccount ->
            when (it.name) {
                defaultCheckingDraftsAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                else -> fail("unexpected draft account")
            }
        }
    }

}
