package jp.co.sugipharmacy.coupon.domain.rule

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.domain.Scalar

/**
 * 外部入力（JSON を汎用デシリアライズした Map/List/スカラー）を条件ASTへ正規化する。
 * 受け付ける形は2つ。
 *
 * 1. 簡易形（Confluence 仕様の形）: eq の OR として解釈する。
 *    { "rules": [ { "key": "age", "value": "20" }, { "key": "age", "value": "30" } ] }
 *
 * 2. リッチ形: 比較演算子（eq/gte/lte/gt/lt/in）と AND/OR ネスト。
 *    { "and": [ { "key": "age", "operator": "gte", "value": 20 },
 *               { "or": [ ... ] } ] }
 */
object ConditionParser {

    fun parse(input: Any?): Condition {
        val node = input.asObject("condition")
        return if (node.containsKey("rules")) parseFlatForm(node) else parseNode(node, "condition")
    }

    private fun parseFlatForm(node: Map<*, *>): Condition {
        val rules = node["rules"] as? List<*>
        if (rules.isNullOrEmpty()) {
            throw DomainValidationError("condition.rules must be a non-empty array")
        }
        val comparisons = rules.mapIndexed { i, rule -> parseFlatRule(rule, i) }
        return if (comparisons.size == 1) comparisons.single() else Condition.Or(comparisons)
    }

    private fun parseFlatRule(rule: Any?, index: Int): Condition.Comparison {
        val path = "condition.rules[$index]"
        val node = rule.asObject(path)
        return Condition.Comparison(
            key = requireKey(node, path),
            operator = Condition.Operator.EQ,
            values = listOf(requireScalar(node["value"], "$path.value")),
        )
    }

    private fun parseNode(raw: Any?, path: String): Condition {
        val node = raw.asObject(path)
        val hasAnd = node.containsKey("and")
        val hasOr = node.containsKey("or")
        if (hasAnd && hasOr) {
            throw DomainValidationError("$path must not contain both \"and\" and \"or\"")
        }
        if (hasAnd || hasOr) {
            val name = if (hasAnd) "and" else "or"
            val children = node[name] as? List<*>
            if (children.isNullOrEmpty()) {
                throw DomainValidationError("$path.$name must be a non-empty array")
            }
            val parsed = children.mapIndexed { i, child -> parseNode(child, "$path.$name[$i]") }
            return if (hasAnd) Condition.And(parsed) else Condition.Or(parsed)
        }
        return parseComparison(node, path)
    }

    private fun parseComparison(node: Map<*, *>, path: String): Condition.Comparison {
        val key = requireKey(node, path)
        val operatorToken = node["operator"] as? String
            ?: throw DomainValidationError("$path.operator must be one of: ${Condition.Operator.tokens()}")
        val operator = Condition.Operator.fromToken(operatorToken)
            ?: throw DomainValidationError("$path.operator must be one of: ${Condition.Operator.tokens()}")
        val rawValue = node["value"]
        if (operator == Condition.Operator.IN) {
            val values = rawValue as? List<*>
            if (values.isNullOrEmpty()) {
                throw DomainValidationError("$path.value must be a non-empty array for operator \"in\"")
            }
            return Condition.Comparison(
                key = key,
                operator = operator,
                values = values.mapIndexed { i, v -> requireScalar(v, "$path.value[$i]") },
            )
        }
        return Condition.Comparison(
            key = key,
            operator = operator,
            values = listOf(requireScalar(rawValue, "$path.value")),
        )
    }

    private fun requireKey(node: Map<*, *>, path: String): String {
        val key = node["key"] as? String
        if (key.isNullOrBlank()) {
            throw DomainValidationError("$path.key must be a non-empty string")
        }
        return key
    }

    private fun requireScalar(value: Any?, path: String): Scalar =
        Scalar.of(value) ?: throw DomainValidationError("$path must be a string, number, or boolean")

    private fun Any?.asObject(path: String): Map<*, *> =
        this as? Map<*, *> ?: throw DomainValidationError("$path must be an object")
}
