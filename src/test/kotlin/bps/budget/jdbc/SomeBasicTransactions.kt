package bps.budget.jdbc

import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.model.defaultCheckingAccountName
import bps.budget.model.defaultCheckingDraftsAccountName
import bps.budget.model.defaultEducationAccountName
import bps.budget.model.defaultEntertainmentAccountName
import bps.budget.model.defaultFoodAccountName
import bps.budget.model.defaultGeneralAccountName
import bps.budget.model.defaultMedicalAccountName
import bps.budget.model.defaultNecessitiesAccountName
import bps.budget.model.defaultNetworkAccountName
import bps.budget.model.defaultTransportationAccountName
import bps.budget.model.defaultTravelAccountName
import bps.budget.model.defaultWalletAccountName
import bps.budget.model.defaultWorkAccountName
import bps.budget.persistence.budgetDataFactory
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.ui.ConsoleUiFacade
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.OffsetDateTime

class SomeBasicTransactions : FreeSpec(), BasicAccountsTestFixture {

    override val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)

    init {
        createBasicAccountsBeforeSpec()
        closeJdbcAfterSpec()

        "with data from config" - {
            val uiFunctions = ConsoleUiFacade()
            val budgetData = budgetDataFactory(uiFunctions, jdbcDao)
            "record income" {
                val amount = BigDecimal("1000.00")
                val income: Transaction =
                    Transaction
                        .Builder(
                            description = "income",
                            timestamp = OffsetDateTime.now(),
                        )
                        .apply {
                            categoryItems.add(
                                Transaction.ItemBuilder(
                                    amount,
                                    categoryAccount = budgetData.generalAccount,
                                ),
                            )
                            realItems.add(
                                Transaction.ItemBuilder(
                                    amount,
                                    realAccount = budgetData.realAccounts.find {
                                        it.name == defaultCheckingAccountName
                                    }!!,
                                ),
                            )

                        }
                        .build()
                budgetData.commit(income)
                jdbcDao.commit(income)
            }
            "allocate to food" {
                val amount = BigDecimal("300.00")
                val allocate: Transaction =
                    Transaction
                        .Builder(
                            description = "allocate",
                            timestamp = OffsetDateTime.now(),
                        )
                        .apply {
                            categoryItems.addAll(
                                buildList {
                                    add(Transaction.ItemBuilder(-amount, categoryAccount = budgetData.generalAccount))
                                    add(
                                        Transaction.ItemBuilder(
                                            amount,
                                            categoryAccount = budgetData.categoryAccounts.find {
                                                it.name == defaultFoodAccountName
                                            }!!,
                                        ),
                                    )
                                },
                            )

                        }
                        .build()
                budgetData.commit(allocate)
                jdbcDao.commit(allocate)
            }
            "write a check for food" {
                val amount = BigDecimal("100.00")
                val writeCheck: Transaction = Transaction.Builder(
                    description = "groceries",
                    timestamp = OffsetDateTime.now(),
                )
                    .apply {
                        categoryItems.add(
                            Transaction.ItemBuilder(
                                -amount,
                                categoryAccount = budgetData.categoryAccounts.find {
                                    it.name == defaultFoodAccountName
                                }!!,
                            ),
                        )
                        draftItems.add(
                            Transaction.ItemBuilder(
                                amount,
                                draftAccount = budgetData.draftAccounts.find {
                                    it.name == defaultCheckingDraftsAccountName
                                }!!,
                            ),
                        )
                    }
                    .build()
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
                        defaultWorkAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultTransportationAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultTravelAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultMedicalAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultEducationAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultEntertainmentAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultNetworkAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        else -> fail("unexpected category account: $it")
                    }
                }
                budgetData.draftAccounts.forEach { it: DraftAccount ->
                    when (it.name) {
                        defaultCheckingDraftsAccountName -> it.balance shouldBe BigDecimal("100.00")
                        else -> fail("unexpected draft account: $it")
                    }
                }
            }
            "check clears" {
                val amount = BigDecimal("100.00")
                val writeCheck: Transaction = Transaction.Builder(
                    description = "groceries",
                    timestamp = OffsetDateTime.now(),
                )
                    .apply {
                        realItems.add(
                            Transaction.ItemBuilder(
                                -amount,
                                realAccount = budgetData.realAccounts.find {
                                    it.name == defaultCheckingAccountName
                                }!!,
                            ),
                        )
                        draftItems.add(
                            Transaction.ItemBuilder(
                                -amount,
                                draftAccount = budgetData.draftAccounts.find {
                                    it.name == defaultCheckingDraftsAccountName
                                }!!,
                            ),
                        )

                    }
                    .build()
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
                    fail("unexpected real account: $realAccount")
            }
        }
        budgetData.categoryAccounts.size shouldBe 10
        budgetData.categoryAccounts.forEach { it: CategoryAccount ->
            when (it.name) {
                defaultGeneralAccountName -> it.balance shouldBe BigDecimal("700.00")
                defaultFoodAccountName -> it.balance shouldBe BigDecimal("200.00")
                defaultNecessitiesAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                defaultWorkAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                defaultTransportationAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                defaultTravelAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                defaultMedicalAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                defaultEducationAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                defaultEntertainmentAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                defaultNetworkAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                else -> fail("unexpected category account: $it")
            }
        }
        budgetData.draftAccounts.size shouldBe 1
        budgetData.draftAccounts.forEach { it: DraftAccount ->
            when (it.name) {
                defaultCheckingDraftsAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                else -> fail("unexpected draft account: $it")
            }
        }
    }

}
