package bps.budget.persistence.jdbc

import bps.budget.data.BudgetData
import bps.budget.model.Account
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.DataConfigurationException
import bps.budget.persistence.JdbcConfig
import bps.budget.transaction.Transaction
import bps.budget.transaction.TransactionItem
import bps.jdbc.JdbcFixture
import java.math.BigDecimal
import java.net.URLEncoder
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class JdbcDao(
    override val config: JdbcConfig,
) : BudgetDao<JdbcConfig>, JdbcFixture {

    private var jdbcURL: String =
        "jdbc:${config.dbProvider}://${config.host}:${config.port}/${
            URLEncoder.encode(
                config.database,
                "utf-8",
            )
        }?currentSchema=${URLEncoder.encode(config.schema, "utf-8")}"

    @Volatile
    override var connection: Connection = startConnection()
        private set
    private val keepAliveSingleThreadScheduledExecutor = Executors
        .newSingleThreadScheduledExecutor()

    init {
        // NOTE keep the connection alive with an occasional call to `isValid`.
        keepAliveSingleThreadScheduledExecutor
            .apply {
                scheduleWithFixedDelay(
                    {
                        if (!connection.isValid(4_000)) {
//                                println("DB connection died.  Restarting...")
                            connection = startConnection()
                        }
                    },
                    5_000,
                    20_000,
                    TimeUnit.SECONDS,
                )

            }
    }

    private fun startConnection(): Connection =
        DriverManager
            .getConnection(
                jdbcURL,
                config.user ?: System.getenv("BUDGET_JDBC_USER"),
                config.password ?: System.getenv("BUDGET_JDBC_PASSWORD"),
            )
            .apply {
                autoCommit = false
            }

    override fun prepForFirstLoad() {
        transaction {
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists budgets
(
    general_account_id uuid         not null unique, --  references category_accounts (id)
    budget_name        varchar(110) not null primary key
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists staged_category_accounts
(
    id          uuid           not null,
    name        varchar(50)    not null,
    description varchar(110)   not null default '',
    balance     numeric(30, 2) not null default 0.0,
    budget_name varchar(110)   not null
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists category_accounts
(
    id          uuid           not null unique,
    name        varchar(50)    not null,
    description varchar(110)   not null default '',
    balance     numeric(30, 2) not null default 0.0,
    budget_name varchar(110)   not null references budgets (budget_name),
    primary key (id, budget_name),
    unique (name, budget_name)
)
""".trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists staged_real_accounts
(
    id          uuid           not null,
    name        varchar(50)    not null,
    description varchar(110)   not null default '',
    balance     numeric(30, 2) not null default 0.0,
    budget_name varchar(110)   not null
)
""".trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists real_accounts
(
    id          uuid           not null unique,
    name        varchar(50)    not null,
    description varchar(110)   not null default '',
    balance     numeric(30, 2) not null default 0.0,
    budget_name varchar(110)   not null references budgets (budget_name),
    primary key (id, budget_name),
    unique (name, budget_name)
)
""".trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists staged_draft_accounts
(
    id              uuid           not null,
    name            varchar(50)    not null,
    description     varchar(110)   not null default '',
    balance         numeric(30, 2) not null default 0.0,
    real_account_id uuid           not null,
    budget_name     varchar(110)   not null
)
""".trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists draft_accounts
(
    id              uuid           not null unique,
    name            varchar(50)    not null,
    description     varchar(110)   not null default '',
    balance         numeric(30, 2) not null default 0.0,
    real_account_id uuid           not null references real_accounts (id),
    budget_name     varchar(110)   not null references budgets (budget_name),
    primary key (id, budget_name),
    unique (name, budget_name),
    unique (real_account_id, budget_name)
)
""".trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists transactions
(
    id          uuid                     not null unique,
    description varchar(110)             not null default '',
    timestamp   timestamp with time zone not null default now(),
    budget_name varchar(110)             not null references budgets (budget_name),
    primary key (id, budget_name),
    unique (timestamp, budget_name)
)
""".trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists transaction_items
(
    transaction_id      uuid           not null references transactions (id),
    description         varchar(110)   null,
    amount              numeric(30, 2) not null,
    category_account_id uuid           null references category_accounts (id),
    real_account_id     uuid           null references real_accounts (id),
    draft_account_id    uuid           null references draft_accounts (id),
    budget_name         varchar(110)   not null references budgets (budget_name),
    constraint only_one_account_per_transaction_item check
        ((real_account_id is not null and (category_account_id is null and draft_account_id is null)) or
         (category_account_id is not null and (real_account_id is null and draft_account_id is null)) or
         (draft_account_id is not null and (category_account_id is null and real_account_id is null))),
    unique (transaction_id, draft_account_id, budget_name),
    unique (transaction_id, category_account_id, budget_name),
    unique (transaction_id, real_account_id, budget_name)
)
                """.trimIndent(),
                )
            }
        }
    }

    /**
     * Just loads top-level account info.  Details of transactions are loaded on-demand.
     * @throws DataConfigurationException if data isn't found.
     */
    override fun load(): BudgetData =
        try {
            transaction(
                onRollback = { ex ->
                    if (ex.message?.contains("category_accounts") == true && ex.message?.contains("not exist") == true)
                    // TODO fix it right now
                        throw DataConfigurationException("tables do not exist", ex)
                    else
                        throw DataConfigurationException(ex.message, ex)
                },
            ) {
                val generalAccount =
                    prepareStatement(
                        """
                select * from category_accounts c
                join budgets b
                on c.budget_name = b.budget_name
                and c.id = b.general_account_id
                where b.budget_name = ?""".trimIndent(),
                    )
                        .use { getBudget: PreparedStatement ->
                            getBudget.setString(1, config.budgetName)
                            getBudget.executeQuery()
                                .use { result: ResultSet ->
                                    result.next()
                                        .also {
                                            if (!it)
                                                throw DataConfigurationException("Budget data not found for name: ${config.budgetName}")
                                        }
                                    CategoryAccount(
                                        result.getString("name"),
                                        result.getString("description"),
                                        result.getObject("id", UUID::class.java),
                                        result.getCurrencyAmount("balance"),
                                    )
                                }
                        }
                val categoryAccounts: List<CategoryAccount> =
                    prepareStatement("select * from category_accounts where budget_name = ?")
                        .use { getCategoryAccounts: PreparedStatement ->
                            getCategoryAccounts.setString(1, config.budgetName)
                            getCategoryAccounts.executeQuery()
                                .use { result ->
                                    buildList {
                                        while (result.next()) {
                                            add(
                                                CategoryAccount(
                                                    result.getString("name"),
                                                    result.getString("description"),
                                                    result.getObject("id", UUID::class.java),
                                                    result.getCurrencyAmount("balance"),
                                                ),
                                            )
                                        }
                                    }
                                }
                        }
                val realAccounts: List<RealAccount> =
                    prepareStatement("select * from real_accounts where budget_name = ?")
                        .use { getRealAccounts ->
                            getRealAccounts.setString(1, config.budgetName)
                            getRealAccounts.executeQuery()
                                .use { result ->
                                    buildList {
                                        while (result.next()) {
                                            add(
                                                RealAccount(
                                                    result.getString("name"),
                                                    result.getString("description"),
                                                    result.getObject("id", UUID::class.java),
                                                    result.getCurrencyAmount("balance"),
                                                ),
                                            )
                                        }
                                    }
                                }
                        }

                val draftAccounts: List<DraftAccount> =
                    prepareStatement("select * from draft_accounts where budget_name = ?")
                        .use { getDraftAccounts ->
                            getDraftAccounts.setString(1, config.budgetName)
                            getDraftAccounts.executeQuery()
                                .use { result ->
                                    buildList {
                                        while (result.next()) {
                                            add(
                                                DraftAccount(
                                                    result.getString("name"),
                                                    result.getString("description"),
                                                    result.getObject("id", UUID::class.java),
                                                    result.getCurrencyAmount("balance"),
                                                    realAccounts.find {
                                                        it.id.toString() == result.getString(
                                                            "real_account_id",
                                                        )
                                                    }!!,
                                                ),
                                            )
                                        }
                                    }
                                }
                        }
                BudgetData(
                    generalAccount,
                    categoryAccounts,
                    realAccounts,
                    draftAccounts,
                )
            }
        } catch (ex: Exception) {
            if (ex is DataConfigurationException) {
                throw ex
            } else
                throw DataConfigurationException(ex)
        }
            ?: throw DataConfigurationException("Transaction rolled back.")

    /**
     * Inserts the transaction records and updates the account balances in the DB.
     */
    override fun commit(transaction: Transaction) {
        check(transaction.validate())
        transaction {
            insertTransactionPreparedStatement(transaction)
                .use { insertTransaction: PreparedStatement ->
                    insertTransaction.executeUpdate()
                }
            insertTransactionItemsPreparedStatement(transaction)
                .use { insertTransactionItem: PreparedStatement ->
                    insertTransactionItem.executeUpdate()
                }
            updateBalances(transaction)
        }
    }

    private fun Connection.updateBalances(transaction: Transaction) {
        val categoryAccountUpdateStatement = """
            update category_accounts
            set balance = balance + ?
            where id = ? and budget_name = ?""".trimIndent()
        val realAccountUpdateStatement = """
            update real_accounts
            set balance = balance + ?
            where id = ? and budget_name = ?""".trimIndent()
        val draftAccountUpdateStatement = """
            update draft_accounts
            set balance = balance + ?
            where id = ? and budget_name = ?""".trimIndent()
        buildMap {
            put(
                categoryAccountUpdateStatement,
                buildList {
                    transaction.categoryItems.forEach { transactionItem: TransactionItem ->
                        add(
                            transactionItem.categoryAccount!!.id to
                                    transactionItem.amount,
                        )
                    }
                },
            )
            put(
                realAccountUpdateStatement,
                buildList {
                    transaction.realItems.forEach { transactionItem: TransactionItem ->
                        add(
                            transactionItem.realAccount!!.id to
                                    transactionItem.amount,
                        )
                    }
                },
            )
            put(
                draftAccountUpdateStatement,
                buildList {
                    transaction.draftItems.forEach { transactionItem: TransactionItem ->
                        add(
                            transactionItem.draftAccount!!.id to
                                    transactionItem.amount,
                        )
                    }
                },
            )
        }
            .forEach { (statement: String, accountIdAmountPair: List<Pair<UUID, BigDecimal>>) ->
                prepareStatement(statement).use { preparedStatement: PreparedStatement ->
                    accountIdAmountPair.forEach { (id, amount) ->
                        preparedStatement.setBigDecimal(1, amount)
                        preparedStatement.setObject(2, id)
                        preparedStatement.setString(3, config.budgetName)
                        preparedStatement.executeUpdate()
                    }
                }
            }
    }

    private fun Connection.insertTransactionItemsPreparedStatement(transaction: Transaction): PreparedStatement {
        val transactionItemCounter =
            transaction.categoryItems.size + transaction.realItems.size + transaction.draftItems.size
        val statement = buildString {
            var counter = transactionItemCounter
            append("insert into transaction_items (transaction_id, description, amount, budget_name, category_account_id, real_account_id, draft_account_id) values ")
            if (counter-- > 0) {
                append("(?, ?, ?, ?, ?, ?, ?)")
                while (counter-- > 0) {
                    append(", (?, ?, ?, ?, ?, ?, ?)")
                }
            }
        }
        var parameterIndex = 1
        val transactionItemInsert = prepareStatement(statement)
        transaction.categoryItems.forEach { transactionItem: TransactionItem ->
            parameterIndex += setStandardProperties(transactionItemInsert, parameterIndex, transaction, transactionItem)
            transactionItemInsert.setObject(parameterIndex++, transactionItem.categoryAccount!!.id)
            transactionItemInsert.setNull(parameterIndex++, Types.OTHER)
            transactionItemInsert.setNull(parameterIndex++, Types.OTHER)
        }
        transaction.realItems.forEach { transactionItem: TransactionItem ->
            parameterIndex += setStandardProperties(transactionItemInsert, parameterIndex, transaction, transactionItem)
            transactionItemInsert.setNull(parameterIndex++, Types.OTHER)
            transactionItemInsert.setObject(parameterIndex++, transactionItem.realAccount!!.id)
            transactionItemInsert.setNull(parameterIndex++, Types.OTHER)
        }
        transaction.draftItems.forEach { transactionItem: TransactionItem ->
            parameterIndex += setStandardProperties(transactionItemInsert, parameterIndex, transaction, transactionItem)
            transactionItemInsert.setNull(parameterIndex++, Types.OTHER)
            transactionItemInsert.setNull(parameterIndex++, Types.OTHER)
            transactionItemInsert.setObject(parameterIndex++, transactionItem.draftAccount!!.id)
        }
        return transactionItemInsert
    }

    /**
     * Sets a set of standard parameter starting at [parameterIndex]
     * @return the number of parameters set.
     */
    private fun setStandardProperties(
        transactionItemInsert: PreparedStatement,
        parameterIndex: Int,
        transaction: Transaction,
        transactionItem: TransactionItem,
    ): Int {
        transactionItemInsert.setObject(parameterIndex, transaction.id)
        transactionItemInsert.setString(parameterIndex + 1, transactionItem.description)
        transactionItemInsert.setBigDecimal(parameterIndex + 2, transactionItem.amount)
        transactionItemInsert.setString(parameterIndex + 3, config.budgetName)
        return 4
    }

    private fun Connection.insertTransactionPreparedStatement(transaction: Transaction): PreparedStatement {
        val insertTransaction: PreparedStatement = prepareStatement(
            """
                insert into transactions (id, description, timestamp, budget_name) VALUES
                (?, ?, ?, ?)
            """.trimIndent(),
        )
        insertTransaction.setObject(1, transaction.id)
        insertTransaction.setString(2, transaction.description)
        insertTransaction.setObject(3, transaction.timestamp)
        insertTransaction.setString(4, config.budgetName)
        return insertTransaction
    }

    override fun save(data: BudgetData) {
        check(data.validate())
        // create budget if it isn't there
        transaction {
            val generalAccountId = data.generalAccount.id
            prepareStatement(
                """
                insert into budgets (general_account_id, budget_name)
                values (?, ?) on conflict do nothing """,
            )
                .use { createBudgetStatement: PreparedStatement ->
                    createBudgetStatement.setObject(1, generalAccountId)
                    createBudgetStatement.setString(2, config.budgetName)
                    createBudgetStatement.executeUpdate()
                    // TODO set up logging to a file and have the file printed to console on quit
//                        .also { if (it == 0)  }
                }
            // create accounts that aren't there and update those that are
            upsertAccountData(data.categoryAccounts, "category_accounts")
            upsertAccountData(data.realAccounts, "real_accounts")
//            upsertAccountData(data.draftAccounts, "draft_accounts_name_budget_name_key")
            data.draftAccounts.forEach { draftAccount: DraftAccount ->
                prepareStatement(
                    """
                insert into staged_draft_accounts (id, name, description, balance, real_account_id, budget_name)
                VALUES (?, ?, ?, ?, ?, ?)
                on conflict do nothing
            """.trimIndent(),
                )
                    .use { createStagedAccountStatement: PreparedStatement ->
                        createStagedAccountStatement.setObject(1, draftAccount.id)
                        createStagedAccountStatement.setString(2, draftAccount.name)
                        createStagedAccountStatement.setString(3, draftAccount.description)
                        createStagedAccountStatement.setBigDecimal(4, draftAccount.balance)
                        createStagedAccountStatement.setObject(5, draftAccount.realCompanion.id)
                        createStagedAccountStatement.setString(6, config.budgetName)
                        createStagedAccountStatement.executeUpdate()
                    }
                prepareStatement(
                    """
                merge into draft_accounts as t
                    using staged_draft_accounts as s
                    on (t.id = s.id or t.name = s.name) and t.budget_name = s.budget_name
                    when matched then
                        update
                        set name = s.name,
                            description = s.description,
                            balance = s.balance
                    when not matched then
                        insert (id, name, description, balance, real_account_id, budget_name)
                        values (s.id, s.name, s.description, s.balance, s.real_account_id, s.budget_name);
                """.trimIndent(),
                )
                    .use { createAccountStatement: PreparedStatement ->
                        createAccountStatement.executeUpdate()
                    }
                // NOTE may have to do this later all at once at the end?
                prepareStatement("delete from staged_draft_accounts where (id = ? or name = ?) and budget_name = ?")
                    .use { preparedStatement: PreparedStatement ->
                        preparedStatement.setObject(1, draftAccount.id)
                        preparedStatement.setString(2, draftAccount.name)
                        preparedStatement.setString(3, config.budgetName)
                        preparedStatement.executeUpdate()
                    }
//                prepareStatement(
//                    """
//                    insert into draft_accounts (id, name, description, balance, real_account_id, budget_name)
//                    values (?, ?, ?, ?, ?, ?) on conflict on constraint draft_accounts_pkey do update set name = ?, description = ?, balance = ?""",
//                )
//                    .use { createAccountStatement: PreparedStatement ->
//                        createAccountStatement.setObject(1, draftAccount.id)
//                        createAccountStatement.setString(2, draftAccount.name)
//                        createAccountStatement.setString(3, draftAccount.description)
//                        createAccountStatement.setBigDecimal(4, draftAccount.balance)
//                        createAccountStatement.setObject(5, draftAccount.realCompanion.id)
//                        createAccountStatement.setString(6, config.budgetName)
//                        createAccountStatement.setString(7, draftAccount.name)
//                        createAccountStatement.setString(8, draftAccount.description)
//                        createAccountStatement.setBigDecimal(9, draftAccount.balance)
//                        createAccountStatement.executeUpdate()
//                    }
            }
            // TODO mark deleted accounts as in-active
            //      see account_active_periods which is currently unused
        }
    }

    private fun Connection.upsertAccountData(accounts: List<Account>, tableName: String) {
        accounts.forEach { account ->
            prepareStatement(
                """
                insert into staged_$tableName (id, name, description, balance, budget_name)
                VALUES (?, ?, ?, ?, ?)
                on conflict do nothing
            """.trimIndent(),
            )
                .use { createStagedAccountStatement: PreparedStatement ->
                    createStagedAccountStatement.setObject(1, account.id)
                    createStagedAccountStatement.setString(2, account.name)
                    createStagedAccountStatement.setString(3, account.description)
                    createStagedAccountStatement.setBigDecimal(4, account.balance)
                    createStagedAccountStatement.setString(5, config.budgetName)
                    createStagedAccountStatement.executeUpdate()
                }
            prepareStatement(
                """
                merge into $tableName as t
                    using staged_$tableName as s
                    on (t.id = s.id or t.name = s.name) and t.budget_name = s.budget_name
                    when matched then
                        update
                        set name = s.name,
                            description = s.description,
                            balance = s.balance
                    when not matched then
                        insert (id, name, description, balance, budget_name)
                        values (s.id, s.name, s.description, s.balance, s.budget_name);
                """.trimIndent(),
            )
                .use { createAccountStatement: PreparedStatement ->
                    createAccountStatement.executeUpdate()
                }
            // NOTE may have to do this later all at once at the end?
            prepareStatement("delete from staged_$tableName where (id = ? or name = ?) and budget_name = ?")
                .use { preparedStatement: PreparedStatement ->
                    preparedStatement.setObject(1, account.id)
                    preparedStatement.setString(2, account.name)
                    preparedStatement.setString(3, config.budgetName)
                    preparedStatement.executeUpdate()
                }
        }
    }

    override fun close() {
        keepAliveSingleThreadScheduledExecutor.close()
        super.close()
    }

}
