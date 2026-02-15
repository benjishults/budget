package bps.budget

import bps.jdbc.JdbcFixture
import bps.jdbc.JdbcFixture.Companion.transactOrThrow
import java.sql.Statement
import javax.sql.DataSource

// TODO this class is doing too much... could be split up... See how it's done in server.
class JdbcInitializingBudgetDao(
    val budgetName: String,
    val dataSource: DataSource,
) : InitializingBudgetDao, JdbcFixture {

    override fun ensureTablesAndIndexes() {
        dataSource.transactOrThrow {
            createStatement()
                .use { createStatement: Statement ->
                    createStatement.executeUpdate(
                        """
create table if not exists users
(
    id    uuid         not null primary key,
    login varchar(110) not null unique
)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create table if not exists budgets
(
    id                   uuid        not null primary key,
    general_account_name varchar(50) not null default 'General'
)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create table if not exists budget_access
(
    id              uuid         not null primary key,
    user_id         uuid         not null references users (id),
    budget_id       uuid         not null references budgets (id),
    budget_name     varchar(110) not null,
    time_zone       varchar(110) not null,
    analytics_start timestamp    not null default now(),
    -- if null, check fine_access
    coarse_access   varchar,
    unique (user_id, budget_id),
    unique (user_id, budget_name)
)                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create index if not exists lookup_budget_access_by_user
    on budget_access (user_id)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create table if not exists accounts
(
    id                   uuid           not null unique,
    name                 varchar(50)    not null,
    type                 varchar(20)    not null,
    description          varchar(110)   not null default '',
    balance              numeric(30, 2) not null default 0.0,
    companion_account_id uuid           null references accounts (id),
    budget_id            uuid           not null references budgets (id),
    primary key (id, budget_id),
    unique (name, type, budget_id),
    unique (companion_account_id, budget_id)
)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create table if not exists account_active_periods
(
    id             uuid      not null unique,
    start_date_utc timestamp not null default '0001-01-01T00:00:00Z',
    end_date_utc   timestamp not null default '9999-12-31T23:59:59.999Z',
    account_id     uuid      not null references accounts (id),
    budget_id      uuid      not null references budgets (id),
    unique (start_date_utc, account_id, budget_id)
)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create index if not exists lookup_account_active_periods_by_account_id
    on account_active_periods (account_id, budget_id)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create table if not exists transactions
(
    id                        uuid         not null unique,
    description               varchar(110) not null default '',
    timestamp_utc             timestamp    not null default now(),
    type                      varchar(20)  not null,
    -- the transaction that clears this transaction
    cleared_by_transaction_id uuid         null,
    budget_id                 uuid         not null references budgets (id),
    primary key (id, budget_id)
)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create index if not exists lookup_transaction_by_date
    on transactions (timestamp_utc desc, budget_id)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create index if not exists lookup_transaction_by_type_and_date
    on transactions (timestamp_utc desc, type, budget_id)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create table if not exists transaction_items
(
    id             uuid           not null unique,
    transaction_id uuid           not null references transactions (id),
    description    varchar(110)   null,
    amount         numeric(30, 2) not null,
    account_id     uuid           not null references accounts (id),
    draft_status   varchar        not null default 'none', -- 'none' 'outstanding' 'cleared'
    budget_id      uuid           not null references budgets (id)
)
                """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create index if not exists lookup_transaction_items_by_account
    on transaction_items (account_id, budget_id)
                    """.trimIndent(),
                    )
                }
        }
    }

    override fun close() {
        super.close()
    }

}
