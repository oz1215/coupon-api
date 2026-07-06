package jp.co.sugipharmacy.coupon.domain.coupon

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import java.time.Instant

enum class DistributionType { ALL, SEGMENT, INDIVIDUAL }

enum class CouponStatus { ACTIVE, SUSPENDED }

/**
 * クーポンマスタの、該当判定に必要な最小投影。
 * 会員との事前紐づけは持たない（member-agnostic）。
 */
data class Coupon(
    val couponId: String,
    val distributionType: DistributionType,
    val validFrom: Instant,
    val validTo: Instant,
    val status: CouponStatus,
) {
    init {
        if (couponId.isBlank()) {
            throw DomainValidationError("couponId must not be empty")
        }
        if (validTo.isBefore(validFrom)) {
            throw DomainValidationError("coupon \"$couponId\": validTo must not precede validFrom")
        }
    }

    /** 有効期間は両端を含む。 */
    fun isWithinValidPeriod(at: Instant): Boolean =
        !at.isBefore(validFrom) && !at.isAfter(validTo)

    val isSuspended: Boolean
        get() = status == CouponStatus.SUSPENDED
}
