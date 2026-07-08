package jp.co.sugipharmacy.coupon.application.event

import jp.co.sugipharmacy.coupon.domain.coupon.CouponStatus
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import java.time.Instant

/**
 * coupon-admin が発行するドメインイベント（fat event = event-carried state transfer）。
 * coupon-api は呼び戻せないため、投影に必要な情報を全て `data` に載せて受け取る。
 * エンベロープは CloudEvents 風（`eventId` / `type` / `version` / `occurredAt` / `traceId` / `data`）。
 */
enum class CouponEventType(val token: String) {
    COUPON_APPROVED("CouponApproved"),
    COUPON_UPDATED("CouponUpdated"),
    COUPON_SUSPENDED("CouponSuspended"),
    COUPON_DELETED("CouponDeleted"),
    ;

    companion object {
        fun fromToken(token: String): CouponEventType? = entries.firstOrNull { it.token == token }
        fun tokens(): String = entries.joinToString(", ") { it.token }
    }
}

/**
 * イベントペイロード。`CouponDeleted` は `couponId` のみ必須、それ以外は投影に必要な
 * 全項目（配信種別・有効期間・status・配布条件AST）を持つ。
 */
data class CouponEventData(
    val couponId: String,
    val schemaVersion: String? = null,
    /** 正準AST（coupon-rule-schema 準拠）。汎用デシリアライズした Map/List/スカラー。 */
    val condition: Any? = null,
    val distributionType: DistributionType? = null,
    val effectiveFrom: Instant? = null,
    val effectiveTo: Instant? = null,
    val status: CouponStatus? = null,
)

data class CouponEvent(
    val eventId: String,
    val type: CouponEventType,
    val version: Long,
    val occurredAt: Instant,
    val traceId: String? = null,
    val data: CouponEventData,
)
