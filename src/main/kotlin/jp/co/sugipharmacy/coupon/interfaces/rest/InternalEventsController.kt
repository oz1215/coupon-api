package jp.co.sugipharmacy.coupon.interfaces.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jp.co.sugipharmacy.coupon.application.event.EventIngestionService
import jp.co.sugipharmacy.coupon.application.event.ResyncService
import jp.co.sugipharmacy.coupon.interfaces.rest.dto.CouponEventEnvelopeRequest
import jp.co.sugipharmacy.coupon.interfaces.rest.dto.EventIngestResponse
import jp.co.sugipharmacy.coupon.interfaces.rest.dto.ResyncRequest
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * イベント駆動プロジェクションの取り込み口（トランスポートの継ぎ目）。
 * EventBridge → SQS ／ EventBridge API-destination が本エンドポイントを叩く想定。
 * SQS アダプタ（[jp.co.sugipharmacy.coupon.infrastructure.event] のスケルトン）も同じ
 * [EventIngestionService] を呼ぶ。**外部公開のルール登録APIではない**（登録は coupon-admin）。
 */
@RestController
@RequestMapping("/internal/events")
@Tag(
    name = "internal-events",
    description = "イベント駆動プロジェクションの取り込み口（coupon-admin → EventBridge → 本API）。外部ルール登録APIではない。",
)
class InternalEventsController(
    private val ingestion: EventIngestionService,
    private val resync: ResyncService,
) {
    @Operation(
        summary = "ドメインイベント受領（冪等取り込み）",
        description = "coupon-admin の fat event を受領し、percolator＋クーポンマスタ投影を更新する。" +
            "eventId 重複排除＋version の last-writer-wins。不正イベントは適用せず 422（DLQ 相当）。",
    )
    @ApiResponse(responseCode = "202", description = "受領（APPLIED / DUPLICATE / STALE）")
    @ApiResponse(
        responseCode = "422",
        description = "不正イベント（適用せず DLQ）",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @PostMapping
    fun receive(@Valid @RequestBody request: CouponEventEnvelopeRequest): ResponseEntity<EventIngestResponse> {
        val outcome = ingestion.ingest(request.toDomain())
        return ResponseEntity.accepted().body(EventIngestResponse(outcome.name))
    }

    @Operation(
        summary = "全件resync（スナップショット再適用）",
        description = "coupon-admin から再発行された現在状態のスナップショットで read model を作り直す。" +
            "トリガー・頻度（手動/定期/イベント起動）は未確定（変更ブリーフ §4）。",
    )
    @ApiResponse(responseCode = "200", description = "再構築完了")
    @PostMapping("/resync")
    fun resync(@Valid @RequestBody request: ResyncRequest): EventIngestResponse {
        resync.resync(request.events.map { it.toDomain() })
        return EventIngestResponse("RESYNCED")
    }
}
