package bps.budget.model

import java.math.BigDecimal

fun String.toCurrencyAmountOrNull(): BigDecimal? =
    try {
        BigDecimal(this).setScale(2)
    } catch (e: NumberFormatException) {
        null
    }

fun min(a: BigDecimal, b: BigDecimal): BigDecimal =
    if ((a <= b)) a else b
