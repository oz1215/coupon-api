package jp.co.sugipharmacy.coupon.domain.rule

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import java.time.Instant

/**
 * 配布ルール集約。「どの属性の会員に、どのクーポンを当てるか」を
 * 条件として事前保存しておくためのもの（逆引き判定の登録側）。
 */
data class DistributionRule(
    val couponId: String,
    val condition: Condition,
    val validFrom: Instant,
    val validTo: Instant,
) {
    init {
        if (couponId.isBlank()) {
            throw DomainValidationError("couponId must not be empty")
        }
        if (validTo.isBefore(validFrom)) {
            throw DomainValidationError("distribution rule for \"$couponId\": validTo must not precede validFrom")
        }
    }

    /** ルール自体の有効期間内、かつ条件が属性に該当するか。期間は両端を含む。 */
    fun matches(attributes: MemberAttributes, at: Instant): Boolean =
        isWithinValidPeriod(at) && condition.evaluate(attributes)

    fun isWithinValidPeriod(at: Instant): Boolean =
        !at.isBefore(validFrom) && !at.isAfter(validTo)
}
