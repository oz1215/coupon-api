package jp.co.sugipharmacy.coupon.domain

import java.math.BigDecimal

/**
 * 属性値・条件値のスカラー（文字列・数値・真偽値）。
 * 比較規則: 双方が数値化できるなら数値で、それ以外は文字列表現で比較する
 * （"20" と 20 を同一視する — 属性ソースにより数値が文字列で届くため）。
 */
sealed interface Scalar {
    data class Text(val value: String) : Scalar
    data class Num(val value: BigDecimal) : Scalar
    data class Bool(val value: Boolean) : Scalar

    fun asNumber(): BigDecimal? = when (this) {
        is Num -> value
        is Text -> value.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
        is Bool -> null
    }

    fun asText(): String = when (this) {
        is Text -> value
        is Num -> value.toPlainString()
        is Bool -> value.toString()
    }

    fun looselyEquals(other: Scalar): Boolean {
        val a = asNumber()
        val b = other.asNumber()
        return if (a != null && b != null) a.compareTo(b) == 0 else asText() == other.asText()
    }

    companion object {
        /** JSON 由来の生値をスカラーへ。スカラー化できない型（object/array/null）は null。 */
        fun of(raw: Any?): Scalar? = when (raw) {
            is String -> Text(raw)
            is Boolean -> Bool(raw)
            is Number -> Num(BigDecimal(raw.toString()))
            else -> null
        }
    }
}
