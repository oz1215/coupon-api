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
 * 配布ルールを条件ASTへ正規化し、逆引きストアへ事前登録する（簡易形/リッチ形パーサ経由）。
 *
 * NOTE: 外部の登録APIは廃止済み（登録は coupon-admin、反映はイベント購読
 * ＝[jp.co.sugipharmacy.coupon.application.event.EventIngestionService] が正準ASTで投影する）。
 * 本サービスは現在どの口からも呼ばれていない。内部backfill/移行用に残すかは未確定
 * （変更ブリーフ §4）— 残す場合も投入形式は coupon-rule-schema（正準AST）へ寄せる想定。TODO。
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
