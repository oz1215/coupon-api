package jp.co.sugipharmacy.coupon.domain.rule

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.domain.Scalar

/**
 * 受領時検証の上限。数値は暫定 — coupon-rule-schema / AST仕様 §4 で確定予定（TODO）。
 */
data class ConditionLimits(
    val maxDepth: Int = 8,
    val maxInValues: Int = 100,
)

/**
 * 正準AST（`coupon-rule-schema`・単一ソース）→ 内部 [Condition] への変換＋受領時検証。
 *
 * 契約はこの正準形のみ:
 * - 論理: `{ "op":"and"|"or", "nodes":[ ... ] }`（`nodes` は1件以上）
 * - 否定: `{ "op":"not", "node": ... }`
 * - 比較: `{ "op":"eq"|"gte"|"lte"|"gt"|"lt", "attr": <name>, "value": <scalar> }`
 * - 集合: `{ "op":"in", "attr": <name>, "values":[ <scalar>, ... ] }`
 * - scalar = string | number | boolean
 *
 * 不正（未知op / 型不一致 / 空nodes / ネスト深さ上限 / in要素数上限 / 未知attr）は
 * [DomainValidationError]（不正箇所を指す JSON Pointer 付き）。イベント受領側はこれを
 * 適用せず DLQ へ倒す。`attr` の語彙はプロファイルストアの属性カタログが正 — [attrAllowed]
 * で判定する（カタログ所在は §4 未確定のため、既定実装は全許可）。
 *
 * NOTE: `coupon-rule-schema` リポジトリは未作成。作成後はこの手書き型を生成型へ差し替える（TODO）。
 * 正規化（簡易形→正準形）は coupon-admin 側で完了済み前提。本APIは正準ASTのみ扱う。
 */
object CanonicalConditionParser {

    fun parse(
        input: Any?,
        limits: ConditionLimits = ConditionLimits(),
        attrAllowed: (String) -> Boolean = { true },
    ): Condition = parseNode(input, "/condition", 1, limits, attrAllowed)

    private fun parseNode(
        raw: Any?,
        pointer: String,
        depth: Int,
        limits: ConditionLimits,
        attrAllowed: (String) -> Boolean,
    ): Condition {
        if (depth > limits.maxDepth) {
            throw err(pointer, "condition nesting depth exceeds ${limits.maxDepth}")
        }
        val node = raw as? Map<*, *> ?: throw err(pointer, "must be an object")
        val op = node["op"] as? String ?: throw err(pointer, "\"op\" must be a string")
        return when (op) {
            "and", "or" -> {
                val nodes = node["nodes"] as? List<*>
                if (nodes.isNullOrEmpty()) throw err("$pointer/nodes", "must be a non-empty array")
                val children = nodes.mapIndexed { i, child ->
                    parseNode(child, "$pointer/nodes/$i", depth + 1, limits, attrAllowed)
                }
                if (op == "and") Condition.And(children) else Condition.Or(children)
            }
            "not" -> {
                if (!node.containsKey("node")) throw err(pointer, "\"not\" requires a \"node\"")
                Condition.Not(parseNode(node["node"], "$pointer/node", depth + 1, limits, attrAllowed))
            }
            "eq", "gte", "lte", "gt", "lt" -> {
                val attr = requireAttr(node, pointer, attrAllowed)
                val value = Scalar.of(node["value"])
                    ?: throw err("$pointer/value", "must be a string, number, or boolean")
                Condition.Comparison(attr, Condition.Operator.fromToken(op)!!, listOf(value))
            }
            "in" -> {
                val attr = requireAttr(node, pointer, attrAllowed)
                val values = node["values"] as? List<*>
                    ?: throw err("$pointer/values", "must be an array")
                if (values.isEmpty()) throw err("$pointer/values", "must be a non-empty array")
                if (values.size > limits.maxInValues) {
                    throw err("$pointer/values", "exceeds max ${limits.maxInValues} values")
                }
                val scalars = values.mapIndexed { i, v ->
                    Scalar.of(v) ?: throw err("$pointer/values/$i", "must be a string, number, or boolean")
                }
                Condition.Comparison(attr, Condition.Operator.IN, scalars)
            }
            else -> throw err(pointer, "unsupported op \"$op\"")
        }
    }

    private fun requireAttr(node: Map<*, *>, pointer: String, attrAllowed: (String) -> Boolean): String {
        val attr = node["attr"] as? String
        if (attr.isNullOrBlank()) throw err("$pointer/attr", "must be a non-empty string")
        if (!attrAllowed(attr)) throw err("$pointer/attr", "unknown attribute \"$attr\"")
        return attr
    }

    private fun err(pointer: String, message: String) =
        DomainValidationError("$pointer: $message", pointer = pointer)
}
