package org.openmeds.reminder.domain.model

import java.math.BigInteger

@JvmInline
value class MilliUnits(val value: Long) {
    init {
        require(value >= 0) { "Milli-units must not be negative" }
    }

    operator fun minus(other: MilliUnits): SignedMilliUnits = SignedMilliUnits(Math.subtractExact(value, other.value))

    companion object {
        fun fromDecimal(text: String): MilliUnits = MilliUnits(parseDecimal(text, allowNegative = false))
    }
}

@JvmInline
value class SignedMilliUnits(val value: Long) {
    operator fun minus(other: MilliUnits): SignedMilliUnits = SignedMilliUnits(Math.subtractExact(value, other.value))

    companion object {
        fun fromDecimal(text: String): SignedMilliUnits = SignedMilliUnits(parseDecimal(text, allowNegative = true))
    }
}

private fun parseDecimal(text: String, allowNegative: Boolean): Long {
    val match = DECIMAL_PATTERN.matchEntire(text)
        ?: throw IllegalArgumentException("Quantity must be a decimal with at most three fractional digits")
    val negative = match.groupValues[1] == "-"
    require(allowNegative || !negative) { "Milli-units must not be negative" }

    val whole = BigInteger(match.groupValues[2])
    val fraction = BigInteger(match.groupValues[3].padEnd(3, '0').ifEmpty { "0" })
    val magnitude = whole.multiply(THOUSAND).add(fraction)
    return if (negative) magnitude.negate().toExactLong() else magnitude.toExactLong()
}

private fun BigInteger.toExactLong(): Long {
    if (this < LONG_MIN || this > LONG_MAX) {
        throw ArithmeticException("Quantity is outside the Long milli-unit range")
    }
    return toLong()
}

private val DECIMAL_PATTERN = Regex("([+-]?)([0-9]+)(?:\\.([0-9]{1,3}))?")
private val THOUSAND = BigInteger.valueOf(1_000L)
private val LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE)
private val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
