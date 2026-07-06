package jp.co.sugipharmacy.coupon.infrastructure.percolator

import jp.co.sugipharmacy.coupon.application.port.PercolatorPort
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import jp.co.sugipharmacy.coupon.domain.rule.DistributionRule
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 本番規模想定の OpenSearch percolator スケルトン（未実装・既定では配線されない）。
 * `coupon.percolator=opensearch` で有効化するが、実装完了までは呼び出し時に失敗する。
 *
 * 実装方針（条件AST → percolator クエリの写像）:
 * - register: DistributionRule を percolator インデックスへ1ドキュメントとして登録する。
 *   - Comparison(EQ)  -> term
 *   - Comparison(IN)  -> terms
 *   - Comparison(GTE/LTE/GT/LT) -> range
 *   - And -> bool.must / Or -> bool.should (minimum_should_match=1)
 *   - ルール有効期間 -> validFrom/validTo への range 句（percolate 文書が `at` を持つ）
 *   - couponId はドキュメントのフィールドとして保持し、ヒット結果から回収する。
 * - percolate: 属性1件＋`at` を文書として percolate クエリを1回発行し、
 *   ヒットした全ドキュメントの couponId 集合を返す。ルールのループ照合はしない。
 */
@Component
@ConditionalOnProperty(name = ["coupon.percolator"], havingValue = "opensearch")
class OpenSearchPercolatorAdapter : PercolatorPort {

    override fun register(rule: DistributionRule) {
        TODO("OpenSearch percolator への配布ルール登録（NCP-2892 本実装フェーズ）")
    }

    override fun percolate(attributes: MemberAttributes, at: Instant): Set<String> {
        TODO("OpenSearch percolate 検索による逆引き該当判定（NCP-2892 本実装フェーズ）")
    }
}
