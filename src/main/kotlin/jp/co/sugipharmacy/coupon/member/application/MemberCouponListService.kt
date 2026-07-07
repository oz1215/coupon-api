package jp.co.sugipharmacy.coupon.member.application

import jp.co.sugipharmacy.coupon.application.CouponService
import jp.co.sugipharmacy.coupon.application.EligibilityService
import jp.co.sugipharmacy.coupon.domain.coupon.CouponStatus
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
 * 表示一覧 = (eligibility の生の結果: 該当SEGMENT ∪ ALL)
 *          ∪ (この会員に付与済みの INDIVIDUAL のうち有効期間内かつ ACTIVE)
 *          − 会員の消費済み − 緊急停止中（SUSPENDED）。
 *
 * モジュールをまたぐ和・差はこの合成層でのみ行う（ガードレール#3）。
 * EligibilityService / POST /coupons/eligibility は member-agnostic な生集合
 * （該当SEGMENT ∪ ALL）を返し続け、付与済み INDIVIDUAL の和も差し引きも持ち込まない。
 * 依存方向は member → eligibility の一方向のみ（逆依存は禁止・ArchitectureTest が強制）。
 *
 * 付与済み INDIVIDUAL の有効期間はクーポンマスタの validFrom/validTo で判定する
 * （レガシーの会員別利用開始/終了日は MVP 対象外）。
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
            .map { DisplayCoupon(it.couponId, it.distributionType) }
        val grantedIndividual = couponService.findCoupons(stateService.getGrantedCouponIds(memberId))
            .filter {
                it.distributionType == DistributionType.INDIVIDUAL &&
                    it.isWithinValidPeriod(at) &&
                    it.status == CouponStatus.ACTIVE
            }
            .map { DisplayCoupon(it.couponId, it.distributionType) }
        val used = stateService.getUsedCouponIds(memberId)
        val suspended = couponService.getSuspendedCouponIds().toSet()
        return (eligible + grantedIndividual)
            .distinctBy { it.couponId }
            .filterNot { it.couponId in used || it.couponId in suspended }
    }
}
