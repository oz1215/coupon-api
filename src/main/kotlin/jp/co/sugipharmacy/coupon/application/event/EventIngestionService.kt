package jp.co.sugipharmacy.coupon.application.event

import jp.co.sugipharmacy.coupon.application.CouponService
import jp.co.sugipharmacy.coupon.application.RegisterCouponInput
import jp.co.sugipharmacy.coupon.application.RuleValidationProperties
import jp.co.sugipharmacy.coupon.application.event.port.ProcessedEventStore
import jp.co.sugipharmacy.coupon.application.port.AttributeCatalog
import jp.co.sugipharmacy.coupon.application.port.PercolatorPort
import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.domain.coupon.CouponStatus
import jp.co.sugipharmacy.coupon.domain.rule.CanonicalConditionParser
import jp.co.sugipharmacy.coupon.domain.rule.DistributionRule
import org.springframework.stereotype.Service

/** 取り込み結果。適用したか、冪等でスキップしたか。 */
enum class IngestOutcome { APPLIED, DUPLICATE, STALE }

/**
 * 検証に失敗し適用できないイベント。トランスポート層は DLQ へ倒し、
 * `/internal/events` は problem+json（422）へ写像する。`pointer` は不正箇所（あれば）。
 */
class EventRejectedException(
    val code: String,
    message: String,
    val pointer: String? = null,
) : RuntimeException(message)

/**
 * coupon-admin のドメインイベントを購読して、自分の read model（クーポンマスタ投影＋
 * percolator）を構築・更新する（イベント駆動プロジェクション）。coupon-admin は呼ばない。
 *
 * - **検証優先**: 構造・必須項目・条件ASTを先に検証する。不正は [EventRejectedException]
 *   （＝DLQ 行き）とし、冪等記録も残さない（修正後の再配信を受けられるように）。
 * - **冪等**: `eventId` 重複排除。`version` の last-writer-wins（今より新しい版のみ適用）。
 * - **投影**: Approved/Updated/Suspended は upsert（配信種別・有効期間・status・条件）。
 *   Deleted はマスタ投影とルールを削除。更新は既存ルールを置換する。
 */
@Service
class EventIngestionService(
    private val coupons: CouponService,
    private val percolator: PercolatorPort,
    private val attributeCatalog: AttributeCatalog,
    private val ruleProperties: RuleValidationProperties,
    private val processed: ProcessedEventStore,
) {
    fun ingest(event: CouponEvent): IngestOutcome {
        val projection = validateAndBuild(event) // 不正なら EventRejectedException（＝DLQ）

        if (processed.isProcessed(event.eventId)) return IngestOutcome.DUPLICATE
        val last = processed.lastAppliedVersion(event.data.couponId)
        if (last != null && event.version <= last) return IngestOutcome.STALE

        apply(projection)
        processed.recordApplied(event.eventId, event.data.couponId, event.version)
        return IngestOutcome.APPLIED
    }

    private fun apply(projection: Projection) {
        when (projection) {
            is Projection.Delete -> {
                coupons.remove(projection.couponId)
                percolator.removeByCoupon(projection.couponId)
            }
            is Projection.Upsert -> {
                coupons.register(projection.coupon)
                // 更新は既存ルールを置換する（古い条件を残さない）。
                percolator.removeByCoupon(projection.coupon.couponId)
                projection.rule?.let(percolator::register)
            }
        }
    }

    private fun validateAndBuild(event: CouponEvent): Projection {
        val d = event.data
        if (event.type == CouponEventType.COUPON_DELETED) {
            return Projection.Delete(d.couponId)
        }
        val distributionType = d.distributionType
            ?: reject("EVENT_INCOMPLETE", "data.distributionType is required", "/data/distributionType")
        val from = d.effectiveFrom
            ?: reject("EVENT_INCOMPLETE", "data.effectiveFrom is required", "/data/effectiveFrom")
        val to = d.effectiveTo
            ?: reject("EVENT_INCOMPLETE", "data.effectiveTo is required", "/data/effectiveTo")
        if (to.isBefore(from)) {
            reject("EVENT_INVALID_PERIOD", "effectiveTo must not precede effectiveFrom", "/data/effectiveTo")
        }
        val status = d.status
            ?: if (event.type == CouponEventType.COUPON_SUSPENDED) CouponStatus.SUSPENDED else CouponStatus.ACTIVE

        val rule = d.condition?.let { raw ->
            val condition = try {
                CanonicalConditionParser.parse(raw, ruleProperties.toLimits(), attributeCatalog::isKnown)
            } catch (e: DomainValidationError) {
                reject("RULE_INVALID", e.message ?: "invalid distribution condition", e.pointer)
            }
            try {
                DistributionRule(d.couponId, condition, from, to)
            } catch (e: DomainValidationError) {
                reject("RULE_INVALID", e.message ?: "invalid distribution rule", e.pointer)
            }
        }
        return Projection.Upsert(
            coupon = RegisterCouponInput(d.couponId, distributionType, from, to, status),
            rule = rule,
        )
    }

    private fun reject(code: String, message: String, pointer: String? = null): Nothing =
        throw EventRejectedException(code, message, pointer)

    private sealed interface Projection {
        data class Delete(val couponId: String) : Projection
        data class Upsert(val coupon: RegisterCouponInput, val rule: DistributionRule?) : Projection
    }
}
