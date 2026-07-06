package jp.co.sugipharmacy.coupon.domain.rule

import jp.co.sugipharmacy.coupon.domain.Scalar
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import jp.co.sugipharmacy.coupon.domain.rule.Condition.Operator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 条件ASTの評価 = 逆引き該当判定の心臓部。
 * 境界値（◯歳以上/以下）・型ゆらぎ（"20" と 20）・AND/OR ネストを網羅する。
 */
class ConditionEvaluatorTest {

    private fun attrs(vararg pairs: Pair<String, Any?>): MemberAttributes =
        MemberAttributes.from(mapOf(*pairs))

    private fun cmp(key: String, operator: Operator, vararg values: Any): Condition.Comparison =
        Condition.Comparison(key, operator, values.map { Scalar.of(it)!! })

    @Nested
    @DisplayName("eq")
    inner class Eq {
        @Test
        fun `文字列同士が一致すれば該当`() {
            assertThat(cmp("gender", Operator.EQ, "F").evaluate(attrs("gender" to "F"))).isTrue()
        }

        @Test
        fun `文字列同士が不一致なら非該当`() {
            assertThat(cmp("gender", Operator.EQ, "F").evaluate(attrs("gender" to "M"))).isFalse()
        }

        @Test
        fun `属性の文字列 20 と条件の数値 20 を同一視する`() {
            assertThat(cmp("age", Operator.EQ, 20).evaluate(attrs("age" to "20"))).isTrue()
        }

        @Test
        fun `属性の数値 20 と条件の文字列 20 を同一視する`() {
            assertThat(cmp("age", Operator.EQ, "20").evaluate(attrs("age" to 20))).isTrue()
        }

        @Test
        fun `真偽値フラグは true と一致する`() {
            assertThat(
                cmp("boughtAlcoholWithin30Days", Operator.EQ, true)
                    .evaluate(attrs("boughtAlcoholWithin30Days" to true)),
            ).isTrue()
        }

        @Test
        fun `属性が存在しなければ非該当（配布漏れに倒す）`() {
            assertThat(cmp("age", Operator.EQ, 20).evaluate(attrs())).isFalse()
        }
    }

    @Nested
    @DisplayName("範囲演算子の境界値")
    inner class RangeBoundaries {
        @Test
        fun `gte 20 は 19 で非該当・20 で該当・21 で該当`() {
            val condition = cmp("age", Operator.GTE, 20)
            assertThat(condition.evaluate(attrs("age" to 19))).isFalse()
            assertThat(condition.evaluate(attrs("age" to 20))).isTrue()
            assertThat(condition.evaluate(attrs("age" to 21))).isTrue()
        }

        @Test
        fun `lte 29 は 29 で該当・30 で非該当`() {
            val condition = cmp("age", Operator.LTE, 29)
            assertThat(condition.evaluate(attrs("age" to 29))).isTrue()
            assertThat(condition.evaluate(attrs("age" to 30))).isFalse()
        }

        @Test
        fun `gt 20 は 20 で非該当・21 で該当`() {
            val condition = cmp("age", Operator.GT, 20)
            assertThat(condition.evaluate(attrs("age" to 20))).isFalse()
            assertThat(condition.evaluate(attrs("age" to 21))).isTrue()
        }

        @Test
        fun `lt 20 は 19 で該当・20 で非該当`() {
            val condition = cmp("age", Operator.LT, 20)
            assertThat(condition.evaluate(attrs("age" to 19))).isTrue()
            assertThat(condition.evaluate(attrs("age" to 20))).isFalse()
        }

        @Test
        fun `文字列の数値属性も数値として比較する`() {
            assertThat(cmp("age", Operator.GTE, 20).evaluate(attrs("age" to "20"))).isTrue()
        }

        @Test
        fun `数値化できない属性は範囲比較で非該当`() {
            assertThat(cmp("age", Operator.GTE, 20).evaluate(attrs("age" to "unknown"))).isFalse()
        }

        @Test
        fun `数値化できない条件値も範囲比較で非該当`() {
            assertThat(cmp("age", Operator.GTE, "unknown").evaluate(attrs("age" to 25))).isFalse()
        }
    }

    @Nested
    @DisplayName("in")
    inner class In {
        @Test
        fun `リストに含まれれば該当・含まれなければ非該当`() {
            val condition = cmp("prefecture", Operator.IN, "tokyo", "osaka")
            assertThat(condition.evaluate(attrs("prefecture" to "tokyo"))).isTrue()
            assertThat(condition.evaluate(attrs("prefecture" to "kyoto"))).isFalse()
        }

        @Test
        fun `in でも数値同一視が効く（文字列の 20 は数値リスト 20, 30 に含まれる）`() {
            assertThat(cmp("age", Operator.IN, 20, 30).evaluate(attrs("age" to "20"))).isTrue()
        }
    }

    @Nested
    @DisplayName("AND / OR ネスト")
    inner class Nesting {
        /** 20〜29歳（README の「20〜30代」型の年齢帯） */
        private val twenties = Condition.And(
            listOf(cmp("age", Operator.GTE, 20), cmp("age", Operator.LTE, 29)),
        )

        @Test
        fun `AND は全条件成立で該当・1つでも欠ければ非該当（境界 19-20-29-30）`() {
            assertThat(twenties.evaluate(attrs("age" to 19))).isFalse()
            assertThat(twenties.evaluate(attrs("age" to 20))).isTrue()
            assertThat(twenties.evaluate(attrs("age" to 29))).isTrue()
            assertThat(twenties.evaluate(attrs("age" to 30))).isFalse()
        }

        @Test
        fun `OR はいずれか成立で該当`() {
            val condition = Condition.Or(
                listOf(cmp("age", Operator.EQ, 20), cmp("age", Operator.EQ, 30)),
            )
            assertThat(condition.evaluate(attrs("age" to 20))).isTrue()
            assertThat(condition.evaluate(attrs("age" to 25))).isFalse()
            assertThat(condition.evaluate(attrs("age" to 30))).isTrue()
        }

        @Test
        fun `OR の下に AND を入れ子にできる（20代 または 60歳以上）`() {
            val condition = Condition.Or(listOf(twenties, cmp("age", Operator.GTE, 60)))
            assertThat(condition.evaluate(attrs("age" to 25))).isTrue()
            assertThat(condition.evaluate(attrs("age" to 45))).isFalse()
            assertThat(condition.evaluate(attrs("age" to 60))).isTrue()
        }

        @Test
        fun `AND の下に OR を入れ子にできる（20代 かつ 東京または大阪）`() {
            val condition = Condition.And(
                listOf(twenties, cmp("prefecture", Operator.IN, "tokyo", "osaka")),
            )
            assertThat(condition.evaluate(attrs("age" to 25, "prefecture" to "tokyo"))).isTrue()
            assertThat(condition.evaluate(attrs("age" to 25, "prefecture" to "kyoto"))).isFalse()
            assertThat(condition.evaluate(attrs("age" to 35, "prefecture" to "tokyo"))).isFalse()
        }
    }
}
