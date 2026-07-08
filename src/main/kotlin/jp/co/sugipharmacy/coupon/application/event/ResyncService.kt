package jp.co.sugipharmacy.coupon.application.event

import jp.co.sugipharmacy.coupon.application.event.port.ProcessedEventStore
import jp.co.sugipharmacy.coupon.application.port.CouponRepository
import jp.co.sugipharmacy.coupon.application.port.PercolatorPort
import org.springframework.stereotype.Service

/**
 * 全件resync: coupon-admin から再発行されたスナップショット（現在状態を表す fat event 群）で
 * read model（クーポンマスタ投影＋percolator）を作り直す。ログのリプレイに依存しない。
 *
 * TODO: 起動トリガー・頻度（手動 / 定期 / イベント起動）は未確定（変更ブリーフ §4）。
 * 本サービスは「適用（再構築）」能力のみ提供し、スケジュールは張らない。
 */
@Service
class ResyncService(
    private val coupons: CouponRepository,
    private val percolator: PercolatorPort,
    private val processed: ProcessedEventStore,
    private val ingestion: EventIngestionService,
) {
    fun resync(snapshot: List<CouponEvent>) {
        coupons.deleteAll()
        percolator.clear()
        processed.clear()
        // version 昇順に適用 → couponId ごとに最新版が最後に残る。
        snapshot.sortedWith(compareBy({ it.data.couponId }, { it.version }))
            .forEach { ingestion.ingest(it) }
    }
}
