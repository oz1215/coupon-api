package jp.co.sugipharmacy.coupon.infrastructure.percolator

import jp.co.sugipharmacy.coupon.domain.Scalar
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import jp.co.sugipharmacy.coupon.domain.rule.Condition
import jp.co.sugipharmacy.coupon.domain.rule.DistributionRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class InMemoryPercolatorAdapterTest {

    private val from = Instant.parse("2026-07-01T00:00:00Z")
    private val to = Instant.parse("2026-07-31T23:59:59Z")

    private fun rule(couponId: String, gte: Int) = DistributionRule(
        couponId,
        Condition.Comparison("age", Condition.Operator.GTE, listOf(Scalar.of(gte)!!)),
        from,
        to,
    )

    @Test
    fun `登録済みルールのうち属性に該当するクーポンIDの集合を返す`() {
        val adapter = InMemoryPercolatorAdapter()
        adapter.register(rule("CPN-20", 20))
        adapter.register(rule("CPN-40", 40))

        val hit = adapter.percolate(MemberAttributes.from(mapOf("age" to 30)), from)

        assertThat(hit).containsExactly("CPN-20")
    }

    @Test
    fun `ルール有効期間は両端を含む`() {
        val adapter = InMemoryPercolatorAdapter()
        adapter.register(rule("CPN-20", 20))
        val attrs = MemberAttributes.from(mapOf("age" to 30))

        assertThat(adapter.percolate(attrs, from)).containsExactly("CPN-20")
        assertThat(adapter.percolate(attrs, to)).containsExactly("CPN-20")
        assertThat(adapter.percolate(attrs, from.minusSeconds(1))).isEmpty()
        assertThat(adapter.percolate(attrs, to.plusSeconds(1))).isEmpty()
    }
}
