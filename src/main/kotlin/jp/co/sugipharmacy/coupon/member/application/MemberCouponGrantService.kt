package jp.co.sugipharmacy.coupon.member.application

import jp.co.sugipharmacy.coupon.application.CouponService
import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import org.springframework.stereotype.Service

/**
 * 個別クーポン付与（ウェルカム/イベント登録）のユースケース。
 *
 * INDIVIDUAL クーポンは属性では決まらないため eligibility の対象外であり、
 * 付与だけが INDIVIDUAL を会員へ届ける経路になる。ウェルカムとイベントは
 * 付与の契機（入会/イベント）が違うだけで、記録される状態は同じ granted=true。
 *
 * クーポンマスタの参照は eligibility 側 CouponService の読み取り専用メソッド経由
 * （member → eligibility の一方向依存の範囲内）。書き込むのは member 自身のストアのみ。
 */
@Service
class MemberCouponGrantService(
    private val couponService: CouponService,
    private val stateService: MemberCouponStateService,
) {
    fun grant(memberId: String, couponId: String) {
        val coupon = couponService.findCoupon(couponId)
            ?: throw DomainValidationError("coupon \"$couponId\" does not exist in the coupon master")
        if (coupon.distributionType != DistributionType.INDIVIDUAL) {
            throw DomainValidationError(
                "coupon \"$couponId\" is ${coupon.distributionType}; only INDIVIDUAL coupons can be granted " +
                    "(SEGMENT/ALL are resolved by eligibility, not by grants)",
            )
        }
        stateService.setGranted(memberId, couponId)
    }
}
