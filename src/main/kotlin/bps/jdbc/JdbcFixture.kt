package bps.jdbc

import bps.budget.persistence.jdbc.toLocalDateTime
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.util.TimeZone

// TODO since I want to use an inline function here, probably better if this isn't an interface
interface JdbcFixture : AutoCloseable {
    val connection: Connection

    /**
     * Closes the connection.
     */
    override fun close() {
        connection.close()
    }

    fun PreparedStatement.setTimestamp(parameterIndex: Int, timestamp: Instant) {
        setTimestamp(parameterIndex, Timestamp(timestamp.toEpochMilli()))
    }

    fun ResultSet.getLocalDateTimeForTimeZone(timeZone: TimeZone): LocalDateTime =
        getTimestamp(
            "timestamp_utc",
            /*Calendar.Builder().setTimeZone(TimeZone.getDefault()).build(),*/
        )
            .toLocalDateTime(timeZone)

    fun ResultSet.getCurrencyAmount(name: String): BigDecimal =
        getBigDecimal(name).setScale(2)

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

/**
 * commits after running [block].
 * @returns the value of executing [onRollback] if the transaction was rolled back otherwise the result of [block]
 * @param onRollback defaults to throwing the exception
 */
inline fun <T : Any> Connection.transactOrThrow(
    onRollback: (Exception) -> T = { throw it },
    block: Connection.() -> T,
): T =
    transactOrNull(onRollback, block)!!

/**
 * commits after running [block].
 * @returns the value of executing [onRollback] if the transaction was rolled back otherwise the result of [block]
 * @param onRollback defaults to throwing the exception but could do something like returning `null`.
 */
inline fun <T : Any> Connection.transactOrNull(
    onRollback: (Exception) -> T? = { throw it },
    block: Connection.() -> T,
): T? =
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
