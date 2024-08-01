package bps.jdbc

import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet

interface JdbcFixture : AutoCloseable {
    val connection: Connection

    /**
     * Closes the connection.
     */
    override fun close() {
        connection.close()
    }

    /**
     * commits after running [block].
     * @returns the value of executing [onRollback] if the transaction was rolled back otherwise the result of [block]
     * @param onRollback defaults to throwing the exception
     */
    fun <T : Any> transaction(
        onRollback: (Exception) -> T? = { throw it },
        block: Connection.() -> T,
    ): T? =
        with(connection) {
            try {
                block()
                    .also {
                        commit()
                    }
            } catch (exception: Exception) {
                try {
                    rollback()
                    onRollback(exception)
                } catch (rollbackException: Exception) {
                    rollbackException.addSuppressed(exception)
                    throw rollbackException
                }
            }
        }

    fun ResultSet.getCurrencyAmount(name: String): BigDecimal =
        getBigDecimal(name).setScale(2)

    // TODO used?
    companion object {
        operator fun invoke(connection: Connection) =
            object : JdbcFixture {
                override val connection: Connection = connection
                override fun close() {
                    connection.close()
                }
            }
    }

}
