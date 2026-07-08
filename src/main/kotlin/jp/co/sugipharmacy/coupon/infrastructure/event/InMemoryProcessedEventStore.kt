package jp.co.sugipharmacy.coupon.infrastructure.event

import jp.co.sugipharmacy.coupon.application.event.port.ProcessedEventStore
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 冪等記録の in-memory 実装（MVP・プロセス内）。
 * TODO: 複数インスタンス／再起動をまたぐ冪等のため、永続ストアへ差し替える。
 */
@Component
class InMemoryProcessedEventStore : ProcessedEventStore {

    private val seenEventIds = ConcurrentHashMap.newKeySet<String>()
    private val lastVersionByCoupon = ConcurrentHashMap<String, Long>()

    override fun isProcessed(eventId: String): Boolean = seenEventIds.contains(eventId)

    override fun lastAppliedVersion(couponId: String): Long? = lastVersionByCoupon[couponId]

    override fun recordApplied(eventId: String, couponId: String, version: Long) {
        seenEventIds.add(eventId)
        lastVersionByCoupon.merge(couponId, version) { old, new -> maxOf(old, new) }
    }

    override fun clear() {
        seenEventIds.clear()
        lastVersionByCoupon.clear()
    }
}
