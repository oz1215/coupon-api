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
 * 合成規則のユニットテスト:
 * 表示一覧 = (eligibility の生の結果) ∪ (付与済み INDIVIDUAL のうち有効期間内かつ ACTIVE) − 消費済み − 停止中。
 * eligibility 側は実物（インメモリ実装）で組み立て、member → eligibility の
 * 一方向依存のまま合成が成り立つこと、和・差が合成層にしか無いことを確認する。
 */
class MemberCouponListServiceTest {

    private val from = Instant.parse("2026-07-01T00:00:00Z")
    private val to = Instant.parse("2026-07-31T23:59:59Z")
    private val at = Instant.parse("2026-07-15T00:00:00Z")

    private val coupons = InMemoryCouponRepository()
    private val percolator = InMemoryPercolatorAdapter()
    private val stateService = MemberCouponStateService(InMemoryMemberCouponStateRepository())
    private val eligibilityService = EligibilityService(percolator, coupons)
    private val grantService = MemberCouponGrantService(CouponService(coupons), stateService)
    private val listService = MemberCouponListService(
        eligibilityService = eligibilityService,
        couponService = CouponService(coupons),
        stateService = stateService,
    )

    private fun registerCoupon(
        couponId: String,
        type: DistributionType = DistributionType.ALL,
        status: CouponStatus = CouponStatus.ACTIVE,
        validFrom: Instant = from,
        validTo: Instant = to,
    ) {
        coupons.save(Coupon(couponId, type, validFrom, validTo, status))
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

    @Test
    fun `付与済みINDIVIDUALは表示一覧に合成され、未付与の会員には出ない`() {
        registerCoupon("ALL-1")
        registerCoupon("IND-1", DistributionType.INDIVIDUAL)
        grantService.grant("M-1", "IND-1")

        assertEquals(listOf("ALL-1", "IND-1"), displayIds("M-1"))
        val individual = listService.getDisplayCoupons("M-1", mapOf("age" to 25), at)
            .single { it.couponId == "IND-1" }
        assertEquals(DistributionType.INDIVIDUAL, individual.distributionType)

        // 付与されていない会員の一覧には INDIVIDUAL は現れない
        assertEquals(listOf("ALL-1"), displayIds("M-2"))
    }

    @Test
    fun `付与済みINDIVIDUALも消費済みなら差し引かれる`() {
        registerCoupon("IND-1", DistributionType.INDIVIDUAL)
        grantService.grant("M-1", "IND-1")
        stateService.markUsed("M-1", listOf("IND-1"))

        assertEquals(emptyList(), displayIds("M-1"))
    }

    @Test
    fun `付与済みINDIVIDUALも停止中（SUSPENDED）なら差し引かれる`() {
        registerCoupon("IND-1", DistributionType.INDIVIDUAL)
        grantService.grant("M-1", "IND-1")
        // 付与後に緊急停止（同一IDの再登録で status 更新）
        registerCoupon("IND-1", DistributionType.INDIVIDUAL, status = CouponStatus.SUSPENDED)

        assertEquals(emptyList(), displayIds("M-1"))
    }

    @Test
    fun `付与済みINDIVIDUALはクーポンマスタの有効期間外なら出ない`() {
        registerCoupon(
            "IND-EXPIRED",
            DistributionType.INDIVIDUAL,
            validFrom = Instant.parse("2026-06-01T00:00:00Z"),
            validTo = Instant.parse("2026-06-30T23:59:59Z"),
        )
        registerCoupon(
            "IND-FUTURE",
            DistributionType.INDIVIDUAL,
            validFrom = Instant.parse("2026-08-01T00:00:00Z"),
            validTo = Instant.parse("2026-08-31T23:59:59Z"),
        )
        registerCoupon("IND-NOW", DistributionType.INDIVIDUAL)
        grantService.grant("M-1", "IND-EXPIRED")
        grantService.grant("M-1", "IND-FUTURE")
        grantService.grant("M-1", "IND-NOW")

        assertEquals(listOf("IND-NOW"), displayIds("M-1"))
    }

    @Test
    fun `ガードレール - 付与済みINDIVIDUALはeligibilityの生集合には漏れない`() {
        registerCoupon("ALL-1")
        registerCoupon("IND-1", DistributionType.INDIVIDUAL)
        grantService.grant("M-1", "IND-1")

        // eligibility は付与を知らない: 生集合は SEGMENT∪ALL のまま
        val raw = eligibilityService.findEligibleCoupons(mapOf("age" to 25), at).map { it.couponId }
        assertEquals(listOf("ALL-1"), raw)

        // 和は合成層でのみ起こる
        assertEquals(listOf("ALL-1", "IND-1"), displayIds("M-1"))
    }
}
