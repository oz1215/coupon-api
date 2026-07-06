package jp.co.sugipharmacy.coupon.domain.rule

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.domain.Scalar
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class DistributionRuleTest {

    private val from = Instant.parse("2026-07-01T00:00:00Z")
    private val to = Instant.parse("2026-07-31T23:59:59Z")
    private val age20 = Condition.Comparison("age", Condition.Operator.EQ, listOf(Scalar.of(20)!!))

    private fun rule() = DistributionRule("CPN-1", age20, from, to)

    private fun attrs(age: Int) = MemberAttributes.from(mapOf("age" to age))

    @Test
    fun `期間内かつ条件該当で matches（期間は両端を含む）`() {
        assertThat(rule().matches(attrs(20), from)).isTrue()
        assertThat(rule().matches(attrs(20), to)).isTrue()
        assertThat(rule().matches(attrs(20), from.minusSeconds(1))).isFalse()
        assertThat(rule().matches(attrs(20), to.plusSeconds(1))).isFalse()
    }

    @Test
    fun `期間内でも条件非該当なら matches しない`() {
        assertThat(rule().matches(attrs(21), from)).isFalse()
    }

    @Test
    fun `validTo が validFrom より前なら生成できない`() {
        assertThatThrownBy { DistributionRule("CPN-1", age20, to, from) }
            .isInstanceOf(DomainValidationError::class.java)
    }

    @Test
    fun `couponId が空白なら生成できない`() {
        assertThatThrownBy { DistributionRule(" ", age20, from, to) }
            .isInstanceOf(DomainValidationError::class.java)
    }
}
