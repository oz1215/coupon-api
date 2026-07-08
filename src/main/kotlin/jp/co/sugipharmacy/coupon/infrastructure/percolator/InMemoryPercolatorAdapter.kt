package jp.co.sugipharmacy.coupon.infrastructure.percolator

import jp.co.sugipharmacy.coupon.application.port.PercolatorPort
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import jp.co.sugipharmacy.coupon.domain.rule.DistributionRule
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 既定の逆引き実装。外部インフラゼロで動く（開発・テスト・MVP検証用）。
 * プロセス内の線形走査だが、外部システムへの問い合わせは一切行わないため
 * PercolatorPort の契約（ループ照合の禁止＝外部照合の禁止）は満たす。
 * 本番規模では OpenSearchPercolatorAdapter（coupon.percolator=opensearch）に差し替える。
 */
@Component
@ConditionalOnProperty(name = ["coupon.percolator"], havingValue = "in-memory", matchIfMissing = true)
class InMemoryPercolatorAdapter : PercolatorPort {

    private val rules = CopyOnWriteArrayList<DistributionRule>()

    override fun register(rule: DistributionRule) {
        rules.add(rule)
    }

    override fun percolate(attributes: MemberAttributes, at: Instant): Set<String> =
        rules.asSequence()
            .filter { it.matches(attributes, at) }
            .map { it.couponId }
            .toSet()

    override fun removeByCoupon(couponId: String) {
        rules.removeAll { it.couponId == couponId }
    }

    override fun clear() {
        rules.clear()
    }
}
