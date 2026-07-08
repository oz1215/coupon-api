package jp.co.sugipharmacy.coupon.infrastructure.event

import jp.co.sugipharmacy.coupon.application.event.EventIngestionService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * イベント取り込みトランスポートのスケルトン（`coupon.events.transport=sqs` のときのみ配線）。
 *
 * 想定: coupon-admin → Amazon EventBridge（カスタムバス）→ SQS →（このリスナ）。
 * 受信メッセージ（CloudEvents 風エンベロープ）を CouponEvent へマップし
 * [EventIngestionService.ingest] を呼ぶ。EventRejectedException は nack→DLQ、
 * APPLIED/DUPLICATE/STALE は ack。冪等・順序は ProcessedEventStore が担保する。
 *
 * TODO: 実配線（spring-cloud-aws SqsListener 等）・DLQ・アラートは未実装（変更ブリーフ §4）。
 * 既定（coupon.events.transport=none）では本Beanは生成されない。取り込みは `/internal/events`
 * でも駆動できる（EventBridge API-destination 経路・ローカル検証・テスト）。
 */
@Component
@ConditionalOnProperty(name = ["coupon.events.transport"], havingValue = "sqs")
class SqsCouponEventListener(
    @Suppress("unused") private val ingestion: EventIngestionService,
) {
    fun onMessage(payload: String): Unit =
        TODO("SQS/EventBridge 実配線は未実装（変更ブリーフ §4）: payload=$payload")
}
