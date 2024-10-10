package bps.jdbc

import bps.budget.persistence.jdbc.toLocalDateTime
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinInstant
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types.OTHER
import java.util.UUID

// TODO since I want to use an inline function here, probably better if this isn't an interface
interface JdbcFixture {
//    val connection: Connection
//
//    /**
//     * Closes the connection.
//     */
//    override fun close() {
//        connection.close()
//    }

    fun PreparedStatement.setTimestamp(parameterIndex: Int, timestamp: Instant) {
        setTimestamp(parameterIndex, Timestamp(timestamp.toEpochMilliseconds()))
    }

    fun ResultSet.getLocalDateTimeForTimeZone(
        timeZone: TimeZone,
        columnLabel: String = "timestamp_utc",
    ): LocalDateTime =
        getTimestamp(columnLabel)
            .toLocalDateTime(timeZone)

    fun ResultSet.getInstant(columnLabel: String = "timestamp_utc"): Instant =
        getTimestamp(columnLabel)
            .toInstant()
            .toKotlinInstant()

    fun ResultSet.getCurrencyAmount(name: String): BigDecimal =
        getBigDecimal(name).setScale(2)

    fun ResultSet.getUuid(name: String): UUID =
        getObject(name, UUID::class.java)

    fun PreparedStatement.setUuid(parameterIndex: Int, uuid: UUID) =
        setObject(parameterIndex, uuid, OTHER)

    companion object : JdbcFixture

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
    block: Connection.() -> T?,
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
