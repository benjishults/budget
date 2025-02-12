package bps.budget.jdbc

import bps.budget.auth.AuthenticatedUser
import bps.budget.consistency.commitTransactionConsistently
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.model.Transaction.Type
import bps.budget.model.defaultCheckingAccountName
import bps.budget.model.defaultCosmeticsAccountName
import bps.budget.model.defaultEducationAccountName
import bps.budget.model.defaultEntertainmentAccountName
import bps.budget.model.defaultFoodAccountName
import bps.budget.model.defaultGeneralAccountName
import bps.budget.model.defaultHobbyAccountName
import bps.budget.model.defaultHomeAccountName
import bps.budget.model.defaultHousingAccountName
import bps.budget.model.defaultMedicalAccountName
import bps.budget.model.defaultNecessitiesAccountName
import bps.budget.model.defaultNetworkAccountName
import bps.budget.model.defaultTransportationAccountName
import bps.budget.model.defaultTravelAccountName
import bps.budget.model.defaultWalletAccountName
import bps.budget.model.defaultWorkAccountName
import bps.budget.persistence.getBudgetNameFromPersistenceConfig
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.persistence.loadBudgetData
import bps.kotlin.WithMockClock
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone
import java.math.BigDecimal
import java.util.UUID

class SomeBasicTransactionsTest : FreeSpec(),
    WithMockClock,
    BasicAccountsJdbcTestFixture {

    override val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)

    init {
        val clock = produceSecondTickingClock()
        val budgetId: UUID = UUID.fromString("89bc165a-ee70-43a4-b637-2774bcfc3ea4")
        val userId: UUID = UUID.fromString("f0f209c8-1b1e-43b3-8799-2dba58524d02")
        createBasicAccountsBeforeSpec(
            budgetId,
            getBudgetNameFromPersistenceConfig(configurations.persistence)!!,
            AuthenticatedUser(userId, configurations.user.defaultLogin!!),
            TimeZone.of("America/Chicago"),
            clock,
        )
        closeJdbcAfterSpec()

        "with data from config" - {
            val budgetData = loadBudgetData(
                authenticatedUser = jdbcDao.userBudgetDao.getUserByLoginOrNull(configurations.user.defaultLogin!!) as AuthenticatedUser,
                budgetDao = jdbcDao,
                budgetName = getBudgetNameFromPersistenceConfig(configurations.persistence)!!,
            )
            "record income" {
                val amount = BigDecimal("1000.00").setScale(2)
                val income: Transaction =
                    Transaction
                        .Builder(
                            description = "income into $defaultCheckingAccountName",
                            timestamp = clock.now(),
                            type = Type.income,
                        )
                        .apply {
                            with(budgetData.generalAccount) {
                                addItemBuilderTo(amount)
                            }
                            with(
                                budgetData.realAccounts
                                    .find {
                                        it.name == defaultCheckingAccountName
                                    }!!,
                            ) {
                                addItemBuilderTo(amount)
                            }
                        }
                        .build()
                commitTransactionConsistently(income, jdbcDao.transactionDao, budgetData)
            }
            "allocate to food" {
                val amount = BigDecimal("300.00")
                val allocate: Transaction =
                    Transaction
                        .Builder(
                            description = "allocate into $defaultFoodAccountName",
                            timestamp = clock.now(),
                            type = Type.allowance,
                        )
                        .apply {
                            with(budgetData.generalAccount) {
                                addItemBuilderTo(-amount)
                            }
                            with(
                                budgetData.categoryAccounts.find {
                                    it.name == defaultFoodAccountName
                                }!!,
                            ) {
                                addItemBuilderTo(amount)
                            }
                        }
                        .build()
                commitTransactionConsistently(allocate, jdbcDao.transactionDao, budgetData)
            }
            "write a check for food" {
                val amount = BigDecimal("100.00")
                val writeCheck: Transaction = Transaction.Builder(
                    description = "groceries",
                    timestamp = clock.now(),
                    type = Type.expense,
                )
                    .apply {
                        with(
                            budgetData.categoryAccounts.find {
                                it.name == defaultFoodAccountName
                            }!!,
                        ) {
                            addItemBuilderTo(-amount)
                        }
                        with(
                            budgetData.draftAccounts.find {
                                it.name == defaultCheckingAccountName
                            }!!,
                        ) {
                            addItemBuilderTo(amount)
                        }
                    }
                    .build()
                commitTransactionConsistently(writeCheck, jdbcDao.transactionDao, budgetData)
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
                        defaultCosmeticsAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultEducationAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultEntertainmentAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultFoodAccountName -> it.balance shouldBe BigDecimal("200.00")
                        defaultHobbyAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultHomeAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultHousingAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultMedicalAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultNecessitiesAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultNetworkAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultTransportationAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultTravelAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        defaultWorkAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                        else -> fail("unexpected category account: $it")
                    }
                }
                budgetData.draftAccounts.forEach { it: DraftAccount ->
                    when (it.name) {
                        defaultCheckingAccountName -> it.balance shouldBe BigDecimal("100.00")
                        else -> fail("unexpected draft account: $it")
                    }
                }
            }
            "check clears" {
                val amount = BigDecimal("100.00")
                val writeCheck: Transaction = Transaction.Builder(
                    description = "groceries",
                    timestamp = clock.now(),
                    type = Type.clearing,
                )
                    .apply {
                        with(
                            budgetData.realAccounts.find {
                                it.name == defaultCheckingAccountName
                            }!!,
                        ) {
                            addItemBuilderTo(-amount)
                        }
                        with(
                            budgetData.draftAccounts.find {
                                it.name == defaultCheckingAccountName
                            }!!,
                        ) {
                            addItemBuilderTo(-amount)
                        }
                    }
                    .build()
                commitTransactionConsistently(writeCheck, jdbcDao.transactionDao, budgetData)
            }
            "check balances after check clears" {
                checkBalancesAfterCheckClears(budgetData)
            }
            "check balances in DB" {
                checkBalancesAfterCheckClears(jdbcDao.load(budgetData.id, userId))
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
        budgetData.categoryAccounts.size shouldBe 14
        budgetData.categoryAccounts.forEach { it: CategoryAccount ->
            when (it.name) {
                defaultGeneralAccountName -> it.balance shouldBe BigDecimal("700.00")
                defaultCosmeticsAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                defaultFoodAccountName -> it.balance shouldBe BigDecimal("200.00")
                defaultHobbyAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                defaultHomeAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                defaultHousingAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
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
                defaultCheckingAccountName -> it.balance shouldBe BigDecimal.ZERO.setScale(2)
                else -> fail("unexpected draft account: $it")
            }
        }
    }

}
