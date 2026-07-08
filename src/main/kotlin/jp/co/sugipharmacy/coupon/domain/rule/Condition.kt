package jp.co.sugipharmacy.coupon.domain.rule

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.domain.Scalar
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes

/**
 * 配布ルール条件のAST。
 * Confluence の簡易形（rules 配列＝eq の OR）とリッチ形（比較演算子＋AND/OR ネスト）の
 * 双方をこの一つの型に正規化してから評価する（型は一つ、実行は複数）。
 *
 * 評価は誤配布より配布漏れに倒す: 属性欠落・数値化不能は「該当しない」。
 */
sealed interface Condition {
    fun evaluate(attributes: MemberAttributes): Boolean

    enum class Operator(val token: String) {
        EQ("eq"), GTE("gte"), LTE("lte"), GT("gt"), LT("lt"), IN("in");

        companion object {
            fun fromToken(token: String): Operator? = entries.firstOrNull { it.token == token }
            fun tokens(): String = entries.joinToString(", ") { it.token }
        }
    }

    /** 葉ノード。`values` は IN のとき1件以上、それ以外は厳密に1件。 */
    data class Comparison(
        val key: String,
        val operator: Operator,
        val values: List<Scalar>,
    ) : Condition {
        init {
            if (key.isBlank()) {
                throw DomainValidationError("comparison key must be a non-empty string")
            }
            if (operator == Operator.IN) {
                if (values.isEmpty()) {
                    throw DomainValidationError("operator \"in\" requires a non-empty value list")
                }
            } else if (values.size != 1) {
                throw DomainValidationError("operator \"${operator.token}\" requires exactly one value")
            }
        }

        override fun evaluate(attributes: MemberAttributes): Boolean {
            val actual = attributes.get(key) ?: return false
            return when (operator) {
                Operator.EQ -> actual.looselyEquals(values.single())
                Operator.IN -> values.any { actual.looselyEquals(it) }
                Operator.GTE, Operator.LTE, Operator.GT, Operator.LT -> {
                    val left = actual.asNumber() ?: return false
                    val right = values.single().asNumber() ?: return false
                    when (operator) {
                        Operator.GTE -> left >= right
                        Operator.LTE -> left <= right
                        Operator.GT -> left > right
                        Operator.LT -> left < right
                        else -> error("unreachable")
                    }
                }
            }
        }
    }

    data class And(val conditions: List<Condition>) : Condition {
        init {
            if (conditions.isEmpty()) throw DomainValidationError("\"and\" requires at least one condition")
        }

        override fun evaluate(attributes: MemberAttributes): Boolean =
            conditions.all { it.evaluate(attributes) }
    }

    data class Or(val conditions: List<Condition>) : Condition {
        init {
            if (conditions.isEmpty()) throw DomainValidationError("\"or\" requires at least one condition")
        }

        override fun evaluate(attributes: MemberAttributes): Boolean =
            conditions.any { it.evaluate(attributes) }
    }

    /**
     * 否定ノード（正準ASTの `not`）。内側の評価結果を反転する。
     * 注意: fail-safe の向きも反転する — 内側が「属性欠落＝非該当」に倒れる場合、
     * `not` の外側では「該当」になる。OpenSearch 側（bool.must_not）も同じ挙動であり、
     * InMemory との parity は保たれる。
     */
    data class Not(val condition: Condition) : Condition {
        override fun evaluate(attributes: MemberAttributes): Boolean =
            !condition.evaluate(attributes)
    }
}
