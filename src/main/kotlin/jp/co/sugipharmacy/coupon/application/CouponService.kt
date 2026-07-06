package jp.co.sugipharmacy.coupon.application

import jp.co.sugipharmacy.coupon.application.port.CouponRepository
import jp.co.sugipharmacy.coupon.domain.coupon.Coupon
import jp.co.sugipharmacy.coupon.domain.coupon.CouponStatus
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import org.springframework.stereotype.Service
import java.time.Instant

data class RegisterCouponInput(
    val couponId: String,
    val distributionType: DistributionType,
    val validFrom: Instant,
    val validTo: Instant,
    val status: CouponStatus,
)

@Service
class CouponService(
    private val coupons: CouponRepository,
) {
    /** クーポンマスタ登録（upsert）。同一IDの再登録で status 更新＝緊急停止も表現できる。 */
    fun register(input: RegisterCouponInput) {
        coupons.save(
            Coupon(
                couponId = input.couponId,
                distributionType = input.distributionType,
                validFrom = input.validFrom,
                validTo = input.validTo,
                status = input.status,
            ),
        )
    }

    /** 緊急停止中クーポンIDの小さな一覧。BFF が表示直前に差し引く。 */
    fun getSuspendedCouponIds(): List<String> =
        coupons.findSuspended().map { it.couponId }
}
