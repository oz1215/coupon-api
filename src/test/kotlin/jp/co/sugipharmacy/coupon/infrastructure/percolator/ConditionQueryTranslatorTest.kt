package jp.co.sugipharmacy.coupon.infrastructure.percolator

import jp.co.sugipharmacy.coupon.domain.Scalar
import jp.co.sugipharmacy.coupon.domain.rule.Condition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * 条件AST → OpenSearch クエリDSL変換の構造テスト（OpenSearch 不要・常時実行）。
 * 実機での挙動同値性は OpenSearchPercolatorAdapterIntegrationTest（要 Docker）が担う。
 */
class ConditionQueryTranslatorTest {

    private fun comparison(key: String, op: Condition.Operator, vararg values: Any) =
        Condition.Comparison(key, op, values.map { Scalar.of(it)!! })

    @Test
    fun `eq の数値化できる値は num サブフィールドへの term になる（文字列20 と 数値20 の同一視）`() {
        assertThat(ConditionQueryTranslator.translate(comparison("age", Condition.Operator.EQ, "20")))
            .isEqualTo(mapOf("term" to mapOf("attrs.age.num" to mapOf("value" to BigDecimal(20)))))
        assertThat(ConditionQueryTranslator.translate(comparison("age", Condition.Operator.EQ, 20)))
            .isEqualTo(mapOf("term" to mapOf("attrs.age.num" to mapOf("value" to BigDecimal(20)))))
    }

    @Test
    fun `eq の数値化できない値は txt サブフィールドへの term になる`() {
        assertThat(ConditionQueryTranslator.translate(comparison("gender", Condition.Operator.EQ, "female")))
            .isEqualTo(mapOf("term" to mapOf("attrs.gender.txt" to mapOf("value" to "female"))))
        // 真偽値は数値化されない — 文字列表現で比較（InMemory の looselyEquals と同じ）
        assertThat(ConditionQueryTranslator.translate(comparison("flag", Condition.Operator.EQ, true)))
            .isEqualTo(mapOf("term" to mapOf("attrs.flag.txt" to mapOf("value" to "true"))))
    }

    @Test
    fun `in は terms になり、数値系と文字列系が混在すれば bool_should で束ねる`() {
        assertThat(ConditionQueryTranslator.translate(comparison("store", Condition.Operator.IN, "001", "002")))
            .isEqualTo(mapOf("terms" to mapOf("attrs.store.num" to listOf(BigDecimal(1), BigDecimal(2)))))

        assertThat(ConditionQueryTranslator.translate(comparison("store", Condition.Operator.IN, "A01", 2)))
            .isEqualTo(
                mapOf(
                    "bool" to mapOf(
                        "should" to listOf(
                            mapOf("terms" to mapOf("attrs.store.num" to listOf(BigDecimal(2)))),
                            mapOf("terms" to mapOf("attrs.store.txt" to listOf("A01"))),
                        ),
                        "minimum_should_match" to 1,
                    ),
                ),
            )
    }

    @Test
    fun `数値比較は num サブフィールドへの range になる`() {
        assertThat(ConditionQueryTranslator.translate(comparison("age", Condition.Operator.GTE, 20)))
            .isEqualTo(mapOf("range" to mapOf("attrs.age.num" to mapOf("gte" to BigDecimal(20)))))
        assertThat(ConditionQueryTranslator.translate(comparison("age", Condition.Operator.LT, "65")))
            .isEqualTo(mapOf("range" to mapOf("attrs.age.num" to mapOf("lt" to BigDecimal(65)))))
    }

    @Test
    fun `数値化できない境界値の range は match_none（fail-safe・決して該当しない）`() {
        assertThat(ConditionQueryTranslator.translate(comparison("age", Condition.Operator.GTE, "abc")))
            .isEqualTo(mapOf("match_none" to emptyMap<String, Any>()))
    }

    @Test
    fun `and は bool_filter、or は bool_should + minimum_should_match=1 で再帰的にネストする`() {
        val condition = Condition.Or(
            listOf(
                Condition.And(
                    listOf(
                        comparison("age", Condition.Operator.GTE, 20),
                        comparison("gender", Condition.Operator.EQ, "male"),
                    ),
                ),
                comparison("age", Condition.Operator.GTE, 60),
            ),
        )
        assertThat(ConditionQueryTranslator.translate(condition)).isEqualTo(
            mapOf(
                "bool" to mapOf(
                    "should" to listOf(
                        mapOf(
                            "bool" to mapOf(
                                "filter" to listOf(
                                    mapOf("range" to mapOf("attrs.age.num" to mapOf("gte" to BigDecimal(20)))),
                                    mapOf("term" to mapOf("attrs.gender.txt" to mapOf("value" to "male"))),
                                ),
                            ),
                        ),
                        mapOf("range" to mapOf("attrs.age.num" to mapOf("gte" to BigDecimal(60)))),
                    ),
                    "minimum_should_match" to 1,
                ),
            ),
        )
    }
}
