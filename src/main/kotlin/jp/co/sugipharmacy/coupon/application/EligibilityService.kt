package jp.co.sugipharmacy.coupon.application

import jp.co.sugipharmacy.coupon.application.port.CouponRepository
import jp.co.sugipharmacy.coupon.application.port.PercolatorPort
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import org.springframework.stereotype.Service
import java.time.Instant

data class EligibleCoupon(
    val couponId: String,
    val distributionType: DistributionType,
)

/**
 * クーポン一覧取得（属性に基づく該当判定＋全員配信の付与）。
 *
 * - SEGMENT: percolator への逆引き1回で該当クーポン集合を得る。
 * - ALL: 有効期間内の全員配信クーポンを常に付与する。
 * - INDIVIDUAL: 属性では決まらないため本判定の対象外（ウェルカム/イベント登録側の責務）。
 * - SUSPENDED は除外しない。停止の即時反映は BFF が GET /coupons/suspended の
 *   一覧を表示直前に差し引くことで行う（キャッシュに焼き込まない）。
 * - 消費済み・選択状態の差し引きも BFF の責務であり、ここでは行わない。
 */
@Service
class EligibilityService(
    private val percolator: PercolatorPort,
    private val coupons: CouponRepository,
) {
    fun findEligibleCoupons(rawAttributes: Map<String, Any?>, at: Instant): List<EligibleCoupon> {
        val attributes = MemberAttributes.from(rawAttributes)

        val matchedIds = percolator.percolate(attributes, at)
        val segmentCoupons = coupons.findByIds(matchedIds)
            .filter { it.distributionType == DistributionType.SEGMENT && it.isWithinValidPeriod(at) }
        val allCoupons = coupons.findAllDistributionWithinPeriod(at)

        return (segmentCoupons + allCoupons)
            .distinctBy { it.couponId }
            .map { EligibleCoupon(it.couponId, it.distributionType) }
    }
}
