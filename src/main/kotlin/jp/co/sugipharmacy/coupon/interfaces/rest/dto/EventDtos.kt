package jp.co.sugipharmacy.coupon.interfaces.rest.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jp.co.sugipharmacy.coupon.application.event.CouponEvent
import jp.co.sugipharmacy.coupon.application.event.CouponEventData
import jp.co.sugipharmacy.coupon.application.event.CouponEventType
import jp.co.sugipharmacy.coupon.application.event.EventRejectedException
import jp.co.sugipharmacy.coupon.domain.coupon.CouponStatus
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import java.time.Instant

/**
 * coupon-admin のドメインイベント（CloudEvents 風エンベロープ）の受領形。
 * これはトランスポートの継ぎ目であり、外部公開のルール登録APIではない。
 */
@Schema(description = "coupon-admin ドメインイベント（fat event / CloudEvents 風）")
data class CouponEventEnvelopeRequest(
    @field:Schema(description = "イベントID（冪等の重複排除キー）", example = "evt-01H...")
    @field:NotBlank val eventId: String,
    @field:Schema(description = "イベント型", example = "CouponApproved")
    @field:NotBlank val type: String,
    @field:Schema(description = "クーポンの版（last-writer-wins）", example = "7")
    @field:NotNull val version: Long,
    @field:Schema(description = "発生時刻（RFC 3339）", example = "2026-07-09T09:00:00Z")
    @field:NotNull val occurredAt: Instant,
    @field:Schema(description = "W3C Trace Context の相関ID", example = "0af7651916cd43dd8448eb211c80319c")
    val traceId: String? = null,
    @field:NotNull @field:Valid val data: CouponEventDataRequest,
) {
    fun toDomain(): CouponEvent {
        val eventType = CouponEventType.fromToken(type)
            ?: throw EventRejectedException(
                "EVENT_UNKNOWN_TYPE",
                "unknown event type \"$type\" (expected: ${CouponEventType.tokens()})",
                "/type",
            )
        return CouponEvent(
            eventId = eventId,
            type = eventType,
            version = version,
            occurredAt = occurredAt,
            traceId = traceId,
            data = data.toDomain(),
        )
    }
}

@Schema(description = "イベントペイロード。CouponDeleted は couponId のみ、それ以外は投影に必要な全項目。")
data class CouponEventDataRequest(
    @field:NotBlank val couponId: String,
    @field:Schema(description = "条件スキーマ版（coupon-rule-schema）", example = "1.0")
    val schemaVersion: String? = null,
    @field:Schema(description = "配布条件（正準AST・coupon-rule-schema 準拠）。SEGMENT のとき必須相当。")
    val condition: Any? = null,
    val distributionType: DistributionType? = null,
    @field:Schema(description = "有効期間の開始（含む・RFC 3339）", example = "2026-07-01T00:00:00+09:00")
    val effectiveFrom: Instant? = null,
    @field:Schema(description = "有効期間の終了（含む・RFC 3339）", example = "2026-07-31T23:59:59+09:00")
    val effectiveTo: Instant? = null,
    val status: CouponStatus? = null,
) {
    fun toDomain(): CouponEventData = CouponEventData(
        couponId = couponId,
        schemaVersion = schemaVersion,
        condition = condition,
        distributionType = distributionType,
        effectiveFrom = effectiveFrom,
        effectiveTo = effectiveTo,
        status = status,
    )
}

@Schema(description = "全件resync リクエスト（現在状態を表すイベントのスナップショット）")
data class ResyncRequest(
    @field:NotNull @field:Valid val events: List<CouponEventEnvelopeRequest>,
)

@Schema(description = "取り込み結果（APPLIED / DUPLICATE / STALE / RESYNCED）")
data class EventIngestResponse(val outcome: String)
