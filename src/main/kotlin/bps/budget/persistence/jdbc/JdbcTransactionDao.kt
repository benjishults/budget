package bps.budget.persistence.jdbc

import bps.budget.model.Account
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftAccount
import bps.budget.model.DraftStatus
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.model.Transaction.Type
import bps.budget.persistence.AccountDao
import bps.budget.persistence.TransactionDao
import bps.jdbc.JdbcFixture
import bps.jdbc.transactOrThrow
import bps.kotlin.Instrumentable
import kotlinx.datetime.Instant
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

@Instrumentable
class JdbcTransactionDao(
    val connection: Connection,
    val errorStateTracker: JdbcDao.ErrorStateTracker,
    val accountDao: AccountDao,
) : TransactionDao, JdbcFixture {

    /**
     * 1. Sets the [Transaction.Item.draftStatus] to [DraftStatus.cleared] for each [draftTransactionItems]
     * 2. Sets the "cleared-by" relation in the DB.
     * 3. Commits the new [clearingTransaction]
     * 4. Updates account balances
     * @throws IllegalArgumentException if either the [draftTransactionItems] or the [clearingTransaction] is not what
     * we expect
     */
    override fun clearCheck(
        draftTransactionItems: List<Transaction.Item<DraftAccount>>,
        clearingTransaction: Transaction,
        budgetId: UUID,
    ) =
        errorStateTracker.catchCommitErrorState {
            // require clearTransaction is a simple draft transaction(s) clearing transaction
            // with a single real item
            require(clearingTransaction.draftItems.isNotEmpty())
            require(clearingTransaction.categoryItems.isEmpty())
            require(clearingTransaction.chargeItems.isEmpty())
            require(clearingTransaction.realItems.size == 1)
            val realTransactionItem: Transaction.Item<RealAccount> = clearingTransaction.realItems.first()
            val realAccount: RealAccount = realTransactionItem.account
            // the clearing transaction's draft item is on the drafts account related to that real account
            require(
                clearingTransaction
                    .draftItems
                    .first()
                    .account
                    .realCompanion == realAccount,
            )
            require(draftTransactionItems.isNotEmpty())
            // each transaction item is on the correct account
            // and the transactions they are part of were check-writing transactions
            require(
                draftTransactionItems
                    .all {
                        it
                            .account
                            .realCompanion == realAccount &&
                                with(it.transaction) {
                                    realItems.isEmpty() &&
                                            chargeItems.isEmpty() &&
                                            draftItems.size == 1
                                }
                    },
            )
            // each transaction item comes from a different transaction
            require(
                draftTransactionItems
                    .mapTo(mutableSetOf()) { it.transaction }
                    .size ==
                        draftTransactionItems.size,
            )
            // the draft transactions items' amount sum is the amount being taken out of the real account
            require(
                draftTransactionItems
                    .fold(BigDecimal.ZERO.setScale(2)) { sum, transactionItem ->
                        sum + transactionItem.amount
                    } ==
                        -realTransactionItem.amount,
            )
            connection.transactOrThrow {
                draftTransactionItems
                    .forEach { draftTransactionItem ->
                        prepareStatement(
                            """
                            |update transaction_items ti
                            |set draft_status = 'cleared'
                            |where ti.budget_id = ?
                            |and ti.id = ?
                            |and ti.draft_status = 'outstanding'
                        """.trimMargin(),
                        )
                            .use { statement ->
                                statement.setUuid(1, budgetId)
                                statement.setUuid(2, draftTransactionItem.id)
                                if (statement.executeUpdate() != 1)
                                    throw IllegalStateException("Check being cleared not found in DB")
                            }
                        prepareStatement(
                            """
                            |update transactions t
                            |set cleared_by_transaction_id = ?
                            |where t.id = ?
                            |and t.budget_id = ?
                        """.trimMargin(),
                        )
                            .use { statement ->
                                statement.setUuid(1, clearingTransaction.id)
                                statement.setUuid(2, draftTransactionItem.transaction.id)
                                statement.setUuid(3, budgetId)
                                statement.executeUpdate()
                            }
                    }
                insertTransactionPreparedStatement(clearingTransaction, budgetId)
                    .use { insertTransaction: PreparedStatement ->
                        insertTransaction.executeUpdate()
                    }
                with(accountDao) {
                    val (statement, balancesToAdd) = insertTransactionItemsPreparedStatement(
                        clearingTransaction,
                        budgetId,
                    )
                    statement
                        .use { insertTransactionItem: PreparedStatement ->
                            insertTransactionItem.executeUpdate()
                        }
                    balancesToAdd.updateBalances(budgetId)
                }
            }
        }

    private fun Connection.insertTransactionPreparedStatement(
        transaction: Transaction,
        budgetId: UUID,
    ): PreparedStatement {
        val insertTransaction: PreparedStatement = prepareStatement(
            """
                insert into transactions (id, description, timestamp_utc, type, budget_id) VALUES
                (?, ?, ?, ?, ?)
            """.trimIndent(),
        )
        insertTransaction.setUuid(1, transaction.id)
        insertTransaction.setString(2, transaction.description)
        insertTransaction.setInstant(3, transaction.timestamp)
        insertTransaction.setString(4, transaction.type.name)
        insertTransaction.setUuid(5, budgetId)
        return insertTransaction
    }

    private fun Connection.insertTransactionItemsPreparedStatement(
        transaction: Transaction,
        budgetId: UUID,
    ): Pair<PreparedStatement, List<AccountDao.BalanceToAdd>> {
        val items = transaction.allItems()
        val transactionItemCounter = items.size
        transaction.categoryItems.size + transaction.realItems.size + transaction.draftItems.size + transaction.chargeItems.size
        val insertSql = buildString {
            var counter = transactionItemCounter
            append("insert into transaction_items (id, transaction_id, description, amount, draft_status, budget_id, account_id) values ")
            if (counter-- > 0) {
                append("(?, ?, ?, ?, ?, ?, ?)")
                while (counter-- > 0) {
                    append(", (?, ?, ?, ?, ?, ?, ?)")
                }
            }
        }
        var parameterIndex = 1
        val transactionItemInsert = prepareStatement(insertSql)
        val balancesToAdd: List<AccountDao.BalanceToAdd> = buildList {
            transaction
                .allItems()
                .forEach { transactionItem: Transaction.Item<*> ->
                    parameterIndex += setStandardProperties(
                        transactionItemInsert,
                        parameterIndex,
                        transaction,
                        transactionItem,
                        budgetId,
                    )
                    transactionItemInsert.setUuid(parameterIndex++, transactionItem.account.id)
                    add(AccountDao.BalanceToAdd(transactionItem.account.id, transactionItem.amount))
                }
        }
        return transactionItemInsert to balancesToAdd
    }

    /**
     * Inserts the transaction records and updates the account balances in the DB.
     */
    override fun commit(
        transaction: Transaction,
        budgetId: UUID,
        saveBalances: Boolean,
    ) =
        errorStateTracker.catchCommitErrorState {
            connection.transactOrThrow {
                insertTransactionPreparedStatement(transaction, budgetId)
                    .use { insertTransaction: PreparedStatement ->
                        insertTransaction.executeUpdate()
                    }
                with(accountDao) {
                    val (statement, balancesToAdd) = insertTransactionItemsPreparedStatement(
                        transaction,
                        budgetId,
                    )
                    statement
                        .use { insertTransactionItem: PreparedStatement ->
                            insertTransactionItem.executeUpdate()
                        }
                    if (saveBalances) balancesToAdd.updateBalances(budgetId)
                }
            }
        }

    /**
     * Deletes the given transaction and all its items from the DB and updates account balances appropriately.
     * @throws IllegalStateException if the given transaction has already been cleared.
     * @throws IllegalArgumentException if the transaction doesn't exist.
     * @return the list of [BalanceToAdd]s that should be applied to correct balances on accounts.
     */
    override fun deleteTransaction(
        transactionId: UUID,
        budgetId: UUID,
        accountIdToAccountMap: Map<UUID, Account>,
    ): List<AccountDao.BalanceToAdd> {
        connection.transactOrThrow {
            prepareStatement("""select * from transactions where id = ? and budget_id = ?""")
                .use { statement ->
                    statement.setUuid(1, transactionId)
                    statement.setUuid(2, budgetId)
                    statement.executeQuery()
                        .use { resultSet ->
                            if (resultSet.next()) {
                                if (resultSet.getString("cleared_by_transaction_id") !== null) {
                                    throw IllegalStateException("This transaction has already been cleared")
                                }
                            } else {
                                throw IllegalArgumentException("No transaction found with id $transactionId")
                            }
                        }
                }
            return buildList {
                prepareStatement(
                    """
                    |delete from transaction_items where transaction_id = ? and budget_id = ?
                    |returning account_id, amount
                """.trimMargin(),
                )
                    .use { statement ->
                        statement.setUuid(1, transactionId)
                        statement.setUuid(2, budgetId)
                        statement.executeQuery()
                            .use { resultSet ->
                                while (resultSet.next()) {
                                    add(
                                        AccountDao.BalanceToAdd(
                                            resultSet.getUuid("account_id")!!,
                                            -resultSet.getCurrencyAmount("amount"),
                                        ),
                                    )
                                }
                            }
                    }
            }
                .also {
                    prepareStatement("""delete from transactions where id = ? and budget_id = ?""")
                        .use { statement ->
                            statement.setUuid(1, transactionId)
                            statement.setUuid(2, budgetId)
                            if (statement.executeUpdate() != 1)
                                throw IllegalStateException("No transaction found with id $transactionId")
                        }
                }
        }
    }

    /**
     * 1. Sets the [Transaction.Item.draftStatus] to [DraftStatus.cleared] for each [clearedItems]
     * 2. Sets the "cleared-by" relation in the DB.
     * 3. Commits the new [billPayTransaction]
     * 4. Updates account balances
     * @throws IllegalArgumentException if either the [clearedItems] or the [billPayTransaction] is not what
     * we expect
     */
// TODO allow checks to pay credit card bills
    override fun commitCreditCardPayment(
        clearedItems: List<TransactionDao.ExtendedTransactionItem<ChargeAccount>>,
        billPayTransaction: Transaction,
        budgetId: UUID,
    ) =
        errorStateTracker.catchCommitErrorState {
            // require billPayTransaction is a simple real transfer between a real and a charge account
            require(billPayTransaction.draftItems.isEmpty())
            require(billPayTransaction.categoryItems.isEmpty())
            require(billPayTransaction.chargeItems.size == 1)
            require(billPayTransaction.realItems.size == 1)
            val billPayChargeTransactionItem: Transaction.Item<ChargeAccount> =
                billPayTransaction.chargeItems.first()
            val chargeAccount: ChargeAccount =
                billPayChargeTransactionItem.account
            // require clearedItems to be what we expect
            require(clearedItems.isNotEmpty())
            // all cleared items must be on the same charge account that's getting the transfer
            require(
                clearedItems.all {
                    it.account == chargeAccount
                },
            )
            // the amount of the clearedItems must be the same as the amount being transferred
            require(
                clearedItems
                    .fold(BigDecimal.ZERO.setScale(2)) { sum, transactionItem: TransactionDao.ExtendedTransactionItem<ChargeAccount> ->
                        sum + transactionItem.amount
                    } ==
                        -billPayChargeTransactionItem.amount,
            )
            connection.transactOrThrow {
                clearedItems
                    .forEach { chargeTransactionItem: TransactionDao.ExtendedTransactionItem<ChargeAccount> ->
                        prepareStatement(
                            """
                            |update transaction_items ti
                            |set draft_status = 'cleared'
                            |where ti.budget_id = ?
                            |and ti.id = ?
                            |and ti.draft_status = 'outstanding'
                        """.trimMargin(),
                        )
                            .use { statement ->
                                statement.setUuid(1, budgetId)
                                statement.setUuid(2, chargeTransactionItem.item.id)
                                if (statement.executeUpdate() != 1)
                                    throw IllegalStateException("Charge being cleared not found in DB")
                            }
                        prepareStatement(
                            """
                            |update transactions t
                            |set cleared_by_transaction_id = ?
                            |where t.id = ?
                            |and t.budget_id = ?
                        """.trimMargin(),
                        )
                            .use { statement ->
                                statement.setUuid(1, billPayTransaction.id)
                                statement.setUuid(2, chargeTransactionItem.transactionId)
                                statement.setUuid(3, budgetId)
                                statement.executeUpdate()
                            }
                    }
                insertTransactionPreparedStatement(billPayTransaction, budgetId)
                    .use { insertTransaction: PreparedStatement ->
                        insertTransaction.executeUpdate()
                    }
                with(accountDao) {
                    val (statement, balancesToAdd) = insertTransactionItemsPreparedStatement(
                        billPayTransaction,
                        budgetId,
                    )
                    statement
                        .use { insertTransactionItem: PreparedStatement ->
                            insertTransactionItem.executeUpdate()
                        }
                    balancesToAdd.updateBalances(budgetId)
                }
            }
        }

    /**
     * @param balanceAtEndOfPage is the balance of the account after the latest transaction on the page being requested.
     * If `null`, then [ExtendedTransactionItem.accountBalanceAfterItem] will be `null` for each [ExtendedTransactionItem] returned.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <A : Account> fetchTransactionItemsInvolvingAccount(
        account: A,
        limit: Int,
        offset: Int,
        balanceAtEndOfPage: BigDecimal?,
    ): List<TransactionDao.ExtendedTransactionItem<A>> =
        buildList {
            connection.transactOrThrow {
                // TODO if it is a clearing transaction, then create the full transaction by combining the cleared category items
                //      with this.
                prepareStatement(
                    """
                    |select t.id as transaction_id,
                    |       t.description as transaction_description,
                    |       t.timestamp_utc as transaction_timestamp,
                    |       t.type,
                    |       i.id as item_id,
                    |       i.amount,
                    |       i.description,
                    |       i.draft_status
                    |from transactions t
                    |         join transaction_items i
                    |            on i.transaction_id = t.id
                    |                and t.budget_id = i.budget_id
                    |where t.budget_id = ?
                    |  and i.account_id = ?
                    |order by t.timestamp_utc desc, t.id
                    |limit ?
                    |offset ?
            """.trimMargin(),
                )
                    .use { selectExtendedTransactionItemsForAccount: PreparedStatement ->
                        selectExtendedTransactionItemsForAccount
                            .apply {
                                setUuid(1, account.budgetId)
                                setUuid(2, account.id)
                                setInt(3, limit)
                                setInt(4, offset)
                            }
                        selectExtendedTransactionItemsForAccount
                            .executeQuery()
                            .use { result: ResultSet ->
                                with(result) {
                                    var runningBalance: BigDecimal? = balanceAtEndOfPage

                                    while (next()) {
                                        val transactionId: UUID = getUuid("transaction_id")!!
                                        val transactionDescription: String = getString("transaction_description")!!
                                        val transactionTimestamp: Instant = getInstant("transaction_timestamp")
                                        val id: UUID = getUuid("item_id")!!
                                        val amount: BigDecimal = getCurrencyAmount("amount")
                                        val description: String? = getString("description")
                                        val draftStatus: DraftStatus =
                                            DraftStatus.valueOf(getString("draft_status"))
                                        this@buildList.add(
                                            with(account) {
                                                extendedTransactionItemFactory(
                                                    id = id,
                                                    amount = amount,
                                                    description = description,
                                                    draftStatus = draftStatus,
                                                    transactionId = transactionId,
                                                    transactionDescription = transactionDescription,
                                                    transactionTimestamp = transactionTimestamp,
                                                    transactionType = Type.valueOf(getString("type")!!),
                                                    accountBalanceAfterItem = runningBalance,
                                                )
                                            } as TransactionDao.ExtendedTransactionItem<A>,
                                        )
                                        if (runningBalance !== null)
                                            runningBalance -= amount
                                    }
                                    // TODO https://github.com/benjishults/budget/issues/14
//                                    if (draftStatus == DraftStatus.clearing) {
//                                        prepareStatement(
//                                            """
//                                            |select t.*,
//                                            |       i.amount      as item_amount,
//                                            |       i.description as item_description,
//                                            |       i.category_account_id
//                                            |from transactions t
//                                            |         join transaction_items i on i.transaction_id = t.id
//                                            |where t.id in (select id
//                                            |               from transactions
//                                            |               where cleared_by_transaction_id = ?)
//                                            |  and i.category_account_id is not null
//                                        """.trimMargin(),
//                                        )
//                                            .use { selectClearedByExtendedTransactionItems: PreparedStatement ->
//                                                selectClearedByExtendedTransactionItems.setUuid(1, runningTransactionId!!)
//                                                selectClearedByExtendedTransactionItems.executeQuery()
//                                                    .use { result: ResultSet ->
//                                                        while (result.next()) {
//                                                            val itemAmount = result.getCurrencyAmount("item_amount")
//                                                            val itemDescription: String? =
//                                                                result.getString("item_description")
//                                                            transactionBuilder!!
//                                                                .categoryItemBuilders
//                                                                .maybeAddItemBuilder(
//                                                                    result,
//                                                                    itemAmount,
//                                                                    itemDescription,
//                                                                    data,
//                                                                    "category",
//                                                                    DraftStatus.none,
//                                                                ) { account: CategoryAccount ->
//                                                                    this.categoryAccount = account
//                                                                }
//                                                        }
//                                                    }
//                                            }
//                                    } else {
                                }
                            }
                    }
            }
        }

    override fun getTransactionOrNull(
        transactionId: UUID,
        budgetId: UUID,
        accountIdToAccountMap: Map<UUID, Account>,
    ): Transaction? =
        connection.transactOrThrow {
            prepareStatement(
                """
                    |select t.description as transaction_description,
                    |       t.timestamp_utc as transaction_timestamp,
                    |       t.cleared_by_transaction_id,
                    |       t.type,
                    |       i.account_id,
                    |       i.id,
                    |       i.amount,
                    |       i.description,
                    |       i.draft_status
                    |from transactions t
                    |         join transaction_items i
                    |              on i.transaction_id = t.id
                    |                  and t.budget_id = i.budget_id
                    |where t.budget_id = ?
                    |  and t.id = ?
                """.trimMargin(),
            )
                .use { selectTransactionAndItems: PreparedStatement ->
                    selectTransactionAndItems
                        .apply {
                            setUuid(1, budgetId)
                            setUuid(2, transactionId)
                        }
                        .executeQuery()
                        .use { result: ResultSet ->
                            if (result.next()) {
                                val transactionBuilder =
                                    initializeTransactionBuilderWithFirstItem(
                                        result,
                                        transactionId,
                                        accountIdToAccountMap,
                                    )
                                while (result.next()) {
                                    // for each transaction item...
                                    transactionBuilder.populateItem(result, accountIdToAccountMap)
                                }
                                transactionBuilder.build()
                            } else
                                null
                        }
                }
        }

    private fun initializeTransactionBuilderWithFirstItem(
        result: ResultSet,
        transactionId: UUID,
        accountIdToAccountMap: Map<UUID, Account>,
    ): Transaction.Builder =
        Transaction.Builder(
            description = result.getString("transaction_description"),
            timestamp = result.getInstant("transaction_timestamp"),
            type = Type.valueOf(result.getString("type")),
        )
            .apply {
                id = transactionId
                populateItem(result, accountIdToAccountMap)
            }

    private fun Transaction.Builder.populateItem(
        result: ResultSet,
        accountIdToAccountMap: Map<UUID, Account>,
    ) {
        with(accountIdToAccountMap[result.getUuid("account_id")!!]!!) {
            addItemBuilderTo(
                result.getCurrencyAmount("amount"),
                result.getString("description"),
                DraftStatus.valueOf(result.getString("draft_status")),
                result.getUuid("id")!!,
            )
        }
    }

//    private fun Connection.insertTransactionItemsPreparedStatement(
//        transaction: Transaction,
//        budgetId: UUID,
//    ): PreparedStatement {
//        val transactionItemCounter =
//            transaction.categoryItems.size + transaction.realItems.size + transaction.draftItems.size + transaction.chargeItems.size
//        val insertSql = buildString {
//            var counter = transactionItemCounter
//            append("insert into transaction_items (id, transaction_id, description, amount, draft_status, budget_id, account_id) values ")
//            if (counter-- > 0) {
//                append("(?, ?, ?, ?, ?, ?, ?)")
//                while (counter-- > 0) {
//                    append(", (?, ?, ?, ?, ?, ?, ?)")
//                }
//            }
//        }
//        var parameterIndex = 1
//        val transactionItemInsert = prepareStatement(insertSql)
//        transaction
//            .allItems()
//            .forEach { transactionItem: Transaction.Item<*> ->
//                parameterIndex += setStandardProperties(
//                    transactionItemInsert,
//                    parameterIndex,
//                    transaction,
//                    transactionItem,
//                    budgetId,
//                )
//                transactionItemInsert.setUuid(parameterIndex++, transactionItem.account.id)
//            }
//        return transactionItemInsert
//    }

    /**
     * Sets a set of standard parameter starting at [parameterIndex]
     * @return the number of parameters set.
     */
    private fun setStandardProperties(
        transactionItemInsert: PreparedStatement,
        parameterIndex: Int,
        transaction: Transaction,
        transactionItem: Transaction.Item<*>,
        budgetId: UUID,
    ): Int {
        transactionItemInsert.setUuid(parameterIndex, transactionItem.id)
        transactionItemInsert.setUuid(parameterIndex + 1, transaction.id)
        transactionItemInsert.setString(parameterIndex + 2, transactionItem.description)
        transactionItemInsert.setBigDecimal(parameterIndex + 3, transactionItem.amount)
        transactionItemInsert.setString(parameterIndex + 4, transactionItem.draftStatus.name)
        transactionItemInsert.setUuid(parameterIndex + 5, budgetId)
        return 6
    }

//    private fun Connection.insertTransactionPreparedStatement(
//        transaction: Transaction,
//        budgetId: UUID,
//    ): PreparedStatement {
//        val insertTransaction: PreparedStatement = prepareStatement(
//            """
//                insert into transactions (id, description, timestamp_utc, budget_id) VALUES
//                (?, ?, ?, ?)
//            """.trimIndent(),
//        )
//        insertTransaction.setUuid(1, transaction.id)
//        insertTransaction.setString(2, transaction.description)
//        insertTransaction.setTimestamp(3, transaction.timestamp)
//        insertTransaction.setUuid(4, budgetId)
//        return insertTransaction
//    }

    // TODO https://github.com/benjishults/budget/issues/14
//    /**
//     * if
//     * 1. [account] is real
//     * 2. there is a single other item in the transaction
//     * 3. that item has status 'clearing'
//     * then
//     * 1. pull the category items from the transactions cleared by this transaction
//     * 2. replace the 'clearing' item with those items
//     */
//    @Deprecated("This doesn't really work")
//    override fun fetchTransactionsInvolvingAccount(
//        account: Account,
//        data: BudgetData,
//        limit: Int,
//        offset: Int,
//    ): List<Transaction> =
//        connection.transactOrThrow {
//            // TODO if it is a clearing transaction, then create the full transaction by combining the cleared category items
//            //      with this.
//            // FIXME this limit offset stuff won't work the way I want... especially when offset > 0.
//            prepareStatement(
//                """
//                    |select t.timestamp_utc as timestamp_utc,
//                    |       t.id as transaction_id,
//                    |       t.description as description,
//                    |       i.amount      as item_amount,
//                    |       i.description as item_description,
//                    |       i.category_account_id,
//                    |       i.draft_account_id,
//                    |       i.real_account_id,
//                    |       i.charge_account_id,
//                    |       i.draft_status
//                    |from transactions t
//                    |  join transaction_items i on i.transaction_id = t.id
//                    |where t.budget_id = ?
//                    |  and t.id in (select tr.id
//                    |               from transactions tr
//                    |                 join transaction_items ti
//                    |                   on tr.id = ti.transaction_id
//                    |               where ti.${
//                    when (account) {
//                        is CategoryAccount -> "category_account_id"
//                        is ChargeAccount -> "charge_account_id"
//                        is RealAccount -> "real_account_id"
//                        is DraftAccount -> "draft_account_id"
//                        else -> throw Error("Unknown account type ${account::class}")
//                    }
//                } = ?
//                    |               order by tr.timestamp_utc desc, tr.id
//                    |               limit ?
//                    |               offset ?)
//            """.trimMargin(),
//            )
//                .use { selectTransactionAndItemsForAccount: PreparedStatement ->
//                    selectTransactionAndItemsForAccount
//                        .apply {
//                            setUuid(1, data.id)
//                            setUuid(2, account.id)
//                            setInt(3, limit)
//                            setInt(4, offset)
//                        }
//                    // TODO boy, this code is a mess.  looks like I was focussed on getting something to work and
//                    //      didn't clean it up after
//                    selectTransactionAndItemsForAccount
//                        .executeQuery()
//                        .use { result: ResultSet ->
//                            val returnValue: MutableList<Transaction> = mutableListOf()
//                            // NOTE this will be a running transaction and will switch to the next when one is done
//                            var runningTransactionId: UUID? = null
//                            var transactionBuilder: Transaction.Builder? = null
//                            while (result.next()) {
//                                // for each transaction item...
//                                result.getUuid("transaction_id")!!
//                                    .let { uuid: UUID ->
//                                        if (uuid != runningTransactionId) {
//                                            conditionallyAddCompleteTransactionToList(
//                                                runningTransactionId,
//                                                returnValue,
//                                                transactionBuilder,
//                                            )
//                                            runningTransactionId = uuid
//                                            transactionBuilder = initializeTransactionBuilder(result, uuid)
//                                        }
//                                    }
//                                val draftStatus: DraftStatus =
//                                    DraftStatus.valueOf(result.getString("draft_status"))
//                                if (draftStatus == DraftStatus.clearing) {
//                                    prepareStatement(
//                                        """
//                                            |select t.*,
//                                            |       i.amount      as item_amount,
//                                            |       i.description as item_description,
//                                            |       i.category_account_id
//                                            |from transactions t
//                                            |         join transaction_items i on i.transaction_id = t.id
//                                            |where t.id in (select id
//                                            |               from transactions
//                                            |               where cleared_by_transaction_id = ?)
//                                            |  and i.category_account_id is not null
//                                        """.trimMargin(),
//                                    )
//                                        .use { preparedStatement: PreparedStatement ->
//                                            preparedStatement.setUuid(1, runningTransactionId!!)
//                                            preparedStatement.executeQuery()
//                                                .use { result: ResultSet ->
//                                                    while (result.next()) {
//                                                        val itemAmount = result.getCurrencyAmount("item_amount")
//                                                        val itemDescription: String? =
//                                                            result.getString("item_description")
//                                                        transactionBuilder!!
//                                                            .categoryItemBuilders
//                                                            .maybeAddItemBuilder(
//                                                                result,
//                                                                itemAmount,
//                                                                itemDescription,
//                                                                data,
//                                                                "category",
//                                                                DraftStatus.none,
//                                                            ) { account: CategoryAccount ->
//                                                                this.categoryAccount = account
//                                                            }
//                                                    }
//                                                }
//                                        }
//                                } else {
//                                    val itemAmount = result.getCurrencyAmount("item_amount")
//                                    val itemDescription: String? = result.getString("item_description")
//                                    transactionBuilder!!
//                                        .categoryItemBuilders
//                                        .maybeAddItemBuilder(
//                                            result,
//                                            itemAmount,
//                                            itemDescription,
//                                            data,
//                                            "category",
//                                            draftStatus,
//                                        ) { account: CategoryAccount ->
//                                            this.categoryAccount = account
//                                        }
//                                    transactionBuilder!!
//                                        .realItemBuilders.maybeAddItemBuilder(
//                                            result,
//                                            itemAmount,
//                                            itemDescription,
//                                            data,
//                                            "real",
//                                            draftStatus,
//                                        ) { account: RealAccount ->
//                                            realAccount = account
//                                        }
//                                    transactionBuilder!!
//                                        .chargeItemBuilders.maybeAddItemBuilder(
//                                            result,
//                                            itemAmount,
//                                            itemDescription,
//                                            data,
//                                            "charge",
//                                            draftStatus,
//                                        ) { account: ChargeAccount ->
//                                            chargeAccount = account
//                                        }
//                                    transactionBuilder!!
//                                        .draftItemBuilders
//                                        .maybeAddItemBuilder(
//                                            result,
//                                            itemAmount,
//                                            itemDescription,
//                                            data,
//                                            "draft",
//                                            draftStatus,
//                                        ) { account: DraftAccount ->
//                                            draftAccount = account
//                                        }
//                                }
//                            }
//                            conditionallyAddCompleteTransactionToList(
//                                runningTransactionId,
//                                returnValue,
//                                transactionBuilder,
//                            )
//                            returnValue.toList()
//                        }
//                }
//        }

}
