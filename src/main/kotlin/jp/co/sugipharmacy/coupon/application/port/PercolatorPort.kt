package jp.co.sugipharmacy.coupon.application.port

import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import jp.co.sugipharmacy.coupon.domain.rule.DistributionRule
import java.time.Instant

/**
 * 逆引き該当判定の口。配布ルール（条件）を事前登録し、1会員分の属性を
 * 投げると当たるクーポンID集合が返る — 通常検索と逆向き（percolator）。
 * ルールをループして外部システムへ照合しに行く実装は認めない。
 *
 * 型は一つ、実行は複数: InMemory（既定・インフラ不要）と OpenSearch
 * percolator（本番想定・スケルトン）を差し替え可能にする。
 */
interface PercolatorPort {
    fun register(rule: DistributionRule)

    /** 属性に該当し、かつルール有効期間内（at 時点）のクーポンIDを返す。 */
    fun percolate(attributes: MemberAttributes, at: Instant): Set<String>
}
