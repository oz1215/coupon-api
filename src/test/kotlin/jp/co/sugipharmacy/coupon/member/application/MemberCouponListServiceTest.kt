package jp.co.sugipharmacy.coupon.member.application

import jp.co.sugipharmacy.coupon.application.CouponService
import jp.co.sugipharmacy.coupon.application.EligibilityService
import jp.co.sugipharmacy.coupon.domain.coupon.Coupon
import jp.co.sugipharmacy.coupon.domain.coupon.CouponStatus
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import jp.co.sugipharmacy.coupon.domain.rule.ConditionParser
import jp.co.sugipharmacy.coupon.domain.rule.DistributionRule
import jp.co.sugipharmacy.coupon.infrastructure.coupon.InMemoryCouponRepository
import jp.co.sugipharmacy.coupon.infrastructure.percolator.InMemoryPercolatorAdapter
import jp.co.sugipharmacy.coupon.member.infrastructure.InMemoryMemberCouponStateRepository
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/**
 * 合成規則のユニットテスト: 表示一覧 = eligibility の生の結果 − 消費済み − 停止中。
 * eligibility 側は実物（インメモリ実装）で組み立て、member → eligibility の
 * 一方向依存のまま合成が成り立つことを確認する。
 */
class MemberCouponListServiceTest {

    private val from = Instant.parse("2026-07-01T00:00:00Z")
    private val to = Instant.parse("2026-07-31T23:59:59Z")
    private val at = Instant.parse("2026-07-15T00:00:00Z")

    private val coupons = InMemoryCouponRepository()
    private val percolator = InMemoryPercolatorAdapter()
    private val stateService = MemberCouponStateService(InMemoryMemberCouponStateRepository())
    private val listService = MemberCouponListService(
        eligibilityService = EligibilityService(percolator, coupons),
        couponService = CouponService(coupons),
        stateService = stateService,
    )

    private fun registerCoupon(
        couponId: String,
        type: DistributionType = DistributionType.ALL,
        status: CouponStatus = CouponStatus.ACTIVE,
    ) {
        coupons.save(Coupon(couponId, type, from, to, status))
    }

    private fun registerAgeRule(couponId: String, age: Int) {
        percolator.register(
            DistributionRule(
                couponId = couponId,
                condition = ConditionParser.parse(mapOf("key" to "age", "operator" to "eq", "value" to age)),
                validFrom = from,
                validTo = to,
            ),
        )
    }

    private fun displayIds(memberId: String, attributes: Map<String, Any?> = mapOf("age" to 25)): List<String> =
        listService.getDisplayCoupons(memberId, attributes, at).map { it.couponId }.sorted()

    @Test
    fun `状態が空なら eligibility の生の結果と一致する`() {
        registerCoupon("ALL-1")
        registerCoupon("SEG-1", DistributionType.SEGMENT)
        registerAgeRule("SEG-1", 25)

        assertEquals(listOf("ALL-1", "SEG-1"), displayIds("M-1"))
    }

    @Test
    fun `消費済みクーポンは差し引かれる`() {
        registerCoupon("ALL-1")
        registerCoupon("ALL-2")
        stateService.markUsed("M-1", listOf("ALL-1"))

        assertEquals(listOf("ALL-2"), displayIds("M-1"))
        // 他会員には影響しない
        assertEquals(listOf("ALL-1", "ALL-2"), displayIds("M-2"))
    }

    @Test
    fun `停止中（SUSPENDED）クーポンは差し引かれる`() {
        registerCoupon("ALL-1")
        registerCoupon("ALL-SUS", status = CouponStatus.SUSPENDED)

        assertEquals(listOf("ALL-1"), displayIds("M-1"))
    }

    @Test
    fun `ALL配信クーポンは消費済み・停止中でない限り残る`() {
        registerCoupon("ALL-KEEP")
        registerCoupon("ALL-USED")
        registerCoupon("ALL-SUS", status = CouponStatus.SUSPENDED)
        registerCoupon("SEG-MISS", DistributionType.SEGMENT)
        registerAgeRule("SEG-MISS", 40) // 属性不一致 → eligibility 段階で落ちる

        stateService.markUsed("M-1", listOf("ALL-USED"))

        assertEquals(listOf("ALL-KEEP"), displayIds("M-1"))
    }

    @Test
    fun `選択・お気に入りは表示一覧から差し引かない`() {
        registerCoupon("ALL-1")
        stateService.setSelection("M-1", "ALL-1", true)
        stateService.setFavorite("M-1", "ALL-1", true)

        assertEquals(listOf("ALL-1"), displayIds("M-1"))
    }
}
