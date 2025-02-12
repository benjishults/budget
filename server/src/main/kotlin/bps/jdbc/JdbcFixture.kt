package bps.jdbc

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types.OTHER
import java.util.UUID

interface JdbcFixture {

    fun PreparedStatement.setInstant(parameterIndex: Int, timestamp: Instant) {
        setTimestamp(parameterIndex, Timestamp(timestamp.toEpochMilliseconds()))
    }

    /**
     * This assumes that the DB [Timestamp] is stored in UTC.
     */
    fun Timestamp.toLocalDateTime(timeZone: TimeZone): LocalDateTime =
        this
            .toInstant()
            .toKotlinInstant()
            .toLocalDateTime(timeZone)

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

    fun ResultSet.getInstantOrNull(columnLabel: String = "timestamp_utc"): Instant? =
        getTimestamp(columnLabel)
            ?.toInstant()
            ?.toKotlinInstant()

    fun ResultSet.getCurrencyAmount(name: String): BigDecimal =
        getBigDecimal(name).setScale(2)

    fun ResultSet.getUuid(name: String): UUID? =
        getObject(name, UUID::class.java)

    fun PreparedStatement.setUuid(parameterIndex: Int, uuid: UUID) =
        setObject(parameterIndex, uuid, OTHER)

    companion object : JdbcFixture

}

/**
 * commits after running [block].  Throws exception on rollback.
 */
inline fun <T> Connection.transactOrThrow(
    block: Connection.() -> T,
): T =
    transact({ throw it }, block)

/**
 * commits after running [block].
 * @returns the value of executing [onRollback] if the transaction was rolled back otherwise the result of [block]
 * @param onRollback defaults to throwing the exception but could do something like returning `null`.
 */
inline fun <T> Connection.transact(
    onRollback: (Exception) -> T = { throw it },
    block: Connection.() -> T,
): T =
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
