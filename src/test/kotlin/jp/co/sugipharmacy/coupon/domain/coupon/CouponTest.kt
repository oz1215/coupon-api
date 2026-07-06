package jp.co.sugipharmacy.coupon.domain.coupon

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class CouponTest {

    private val from = Instant.parse("2026-07-01T00:00:00Z")
    private val to = Instant.parse("2026-07-31T23:59:59Z")

    private fun coupon() = Coupon("CPN-1", DistributionType.SEGMENT, from, to, CouponStatus.ACTIVE)

    @Test
    fun `有効期間は両端を含む`() {
        assertThat(coupon().isWithinValidPeriod(from.minusSeconds(1))).isFalse()
        assertThat(coupon().isWithinValidPeriod(from)).isTrue()
        assertThat(coupon().isWithinValidPeriod(to)).isTrue()
        assertThat(coupon().isWithinValidPeriod(to.plusSeconds(1))).isFalse()
    }

    @Test
    fun `validTo が validFrom より前なら生成できない`() {
        assertThatThrownBy { Coupon("CPN-1", DistributionType.ALL, to, from, CouponStatus.ACTIVE) }
            .isInstanceOf(DomainValidationError::class.java)
    }

    @Test
    fun `couponId が空白なら生成できない`() {
        assertThatThrownBy { Coupon(" ", DistributionType.ALL, from, to, CouponStatus.ACTIVE) }
            .isInstanceOf(DomainValidationError::class.java)
    }

    @Test
    fun `SUSPENDED のとき isSuspended`() {
        val suspended = Coupon("CPN-1", DistributionType.ALL, from, to, CouponStatus.SUSPENDED)
        assertThat(suspended.isSuspended).isTrue()
        assertThat(coupon().isSuspended).isFalse()
    }
}
