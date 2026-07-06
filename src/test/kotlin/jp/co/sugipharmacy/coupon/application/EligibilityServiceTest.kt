package jp.co.sugipharmacy.coupon.application

import jp.co.sugipharmacy.coupon.domain.Scalar
import jp.co.sugipharmacy.coupon.domain.coupon.Coupon
import jp.co.sugipharmacy.coupon.domain.coupon.CouponStatus
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import jp.co.sugipharmacy.coupon.domain.rule.Condition
import jp.co.sugipharmacy.coupon.domain.rule.DistributionRule
import jp.co.sugipharmacy.coupon.infrastructure.coupon.InMemoryCouponRepository
import jp.co.sugipharmacy.coupon.infrastructure.percolator.InMemoryPercolatorAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * eligibility の合成規則: percolate 該当の SEGMENT（クーポン有効期間内）∪ 有効期間内の ALL。
 * SUSPENDED を差し引かないこと（BFF の責務）までを仕様として固定する。
 */
class EligibilityServiceTest {

    private val at = Instant.parse("2026-07-15T00:00:00Z")
    private val from = Instant.parse("2026-07-01T00:00:00Z")
    private val to = Instant.parse("2026-07-31T23:59:59Z")
    private val pastFrom = Instant.parse("2026-06-01T00:00:00Z")
    private val pastTo = Instant.parse("2026-06-30T23:59:59Z")

    private lateinit var percolator: InMemoryPercolatorAdapter
    private lateinit var coupons: InMemoryCouponRepository
    private lateinit var service: EligibilityService

    @BeforeEach
    fun setUp() {
        percolator = InMemoryPercolatorAdapter()
        coupons = InMemoryCouponRepository()
        service = EligibilityService(percolator, coupons)
    }

    private fun ageGte20Rule(couponId: String, ruleFrom: Instant = from, ruleTo: Instant = to) =
        DistributionRule(
            couponId,
            Condition.Comparison("age", Condition.Operator.GTE, listOf(Scalar.of(20)!!)),
            ruleFrom,
            ruleTo,
        )

    private fun coupon(
        id: String,
        type: DistributionType,
        status: CouponStatus = CouponStatus.ACTIVE,
        validFrom: Instant = from,
        validTo: Instant = to,
    ) = Coupon(id, type, validFrom, validTo, status)

    @Test
    fun `percolate 該当の SEGMENT と期間内の ALL の和集合を返す`() {
        coupons.save(coupon("SEG-1", DistributionType.SEGMENT))
        coupons.save(coupon("ALL-1", DistributionType.ALL))
        percolator.register(ageGte20Rule("SEG-1"))

        val result = service.findEligibleCoupons(mapOf("age" to 25), at)

        assertThat(result).containsExactlyInAnyOrder(
            EligibleCoupon("SEG-1", DistributionType.SEGMENT),
            EligibleCoupon("ALL-1", DistributionType.ALL),
        )
    }

    @Test
    fun `条件に該当しない属性では SEGMENT は返らず ALL のみ`() {
        coupons.save(coupon("SEG-1", DistributionType.SEGMENT))
        coupons.save(coupon("ALL-1", DistributionType.ALL))
        percolator.register(ageGte20Rule("SEG-1"))

        val result = service.findEligibleCoupons(mapOf("age" to 19), at)

        assertThat(result).containsExactly(EligibleCoupon("ALL-1", DistributionType.ALL))
    }

    @Test
    fun `ルールが該当してもクーポン自体の有効期間外なら返さない`() {
        coupons.save(coupon("SEG-OLD", DistributionType.SEGMENT, validFrom = pastFrom, validTo = pastTo))
        percolator.register(ageGte20Rule("SEG-OLD"))

        assertThat(service.findEligibleCoupons(mapOf("age" to 25), at)).isEmpty()
    }

    @Test
    fun `ルール自体の有効期間外なら percolate に当たらない`() {
        coupons.save(coupon("SEG-1", DistributionType.SEGMENT))
        percolator.register(ageGte20Rule("SEG-1", ruleFrom = pastFrom, ruleTo = pastTo))

        assertThat(service.findEligibleCoupons(mapOf("age" to 25), at)).isEmpty()
    }

    @Test
    fun `ルールだけありマスタ未登録のクーポンは黙って落ちる（登録順序に依存しない）`() {
        percolator.register(ageGte20Rule("SEG-UNKNOWN"))

        assertThat(service.findEligibleCoupons(mapOf("age" to 25), at)).isEmpty()
    }

    @Test
    fun `ルールが INDIVIDUAL クーポンを指していても返さない（属性では決まらない）`() {
        coupons.save(coupon("IND-1", DistributionType.INDIVIDUAL))
        percolator.register(ageGte20Rule("IND-1"))

        assertThat(service.findEligibleCoupons(mapOf("age" to 25), at)).isEmpty()
    }

    @Test
    fun `SUSPENDED は差し引かない（即時停止は BFF が suspended 一覧で行う）`() {
        coupons.save(coupon("SEG-SUS", DistributionType.SEGMENT, status = CouponStatus.SUSPENDED))
        coupons.save(coupon("ALL-SUS", DistributionType.ALL, status = CouponStatus.SUSPENDED))
        percolator.register(ageGte20Rule("SEG-SUS"))

        val result = service.findEligibleCoupons(mapOf("age" to 25), at)

        assertThat(result).containsExactlyInAnyOrder(
            EligibleCoupon("SEG-SUS", DistributionType.SEGMENT),
            EligibleCoupon("ALL-SUS", DistributionType.ALL),
        )
    }

    @Test
    fun `有効期間外の ALL は付与しない`() {
        coupons.save(coupon("ALL-OLD", DistributionType.ALL, validFrom = pastFrom, validTo = pastTo))

        assertThat(service.findEligibleCoupons(mapOf("age" to 25), at)).isEmpty()
    }

    @Test
    fun `同一クーポンが複数経路で該当しても重複しない`() {
        coupons.save(coupon("SEG-1", DistributionType.SEGMENT))
        percolator.register(ageGte20Rule("SEG-1"))
        percolator.register(ageGte20Rule("SEG-1")) // 同一クーポンへ複数ルール

        val result = service.findEligibleCoupons(mapOf("age" to 25), at)

        assertThat(result).containsExactly(EligibleCoupon("SEG-1", DistributionType.SEGMENT))
    }
}
