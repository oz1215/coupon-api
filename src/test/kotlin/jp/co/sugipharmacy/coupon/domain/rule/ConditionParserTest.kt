package jp.co.sugipharmacy.coupon.domain.rule

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.domain.Scalar
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import jp.co.sugipharmacy.coupon.domain.rule.Condition.Operator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ConditionParserTest {

    @Nested
    @DisplayName("簡易形（Confluence 仕様: rules 配列 = eq の OR）")
    inner class FlatForm {
        @Test
        fun `ルール1件は単一の eq 比較に正規化される`() {
            val condition = ConditionParser.parse(
                mapOf("rules" to listOf(mapOf("key" to "age", "value" to "20"))),
            )
            assertThat(condition).isEqualTo(
                Condition.Comparison("age", Operator.EQ, listOf(Scalar.Text("20"))),
            )
        }

        @Test
        fun `ルール複数件は eq の OR に正規化される`() {
            val condition = ConditionParser.parse(
                mapOf(
                    "rules" to listOf(
                        mapOf("key" to "age", "value" to "20"),
                        mapOf("key" to "age", "value" to "30"),
                    ),
                ),
            )
            assertThat(condition).isEqualTo(
                Condition.Or(
                    listOf(
                        Condition.Comparison("age", Operator.EQ, listOf(Scalar.Text("20"))),
                        Condition.Comparison("age", Operator.EQ, listOf(Scalar.Text("30"))),
                    ),
                ),
            )
        }

        @Test
        fun `正規化後の評価は OR 意味論（20 か 30 に一致）`() {
            val condition = ConditionParser.parse(
                mapOf(
                    "rules" to listOf(
                        mapOf("key" to "age", "value" to "20"),
                        mapOf("key" to "age", "value" to "30"),
                    ),
                ),
            )
            assertThat(condition.evaluate(MemberAttributes.from(mapOf("age" to 20)))).isTrue()
            assertThat(condition.evaluate(MemberAttributes.from(mapOf("age" to 25)))).isFalse()
            assertThat(condition.evaluate(MemberAttributes.from(mapOf("age" to 30)))).isTrue()
        }

        @Test
        fun `rules が空・配列でない場合は拒否`() {
            assertThatThrownBy { ConditionParser.parse(mapOf("rules" to emptyList<Any>())) }
                .isInstanceOf(DomainValidationError::class.java)
            assertThatThrownBy { ConditionParser.parse(mapOf("rules" to "age=20")) }
                .isInstanceOf(DomainValidationError::class.java)
        }

        @Test
        fun `key 欠落・空文字は拒否`() {
            assertThatThrownBy {
                ConditionParser.parse(mapOf("rules" to listOf(mapOf("value" to "20"))))
            }.isInstanceOf(DomainValidationError::class.java).hasMessageContaining("key")
            assertThatThrownBy {
                ConditionParser.parse(mapOf("rules" to listOf(mapOf("key" to " ", "value" to "20"))))
            }.isInstanceOf(DomainValidationError::class.java).hasMessageContaining("key")
        }

        @Test
        fun `value がスカラーでない場合は拒否`() {
            assertThatThrownBy {
                ConditionParser.parse(
                    mapOf("rules" to listOf(mapOf("key" to "age", "value" to mapOf("gte" to 20)))),
                )
            }.isInstanceOf(DomainValidationError::class.java).hasMessageContaining("value")
        }
    }

    @Nested
    @DisplayName("リッチ形（比較演算子 + AND/OR ネスト）")
    inner class RichForm {
        @Test
        fun `gte 比較を解析できる（◯歳以上）`() {
            val condition = ConditionParser.parse(
                mapOf("key" to "age", "operator" to "gte", "value" to 20),
            )
            assertThat(condition).isEqualTo(
                Condition.Comparison("age", Operator.GTE, listOf(Scalar.of(20)!!)),
            )
        }

        @Test
        fun `in は配列値を要求する`() {
            val condition = ConditionParser.parse(
                mapOf("key" to "prefecture", "operator" to "in", "value" to listOf("tokyo", "osaka")),
            )
            assertThat(condition).isEqualTo(
                Condition.Comparison(
                    "prefecture",
                    Operator.IN,
                    listOf(Scalar.Text("tokyo"), Scalar.Text("osaka")),
                ),
            )
            assertThatThrownBy {
                ConditionParser.parse(mapOf("key" to "p", "operator" to "in", "value" to "tokyo"))
            }.isInstanceOf(DomainValidationError::class.java)
            assertThatThrownBy {
                ConditionParser.parse(mapOf("key" to "p", "operator" to "in", "value" to emptyList<Any>()))
            }.isInstanceOf(DomainValidationError::class.java)
        }

        @Test
        fun `AND の下に OR をネストできる`() {
            val condition = ConditionParser.parse(
                mapOf(
                    "and" to listOf(
                        mapOf("key" to "age", "operator" to "gte", "value" to 20),
                        mapOf(
                            "or" to listOf(
                                mapOf("key" to "prefecture", "operator" to "eq", "value" to "tokyo"),
                                mapOf("key" to "prefecture", "operator" to "eq", "value" to "osaka"),
                            ),
                        ),
                    ),
                ),
            )
            assertThat(condition).isEqualTo(
                Condition.And(
                    listOf(
                        Condition.Comparison("age", Operator.GTE, listOf(Scalar.of(20)!!)),
                        Condition.Or(
                            listOf(
                                Condition.Comparison("prefecture", Operator.EQ, listOf(Scalar.Text("tokyo"))),
                                Condition.Comparison("prefecture", Operator.EQ, listOf(Scalar.Text("osaka"))),
                            ),
                        ),
                    ),
                ),
            )
        }

        @Test
        fun `and と or を同一ノードに併記したら拒否`() {
            assertThatThrownBy {
                ConditionParser.parse(mapOf("and" to listOf<Any>(), "or" to listOf<Any>()))
            }.isInstanceOf(DomainValidationError::class.java)
        }

        @Test
        fun `and が空配列なら拒否`() {
            assertThatThrownBy { ConditionParser.parse(mapOf("and" to emptyList<Any>())) }
                .isInstanceOf(DomainValidationError::class.java)
        }

        @Test
        fun `operator 欠落・未知の演算子は拒否`() {
            assertThatThrownBy { ConditionParser.parse(mapOf("key" to "age", "value" to 20)) }
                .isInstanceOf(DomainValidationError::class.java).hasMessageContaining("operator")
            assertThatThrownBy {
                ConditionParser.parse(mapOf("key" to "age", "operator" to "between", "value" to 20))
            }.isInstanceOf(DomainValidationError::class.java).hasMessageContaining("operator")
        }
    }

    @Test
    fun `オブジェクトでない入力は拒否`() {
        assertThatThrownBy { ConditionParser.parse("age >= 20") }
            .isInstanceOf(DomainValidationError::class.java)
        assertThatThrownBy { ConditionParser.parse(null) }
            .isInstanceOf(DomainValidationError::class.java)
        assertThatThrownBy { ConditionParser.parse(listOf(mapOf("key" to "age"))) }
            .isInstanceOf(DomainValidationError::class.java)
    }
}
