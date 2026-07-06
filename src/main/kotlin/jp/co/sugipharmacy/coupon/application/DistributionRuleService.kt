package jp.co.sugipharmacy.coupon.application

import jp.co.sugipharmacy.coupon.application.port.PercolatorPort
import jp.co.sugipharmacy.coupon.domain.rule.ConditionParser
import jp.co.sugipharmacy.coupon.domain.rule.DistributionRule
import org.springframework.stereotype.Service
import java.time.Instant

data class RegisterDistributionRuleInput(
    val couponId: String,
    val condition: Any?,
    val validFrom: Instant,
    val validTo: Instant,
)

/**
 * 配布ルールを条件ASTへ正規化し、逆引きストアへ事前登録する。
 * クーポンマスタの存在は要求しない（登録順序に依存させない）—
 * マスタ未登録のルールは eligibility 側の突合で自然に落ちる。
 */
@Service
class DistributionRuleService(
    private val percolator: PercolatorPort,
) {
    fun register(input: RegisterDistributionRuleInput) {
        percolator.register(
            DistributionRule(
                couponId = input.couponId,
                condition = ConditionParser.parse(input.condition),
                validFrom = input.validFrom,
                validTo = input.validTo,
            ),
        )
    }
}
