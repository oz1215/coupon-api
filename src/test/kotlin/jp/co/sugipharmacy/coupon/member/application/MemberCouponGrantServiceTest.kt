package jp.co.sugipharmacy.coupon.member.application

import jp.co.sugipharmacy.coupon.application.CouponService
import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.domain.coupon.Coupon
import jp.co.sugipharmacy.coupon.domain.coupon.CouponStatus
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import jp.co.sugipharmacy.coupon.infrastructure.coupon.InMemoryCouponRepository
import jp.co.sugipharmacy.coupon.member.infrastructure.InMemoryMemberCouponStateRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 個別付与（ウェルカム/イベント）のユニットテスト。
 * INDIVIDUAL のみ付与でき、granted フラグとして member モジュールのストアにだけ記録される。
 */
class MemberCouponGrantServiceTest {

    private val from = Instant.parse("2026-07-01T00:00:00Z")
    private val to = Instant.parse("2026-07-31T23:59:59Z")

    private val coupons = InMemoryCouponRepository()
    private val stateService = MemberCouponStateService(InMemoryMemberCouponStateRepository())
    private val grantService = MemberCouponGrantService(CouponService(coupons), stateService)

    private fun registerCoupon(couponId: String, type: DistributionType) {
        coupons.save(Coupon(couponId, type, from, to, CouponStatus.ACTIVE))
    }

    @Test
    fun `INDIVIDUALクーポンを付与すると granted になる`() {
        registerCoupon("IND-1", DistributionType.INDIVIDUAL)
        registerCoupon("IND-2", DistributionType.INDIVIDUAL)

        grantService.grant("M-1", "IND-1")
        grantService.grant("M-1", "IND-2")

        assertEquals(setOf("IND-1", "IND-2"), stateService.getGrantedCouponIds("M-1"))
        // 他会員には影響しない
        assertTrue(stateService.getGrantedCouponIds("M-2").isEmpty())
    }

    @Test
    fun `付与は冪等 - 同じクーポンを二度付与しても granted のまま`() {
        registerCoupon("IND-1", DistributionType.INDIVIDUAL)
        grantService.grant("M-1", "IND-1")
        grantService.grant("M-1", "IND-1")
        assertEquals(setOf("IND-1"), stateService.getGrantedCouponIds("M-1"))
    }

    @Test
    fun `クーポンマスタに存在しないIDの付与は拒否される`() {
        assertThrows<DomainValidationError> { grantService.grant("M-1", "NOPE") }
        assertTrue(stateService.getGrantedCouponIds("M-1").isEmpty())
    }

    @Test
    fun `INDIVIDUAL以外（ALL・SEGMENT）の付与は拒否される`() {
        registerCoupon("ALL-1", DistributionType.ALL)
        registerCoupon("SEG-1", DistributionType.SEGMENT)

        assertThrows<DomainValidationError> { grantService.grant("M-1", "ALL-1") }
        assertThrows<DomainValidationError> { grantService.grant("M-1", "SEG-1") }
        assertTrue(stateService.getGrantedCouponIds("M-1").isEmpty())
    }

    @Test
    fun `付与しても既存フラグ（選択・お気に入り・消費済み）は保持される`() {
        registerCoupon("IND-1", DistributionType.INDIVIDUAL)
        stateService.setFavorite("M-1", "IND-1", true)

        grantService.grant("M-1", "IND-1")

        assertEquals(listOf("IND-1"), stateService.getFavoriteCouponIds("M-1"))
        assertEquals(setOf("IND-1"), stateService.getGrantedCouponIds("M-1"))
    }
}
