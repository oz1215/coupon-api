package jp.co.sugipharmacy.coupon.member.application

import jp.co.sugipharmacy.coupon.application.CouponService
import jp.co.sugipharmacy.coupon.application.EligibilityService
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import org.springframework.stereotype.Service
import java.time.Instant

data class DisplayCoupon(
    val couponId: String,
    val distributionType: DistributionType,
)

/**
 * 会員向け表示一覧の合成（composition）ユースケース。
 *
 * 表示一覧 = eligibility の生の結果 − 会員の消費済み − 緊急停止中（SUSPENDED）。
 *
 * 差し引きはこの合成層でのみ行う。EligibilityService / POST /coupons/eligibility は
 * member-agnostic な生集合（該当SEGMENT ∪ ALL）を返し続け、差し引きを持ち込まない。
 * 依存方向は member → eligibility の一方向のみ（逆依存は禁止・ArchitectureTest が強制）。
 *
 * 会員属性は BFF がプロファイルストア（外部）から取得してリクエストで渡す —
 * 本サービスは属性を保持しない（member-agnostic の維持）。
 */
@Service
class MemberCouponListService(
    private val eligibilityService: EligibilityService,
    private val couponService: CouponService,
    private val stateService: MemberCouponStateService,
) {
    fun getDisplayCoupons(memberId: String, rawAttributes: Map<String, Any?>, at: Instant): List<DisplayCoupon> {
        val eligible = eligibilityService.findEligibleCoupons(rawAttributes, at)
        val used = stateService.getUsedCouponIds(memberId)
        val suspended = couponService.getSuspendedCouponIds().toSet()
        return eligible
            .filterNot { it.couponId in used || it.couponId in suspended }
            .map { DisplayCoupon(it.couponId, it.distributionType) }
    }
}
