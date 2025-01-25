package bps.budget.persistence.migration

import bps.budget.BudgetConfigurations
import bps.budget.model.Account
import bps.budget.model.AccountType
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftStatus
import bps.budget.model.Transaction
import bps.budget.model.Transaction.Type
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.jdbc.JdbcDao
import bps.config.convertToPath
import bps.jdbc.JdbcFixture
import bps.jdbc.transactOrThrow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types.OTHER
import java.sql.Types.VARCHAR
import java.util.UUID

class DataMigrations {

    companion object : JdbcFixture {
        @JvmStatic
        fun main(args: Array<String>) {
            val argsList: List<String> = args.toList()
            val typeIndex = argsList.indexOfFirst { it == "-type" } + 1
            if ("-type" !in argsList || argsList.size <= typeIndex) {
                println("Usage: java DataMigrations -type <type>")
                println("Migration types: new-account_active_periods-table, move-to-single-account-table, customer-fix")
            } else {
                val configurations =
                    BudgetConfigurations(
                        sequenceOf(
                            "migrations.yml",
                            convertToPath("~/.config/bps-budget/migrations.yml"),
                        ),
                    )
                val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)
                val migrationType = argsList[typeIndex]
                with(jdbcDao.connection) {
                    when (migrationType) {
//                        "customer-fix" -> {
//                            customerFix(jdbcDao)
//                        }
//                        "add-transaction-item-id" -> {
//                            addTransactionItemIds(jdbcDao)
//                        }
                        "add-transaction-types" -> {
                            addTransactionTypes(jdbcDao)
                        }
//                        "move-to-single-account-table" -> {
//                            moveToSingleAccountTable(jdbcDao)
//                        }
//                        "new-account_active_periods-table" -> {
//                            addAccountActivePeriodTable(jdbcDao, configurations)
//                        }
                        else -> throw IllegalArgumentException("Unknown migration type: $migrationType")
                    }
                }
            }
        }

        data class TransactionDetail(
            val accountType: AccountType,
            val draftStatus: DraftStatus,
            val amount: BigDecimal,
        )

        data class TransactionInfo(
            var transactionId: UUID? = null,
            var description: String? = null,
            var clearedBy: UUID? = null,
            var budgetId: UUID? = null,
            val details: MutableList<TransactionDetail> = mutableListOf(),
        )

        private fun Connection.addTransactionTypes(jdbcDao: JdbcDao) {
            jdbcDao.use {
                transactOrThrow {
                    // TODO alter table add nullable id column
                    prepareStatement(
                        """
                            alter table transactions
                            add type varchar(20) not null default 'expense'
                        """.trimIndent(),
                    )
                        .use {
                            it.execute()
                        }
                    // TODO populate types
                    val allowances = mutableListOf<TransactionInfo>()
                    val incomes = mutableListOf<TransactionInfo>()
                    val clears = mutableListOf<TransactionInfo>()
                    val transfers = mutableListOf<TransactionInfo>()

                    prepareStatement(
                        """
                        |select t.id, t.description, t.budget_id, ti.draft_status, ti.account_id, a.type as account_type, ti.amount
                        |from transactions t
                        |join transaction_items ti
                        |  on t.id = ti.transaction_id
                        |    and t.budget_id = ti.budget_id
                        |join accounts a
                        |  on a.budget_id = t.budget_id
                        |    and a.id = ti.account_id
                        """.trimMargin(),
                    )
                        .use {
                            it.executeQuery()
                                .use { resultSet: ResultSet ->
                                    var transactionInfo: TransactionInfo? = null
                                    if (resultSet.next()) {
                                        transactionInfo = createNewTransactionInfo(resultSet)
                                        while (resultSet.next()) {
                                            val transactionId = resultSet.getUuid("id")!!
                                            if (transactionId == transactionInfo!!.transactionId) {
                                                transactionInfo.details
                                                    .add(
                                                        TransactionDetail(
                                                            AccountType.valueOf(resultSet.getString("account_type")!!),
                                                            DraftStatus.valueOf(resultSet.getString("draft_status")!!),
                                                            resultSet.getBigDecimal("amount")!!,
                                                        ),
                                                    )
                                            } else {
                                                distributeTransactionInfo(
                                                    transactionInfo = transactionInfo,
                                                    incomes = incomes,
                                                    allowances = allowances,
                                                    transfers = transfers,
                                                    clears = clears,
                                                )
                                                transactionInfo = createNewTransactionInfo(resultSet)
                                            }
                                        }
                                        distributeTransactionInfo(
                                            transactionInfo = transactionInfo,
                                            incomes = incomes,
                                            allowances = allowances,
                                            transfers = transfers,
                                            clears = clears,
                                        )
                                    }
                                }
                        }
                    updateTypes(incomes, Type.income)
                    updateTypes(clears, Type.clearing)
                    updateTypes(allowances, Type.allowance)
                    updateTypes(transfers, Type.transfer)
                    prepareStatement("alter table transactions alter type drop default").use { it.execute() }
                }
            }
        }

        private fun Connection.updateTypes(incomes: MutableList<TransactionInfo>, type: Type) {
            incomes.forEach { transactionInfo: TransactionInfo ->
                prepareStatement(
                    """
                                                        |update transactions
                                                        |set type = ?
                                                        |where budget_id = ?
                                                        |  and id = ?
                                                        """.trimMargin(),
                )
                    .use { statement ->
                        statement.setString(1, type.name)
                        statement.setUuid(2, transactionInfo.budgetId!!)
                        statement.setUuid(3, transactionInfo.transactionId!!)
                        if (statement.executeUpdate() != 1)
                            throw IllegalStateException(
                                """
                                                    |Should have updated a row!
                                                    |$transactionInfo
                                                """.trimMargin(),
                            )
                    }
            }
        }

        private fun createNewTransactionInfo(
            resultSet: ResultSet,
        ): TransactionInfo =
            TransactionInfo().apply {
                description = resultSet.getString("description")!!
                budgetId = resultSet.getUuid("budget_id")!!
                transactionId = resultSet.getUuid("id")!!
                details
                    .add(
                        TransactionDetail(
                            AccountType.valueOf(resultSet.getString("account_type")!!),
                            DraftStatus.valueOf(resultSet.getString("draft_status")!!),
                            resultSet.getBigDecimal("amount")!!,
                        ),
                    )
            }

        private fun distributeTransactionInfo(
            transactionInfo: TransactionInfo,
            incomes: MutableList<TransactionInfo>,
            allowances: MutableList<TransactionInfo>,
            transfers: MutableList<TransactionInfo>,
            clears: MutableList<TransactionInfo>,
        ) {
            val description: String = transactionInfo.description!!
            if (description.startsWith("allowance into '")) {
                allowances.add(transactionInfo)
            } else if (description.startsWith("transfer from '")) {
                transfers.add(transactionInfo)
            } else if (DraftStatus.clearing in transactionInfo
                    .details
                    .map { it.draftStatus }
            ) {
                clears.add(transactionInfo)
            } else if (description.startsWith("income into '") ||
                description.startsWith("initial balance in '") ||
                isItAnAdditionToACategoryAndAReal(transactionInfo)
            ) {
                incomes.add(transactionInfo)
            }
        }

        private fun isItAnAdditionToACategoryAndAReal(transactionInfo: TransactionInfo): Boolean =
            // two items
            transactionInfo.details.size == 2 &&
                    // one is a category
                    transactionInfo.details.find { it.accountType == AccountType.category } != null &&
                    // one is real or charge
                    transactionInfo.details.find {
                        it.accountType in listOf(
                            AccountType.charge,
                            AccountType.real,
                        )
                    } != null &&
                    // amounts are positive
                    transactionInfo.details.all { it.amount > BigDecimal.ZERO }

//        private fun Connection.addTransactionItemIds(jdbcDao: JdbcDao) {
//            jdbcDao.use {
//                transactOrThrow {
//                    // TODO alter table add nullable id column
//                    prepareStatement(
//                        """
//                            alter table transaction_items
//                            add id uuid null
//                        """.trimIndent(),
//                    )
//                        .use {
//                            it.execute()
//                        }
//                    // TODO populate ids
//                    buildList {
//                        prepareStatement("select * from transaction_items")
//                            .use {
//                                it.executeQuery()
//                                    .use { resultSet: ResultSet ->
//                                        val now = Clock.System.now()
//                                        while (resultSet.next()) {
//                                            val budgetId = resultSet.getUuid("budget_id")!!
//                                            add(
//                                                TransactionDao.ExtendedTransactionItem(
//                                                    item = Transaction.ItemBuilder(
//                                                        UUID.randomUUID(),
//                                                        amount = resultSet.getCurrencyAmount("amount"),
//                                                        description = resultSet.getString("description"),
//                                                        account = CategoryAccount(
//                                                            "",
//                                                            id = resultSet.getUuid("account_id")!!,
//                                                            budgetId = budgetId,
//                                                        ),
//                                                        draftStatus = DraftStatus.valueOf(
//                                                            resultSet.getString("draft_status")!!,
//                                                        ),
//                                                    ),
//                                                    transactionId = resultSet.getUuid("transaction_id")!!,
//                                                    transactionDescription = "",
//                                                    transactionTimestamp = now,
//                                                    transactionDao = jdbcDao.transactionDao,
//                                                    budgetId = budgetId,
//                                                    accountBalanceAfterItem = BigDecimal.ZERO.setScale(2),
//                                                ),
//                                            )
//                                        }
//                                    }
//                            }
//                    }
//                        .forEach { transactionItem: TransactionDao.ExtendedTransactionItem<*> ->
//                            prepareStatement(
//                                """
//                                                |update transaction_items
//                                                |set id = ?
//                                                |where budget_id = ?
//                                                |  and transaction_id = ?
//                                                |  and amount = ?
//                                                |  and account_id = ?
//                                                |  and draft_status = ?
//                                                |  and ${if (transactionItem.description == null) "description is null" else "description = ?"}
//                                                """.trimMargin(),
//                            )
//                                .use { statement ->
//                                    statement.setUuid(1, transactionItem.item.id)
//                                    statement.setUuid(2, transactionItem.budgetId)
//                                    statement.setUuid(3, transactionItem.transactionId)
//                                    statement.setBigDecimal(4, transactionItem.amount)
//                                    statement.setUuid(5, transactionItem.account.id)
//                                    statement.setString(6, transactionItem.item.draftStatus.name)
//                                    transactionItem.description
//                                        ?.let { description ->
//                                            statement.setString(7, description)
//                                        }
//                                    if (statement.executeUpdate() != 1)
//                                        throw IllegalStateException(
//                                            """
//                                            |Should have updated a row!
//                                            |$transactionItem
//                                        """.trimMargin(),
//                                        )
//                                }
//                        }
//                    // TODO alter table add not null unique to id and add primary key (id, budget_id)
//                    prepareStatement("alter table transaction_items alter id set not null").use { it.execute() }
//                    prepareStatement("alter table transaction_items add unique (id)").use { it.execute() }
//                    prepareStatement("alter table transaction_items add primary key  (id, budget_id)").use { it.execute() }
//                }
//            }
//        }

//        private fun Connection.moveToSingleAccountTable(jdbcDao: JdbcDao) {
//            jdbcDao.use {
//                transactOrThrow {
//                    prepareStatement(
//                        """
//        create table if not exists accounts
//        (
//            id                   uuid           not null unique,
//            name                 varchar(50)    not null,
//            type                 varchar(20)    not null,
//            description          varchar(110)   not null default '',
//            balance              numeric(30, 2) not null default 0.0,
//            companion_account_id uuid           null references accounts (id),
//            budget_id            uuid           not null references budgets (id),
//            primary key (id, budget_id),
//            unique (name, type, budget_id),
//            unique (companion_account_id, budget_id)
//        )
//                                            """.trimIndent(),
//                    )
//                        .use { statement ->
//                            statement.execute()
//                        }
//                    prepareStatement(
//                        """
//        create index if not exists accounts_by_type
//            on accounts (budget_id, type)
//                                            """.trimIndent(),
//                    )
//                        .use { statement ->
//                            statement.execute()
//                        }
//                    migrateAccountsToSingleTable(AccountType.category.name)
//                    migrateAccountsToSingleTable(AccountType.real.name)
//                    migrateAccountsToSingleTable(AccountType.charge.name)
//                    migrateAccountsToSingleTable(AccountType.draft.name)
//                    migrateActivePeriods()
//                    migrateTransactions()
//                }
//            }
//        }

//        private fun Connection.migrateActivePeriods() {
//            data class ActivePeriod(
//                val id: UUID,
//                val budgetId: UUID,
//                val startDateUtc: Instant,
//                val endDateUtc: Instant,
//                val categoryAccountId: UUID?,
//                val draftAccountId: UUID?,
//                val chargeAccountId: UUID?,
//                val realAccountId: UUID?,
//            )
//            buildList {
//                prepareStatement("select * from account_active_periods")
//                    .use { statement ->
//                        statement.executeQuery()
//                            .use { resultSet ->
//                                while (resultSet.next()) {
//                                    add(
//                                        ActivePeriod(
//                                            id = resultSet.getUuid("id")!!,
//                                            budgetId = resultSet.getUuid("budget_id")!!,
//                                            startDateUtc = resultSet.getInstant("start_date_utc"),
//                                            endDateUtc = resultSet.getInstant("end_date_utc"),
//                                            categoryAccountId = resultSet.getUuid("category_account_id"),
//                                            draftAccountId = resultSet.getUuid("draft_account_id"),
//                                            chargeAccountId = resultSet.getUuid("charge_account_id"),
//                                            realAccountId = resultSet.getUuid("real_account_id"),
//                                        ),
//                                    )
//                                }
//                            }
//                    }
//            }
//                .let { activePeriods ->
//                    // TODO delete table and recreate with new rows
//                    prepareStatement(
//                        """
//                    create table if not exists account_active_periods_temp
//                    (
//                        id             uuid      not null unique,
//                        start_date_utc timestamp not null default '0001-01-01T00:00:00Z',
//                        end_date_utc   timestamp not null default '9999-12-31T23:59:59.999Z',
//                        account_id     uuid      not null references accounts (id),
//                        budget_id      uuid      not null references budgets (id),
//                        unique (start_date_utc, account_id, budget_id)
//                    )
//                """.trimIndent(),
//                    )
//                        .use {
//                            it.executeUpdate()
//                        }
//                    prepareStatement(
//                        """
//create index if not exists lookup_account_active_periods_by_account_id
//    on account_active_periods_temp (account_id, budget_id)
//                        """.trimIndent(),
//                    )
//                        .use {
//                            it.executeUpdate()
//                        }
//                    // TODO copy this data into it
//                    activePeriods.forEach { activePeriod: ActivePeriod ->
//                        prepareStatement(
//                            """
//                            insert into account_active_periods_temp (id, start_date_utc, end_date_utc, account_id, budget_id)
//                            values (?, ?, ?, ?, ?)
//                        """.trimIndent(),
//                        )
//                            .use { statement ->
//                                statement.setUuid(1, activePeriod.id)
//                                statement.setTimestamp(2, activePeriod.startDateUtc)
//                                statement.setTimestamp(3, activePeriod.endDateUtc)
//                                statement.setUuid(
//                                    4,
//                                    (activePeriod.realAccountId
//                                        ?: activePeriod.draftAccountId
//                                        ?: activePeriod.chargeAccountId
//                                        ?: activePeriod.categoryAccountId)!!,
//                                )
//                                statement.setUuid(5, activePeriod.budgetId)
//                                statement.executeUpdate()
//                            }
//                    }
//                    prepareStatement("drop table if exists account_active_periods")
//                        .use { statement ->
//                            statement.execute()
//                        }
//                    prepareStatement("alter table if exists account_active_periods_temp rename to account_active_periods")
//                        .use { statement ->
//                            statement.execute()
//                        }
//                }
//        }

//        private fun Connection.migrateTransactions() {
//            data class TransactionItem(
//                val id: UUID,
//                val transactionId: UUID,
//                val budgetId: UUID,
//                val description: String?,
//                val amount: BigDecimal,
//                val draftStatus: String?,
//                val categoryAccountId: UUID?,
//                val draftAccountId: UUID?,
//                val chargeAccountId: UUID?,
//                val realAccountId: UUID?,
//            )
//            buildList {
//                prepareStatement("select * from transaction_items")
//                    .use { statement ->
//                        statement.executeQuery()
//                            .use { resultSet ->
//                                while (resultSet.next()) {
//                                    add(
//                                        TransactionItem(
//                                            id = resultSet.getUuid("id")!!,
//                                            transactionId = resultSet.getUuid("transaction_id")!!,
//                                            budgetId = resultSet.getUuid("budget_id")!!,
//                                            description = resultSet.getString("description"),
//                                            amount = resultSet.getCurrencyAmount("amount"),
//                                            draftStatus = resultSet.getString("draft_status"),
//                                            categoryAccountId = resultSet.getUuid("category_account_id"),
//                                            draftAccountId = resultSet.getUuid("draft_account_id"),
//                                            chargeAccountId = resultSet.getUuid("charge_account_id"),
//                                            realAccountId = resultSet.getUuid("real_account_id"),
//                                        ),
//                                    )
//                                }
//                            }
//                    }
//            }
//                .let { transactionItems ->
//                    // TODO delete table and recreate with new rows
//                    prepareStatement(
//                        """
//create table if not exists transaction_items_temp
//(
//    id                  uuid           not null unique,
//    transaction_id      uuid           not null references transactions (id),
//    description         varchar(110)   null,
//    amount              numeric(30, 2) not null,
//    account_id uuid          not null references accounts (id),
//    draft_status        varchar        not null default 'none', -- 'none' 'outstanding' 'cleared'
//    budget_id           uuid           not null references budgets (id)
//)
//                """.trimIndent(),
//                    )
//                        .use {
//                            it.executeUpdate()
//                        }
//                    prepareStatement(
//                        """
//create index if not exists lookup_transaction_items_by_transaction
//    on transaction_items_temp (transaction_id, budget_id)
//                        """.trimIndent(),
//                    )
//                        .use { it.executeUpdate() }
//                    // TODO copy this data into it
//                    transactionItems.forEach { transactionItem: TransactionItem ->
//                        prepareStatement(
//                            """
//                            insert into transaction_items_temp (transaction_id, description, amount, account_id, draft_status, budget_id, id)
//                            values (?, ?, ?, ?, ?, ?, ?)
//                        """.trimIndent(),
//                        )
//                            .use { statement ->
//                                statement.setUuid(1, transactionItem.transactionId)
//                                transactionItem.description
//                                    ?.let { statement.setString(2, it) }
//                                    ?: statement.setNull(2, VARCHAR)
//                                statement.setBigDecimal(3, transactionItem.amount)
//                                statement.setUuid(
//                                    4,
//                                    (transactionItem.realAccountId
//                                        ?: transactionItem.draftAccountId
//                                        ?: transactionItem.chargeAccountId
//                                        ?: transactionItem.categoryAccountId)!!,
//                                )
//                                transactionItem.draftStatus
//                                    ?.let { statement.setString(5, it) }
//                                    ?: statement.setNull(5, VARCHAR)
//                                statement.setUuid(6, transactionItem.budgetId)
//                                statement.setUuid(7, UUID.randomUUID())
//                                statement.executeUpdate()
//                            }
//                    }
//                    prepareStatement("drop table if exists transaction_items")
//                        .use {
//                            it.executeUpdate()
//                        }
//                    prepareStatement("alter table if exists transaction_items_temp rename to transaction_items")
//                        .use {
//                            it.executeUpdate()
//                        }
//                }
//        }

//        private fun Connection.migrateAccountsToSingleTable(type: String) {
//            buildList {
//                prepareStatement("select * from ${type}_accounts")
//                    .use { statement ->
//                        statement
//                            .executeQuery()
//                            .use { resultSet ->
//                                while (resultSet.next()) {
//                                    val budgetId = resultSet.getUuid("budget_id")!!
//                                    add(
//                                        object : Account(
//                                            name = resultSet.getString("name"),
//                                            description = resultSet.getString("description"),
//                                            id = resultSet.getUuid("id")!!,
//                                            balance = resultSet.getCurrencyAmount("balance"),
//                                            type = type,
//                                            budgetId = budgetId,
//                                        ) {} to
//                                                if (type == "draft")
//                                                    resultSet.getUuid("real_account_id")
//                                                else
//                                                    null,
//                                    )
//                                }
//                            }
//                    }
//            }
//                .forEach { (account, companionId) ->
//                    prepareStatement(
//                        """
//                |insert into accounts (id, name, description, type, companion_account_id, budget_id, balance)
//                |VALUES (?, ?, ?, ?, ?, ?, ?)
//                |on conflict do nothing
//                """.trimMargin(),
//                    )
//                        .use { statement ->
//                            statement.setUuid(1, account.id)
//                            statement.setString(2, account.name)
//                            statement.setString(3, account.description)
//                            statement.setString(4, type)
//                            companionId
//                                ?.let {
//                                    statement.setUuid(5, it)
//                                }
//                                ?: statement.setNull(5, OTHER)
//                            statement.setUuid(6, account.budgetId)
//                            statement.setBigDecimal(7, account.balance)
//                            statement.executeUpdate()
//                        }
//                }
//        }

//        private fun Connection.migrateAccountsToActivityPeriodTable(tablePrefix: String, budgetId: UUID) {
//            buildList {
//                prepareStatement("select id from ${tablePrefix}_accounts where budget_id = ?")
//                    .use { statement ->
//                        statement.setUuid(1, budgetId)
//                        statement.executeQuery()
//                            .use { resultSet ->
//                                while (resultSet.next()) {
//                                    add(resultSet.getUuid("id")!!)
//                                }
//                            }
//                    }
//            }
//                .let { accountIds: List<UUID> ->
//                    accountIds.forEach { accountId ->
//                        prepareStatement(
//                            """
//                    insert into account_active_periods (id, ${tablePrefix}_account_id, budget_id)
//                    values (?, ?, ?)
//                    on conflict do nothing
//                        """.trimIndent(),
//                        )
//                            .use { statement ->
//                                statement.setUuid(1, UUID.randomUUID())
//                                statement.setUuid(2, accountId)
//                                statement.setUuid(3, budgetId)
//                                statement.executeUpdate()
//                            }
//
//                    }
//                }
//        }

//        private fun Connection.addAccountActivePeriodTable(
//            jdbcDao: JdbcDao,
//            configurations: BudgetConfigurations,
//        ) {
//            jdbcDao.use {
//                try {
//                    transactOrThrow {
//                        prepareStatement(
//                            """
//                                    select ba.budget_id
//                                    from users u
//                                    join budget_access ba on u.id = ba.user_id
//                                    where ba.budget_name = ?
//                                    and u.login = ?
//                                    """.trimIndent(),
//                        )
//                            .use { statement ->
//                                statement.setString(1, configurations.persistence.jdbc!!.budgetName)
//                                statement.setString(2, configurations.user.defaultLogin)
//                                statement.executeQuery().use { resultSet ->
//                                    resultSet.next()
//                                    val budgetId: UUID = resultSet.getUuid("budget_id")!!
//                                    migrateAccountsToActivityPeriodTable(AccountType.category.name, budgetId)
//                                    migrateAccountsToActivityPeriodTable(AccountType.real.name, budgetId)
//                                    migrateAccountsToActivityPeriodTable(AccountType.charge.name, budgetId)
//                                    migrateAccountsToActivityPeriodTable(AccountType.draft.name, budgetId)
//                                }
//                            }
//                    }
//                } catch (ex: Throwable) {
//                    ex.printStackTrace()
//                }
//            }
//        }

    }

}

//        private fun Connection.customerFix(jdbcDao: JdbcDao) {
//            jdbcDao.use { dao ->
//                transactOrThrow {
//                    val budgetId = UUID.fromString("91eca65d-7c6d-46dd-b3a2-1eb992b4bf83")
//                    val budgetData = dao.load(
//                        budgetId,
//                        UUID.fromString("ed7a8350-f108-458e-b492-5564cf455aee"),
//                    )
//                    val hongyiCash = budgetData.realAccounts.find { it.name == "Hongyi's Cash" }!!
//                    val wallet = budgetData.realAccounts.find { it.name == "Wallet" }!!
//                    dao.commit(
//                        Transaction.Builder(
//                            "transfer from '${hongyiCash.name}' to '${wallet.name}'",
//                            Clock.System.now(),
//                        )
//                            .apply {
//                                val amount = BigDecimal("40.00").setScale(2)
//                                with(hongyiCash) {
//                                    addItemBuilderTo(-amount)
//                                }
//                                with(wallet) {
//                                    addItemBuilderTo(amount)
//                                }
//                            }
//                            .build(),
//                        budgetId, false,
//                    )
//                }
//            }
//        }
