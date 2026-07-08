package jp.co.sugipharmacy.coupon.domain.rule

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 正準AST（coupon-rule-schema）→ 内部 Condition への変換＋受領時検証。
 * 構造の解釈（and/or/not, eq/範囲/in）と、不正入力の弾き（未知op/型不一致/空nodes/
 * ネスト深さ上限/in要素数上限/未知attr）を網羅する。
 */
class CanonicalConditionParserTest {

    private fun attrs(vararg pairs: Pair<String, Any?>): MemberAttributes = MemberAttributes.from(mapOf(*pairs))

    private fun parse(json: Any?): Condition = CanonicalConditionParser.parse(json)

    @Test
    fun `eq を解釈する`() {
        val c = parse(mapOf("op" to "eq", "attr" to "age_band", "value" to "20s"))
        assertThat(c.evaluate(attrs("age_band" to "20s"))).isTrue()
        assertThat(c.evaluate(attrs("age_band" to "30s"))).isFalse()
    }

    @Test
    fun `範囲（gte lte）を境界含めて解釈する`() {
        val gte20 = parse(mapOf("op" to "gte", "attr" to "age", "value" to 20))
        assertThat(gte20.evaluate(attrs("age" to 19))).isFalse()
        assertThat(gte20.evaluate(attrs("age" to 20))).isTrue()
        assertThat(gte20.evaluate(attrs("age" to 21))).isTrue()
        val lte29 = parse(mapOf("op" to "lte", "attr" to "age", "value" to 29))
        assertThat(lte29.evaluate(attrs("age" to 29))).isTrue()
        assertThat(lte29.evaluate(attrs("age" to 30))).isFalse()
    }

    @Test
    fun `in を解釈する`() {
        val c = parse(mapOf("op" to "in", "attr" to "pref", "values" to listOf("tokyo", "osaka")))
        assertThat(c.evaluate(attrs("pref" to "osaka"))).isTrue()
        assertThat(c.evaluate(attrs("pref" to "kyoto"))).isFalse()
    }

    @Test
    fun `and or not をネストして解釈する（20代 かつ NOT 酒類フラグ）`() {
        val c = parse(
            mapOf(
                "op" to "and",
                "nodes" to listOf(
                    mapOf(
                        "op" to "or",
                        "nodes" to listOf(
                            mapOf("op" to "eq", "attr" to "age_band", "value" to "20s"),
                            mapOf("op" to "eq", "attr" to "age_band", "value" to "30s"),
                        ),
                    ),
                    mapOf("op" to "not", "node" to mapOf("op" to "eq", "attr" to "liquor", "value" to true)),
                ),
            ),
        )
        // liquor=false（未設定）→ not(eq true) が真、20s → 該当
        assertThat(c.evaluate(attrs("age_band" to "20s", "liquor" to false))).isTrue()
        // liquor=true → not(eq true) が偽 → 非該当
        assertThat(c.evaluate(attrs("age_band" to "20s", "liquor" to true))).isFalse()
        // 40s → OR が偽 → 非該当
        assertThat(c.evaluate(attrs("age_band" to "40s", "liquor" to false))).isFalse()
    }

    @Test
    fun `未知の op は弾く（pointer 付き）`() {
        assertThatThrownBy { parse(mapOf("op" to "between", "attr" to "age", "value" to 20)) }
            .isInstanceOf(DomainValidationError::class.java)
            .hasMessageContaining("op")
    }

    @Test
    fun `空 nodes は弾く`() {
        assertThatThrownBy { parse(mapOf("op" to "and", "nodes" to emptyList<Any>())) }
            .isInstanceOf(DomainValidationError::class.java)
    }

    @Test
    fun `ネスト深さ上限を超えたら弾く`() {
        val deep = mapOf("op" to "and", "nodes" to listOf(mapOf("op" to "and", "nodes" to listOf(mapOf("op" to "eq", "attr" to "age", "value" to 1)))))
        assertThatThrownBy { CanonicalConditionParser.parse(deep, ConditionLimits(maxDepth = 2)) }
            .isInstanceOf(DomainValidationError::class.java)
            .hasMessageContaining("depth")
    }

    @Test
    fun `in の要素数上限を超えたら弾く`() {
        val many = mapOf("op" to "in", "attr" to "pref", "values" to listOf("a", "b", "c"))
        assertThatThrownBy { CanonicalConditionParser.parse(many, ConditionLimits(maxInValues = 2)) }
            .isInstanceOf(DomainValidationError::class.java)
    }

    @Test
    fun `カタログ外の属性は弾く`() {
        val cond = mapOf("op" to "eq", "attr" to "unknown_attr", "value" to "x")
        assertThatThrownBy { CanonicalConditionParser.parse(cond, attrAllowed = { it == "age" }) }
            .isInstanceOf(DomainValidationError::class.java)
            .hasMessageContaining("unknown_attr")
    }

    @Test
    fun `スカラーでない value は弾く（型不一致）`() {
        val cond = mapOf("op" to "eq", "attr" to "age", "value" to mapOf("nested" to 1))
        assertThatThrownBy { parse(cond) }.isInstanceOf(DomainValidationError::class.java)
    }
}
