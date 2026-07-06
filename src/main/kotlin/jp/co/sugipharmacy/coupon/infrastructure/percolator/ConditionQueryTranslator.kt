package jp.co.sugipharmacy.coupon.infrastructure.percolator

import jp.co.sugipharmacy.coupon.domain.rule.Condition

/**
 * 条件AST → OpenSearch クエリDSL（JSON相当の Map）への変換。
 *
 * 写像:
 * - Comparison(EQ)  -> term
 * - Comparison(IN)  -> terms（数値系・文字列系が混在する場合は bool.should で束ねる）
 * - Comparison(GTE/LTE/GT/LT) -> range（境界値が数値化不能なら match_none — InMemory の「常に非該当」と同義）
 * - And -> bool.filter / Or -> bool.should + minimum_should_match=1（再帰）
 *
 * 文字列/数値の同一視（`"20"` と `20`）の戦略:
 * `Scalar.looselyEquals` は「双方が数値化できるなら数値比較、それ以外は文字列比較」。
 * OpenSearch では1フィールド1型のため、属性1つを2つのサブフィールドへ分けて写す。
 * - `attrs.<key>.num`（double）: 数値化できる値のみ。数値比較（eq/in/range）はこちらで行う。
 * - `attrs.<key>.txt`（keyword）: 常に `asText()` を持つ。数値化できない値の eq/in はこちら。
 * 条件値も同じ規則で振り分ける（数値化できる値は num へ、できない値は txt へ）。
 * 「文字列表現が一致するのに数値化可能性が食い違う」組は存在しないため、
 * この2面戦略は looselyEquals と同じ結果になる（精度差は double 依存 — DESIGN.md 参照）。
 *
 * 誤配布より配布漏れ（fail-safe）の再現:
 * - 属性欠落 -> percolate 文書に該当フィールドが無く、term/terms/range いずれも不一致。
 * - 属性が数値化不能なのに数値比較 -> `attrs.<key>.num` が文書に無く range/term(num) は不一致。
 * - 条件値が数値化不能な range -> match_none（登録は成功するが決して当たらない）。
 */
object ConditionQueryTranslator {

    /** percolate 文書内で会員属性を格納するトップレベルフィールド。 */
    const val ATTRIBUTES_FIELD = "attrs"

    /** 数値化できる値を写すサブフィールド（double）。 */
    const val NUMERIC_SUBFIELD = "num"

    /** 常に文字列表現を写すサブフィールド（keyword）。 */
    const val TEXT_SUBFIELD = "txt"

    fun numericFieldPath(key: String): String = "$ATTRIBUTES_FIELD.$key.$NUMERIC_SUBFIELD"

    fun textFieldPath(key: String): String = "$ATTRIBUTES_FIELD.$key.$TEXT_SUBFIELD"

    fun translate(condition: Condition): Map<String, Any> = when (condition) {
        is Condition.And -> mapOf(
            "bool" to mapOf("filter" to condition.conditions.map(::translate)),
        )
        is Condition.Or -> mapOf(
            "bool" to mapOf(
                "should" to condition.conditions.map(::translate),
                "minimum_should_match" to 1,
            ),
        )
        is Condition.Comparison -> translateComparison(condition)
    }

    private fun translateComparison(comparison: Condition.Comparison): Map<String, Any> =
        when (comparison.operator) {
            Condition.Operator.EQ -> equalsQuery(comparison)
            Condition.Operator.IN -> inQuery(comparison)
            Condition.Operator.GTE, Condition.Operator.LTE,
            Condition.Operator.GT, Condition.Operator.LT,
            -> rangeQuery(comparison)
        }

    private fun equalsQuery(comparison: Condition.Comparison): Map<String, Any> {
        val value = comparison.values.single()
        val number = value.asNumber()
        return if (number != null) {
            mapOf("term" to mapOf(numericFieldPath(comparison.key) to mapOf("value" to number)))
        } else {
            mapOf("term" to mapOf(textFieldPath(comparison.key) to mapOf("value" to value.asText())))
        }
    }

    private fun inQuery(comparison: Condition.Comparison): Map<String, Any> {
        val numbers = comparison.values.mapNotNull { it.asNumber() }
        val texts = comparison.values.filter { it.asNumber() == null }.map { it.asText() }
        val clauses = buildList {
            if (numbers.isNotEmpty()) {
                add(mapOf("terms" to mapOf(numericFieldPath(comparison.key) to numbers)))
            }
            if (texts.isNotEmpty()) {
                add(mapOf("terms" to mapOf(textFieldPath(comparison.key) to texts)))
            }
        }
        return if (clauses.size == 1) {
            clauses.single()
        } else {
            mapOf("bool" to mapOf("should" to clauses, "minimum_should_match" to 1))
        }
    }

    private fun rangeQuery(comparison: Condition.Comparison): Map<String, Any> {
        // InMemory と同じ fail-safe: 条件値が数値化できない数値比較は「決して該当しない」。
        val bound = comparison.values.single().asNumber()
            ?: return mapOf("match_none" to emptyMap<String, Any>())
        val operator = when (comparison.operator) {
            Condition.Operator.GTE -> "gte"
            Condition.Operator.LTE -> "lte"
            Condition.Operator.GT -> "gt"
            Condition.Operator.LT -> "lt"
            else -> error("unreachable")
        }
        return mapOf("range" to mapOf(numericFieldPath(comparison.key) to mapOf(operator to bound)))
    }
}
