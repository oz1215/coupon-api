package jp.co.sugipharmacy.coupon.application.event

import jp.co.sugipharmacy.coupon.application.CouponService
import jp.co.sugipharmacy.coupon.application.RuleValidationProperties
import jp.co.sugipharmacy.coupon.domain.coupon.CouponStatus
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import jp.co.sugipharmacy.coupon.infrastructure.attribute.AllowAllAttributeCatalog
import jp.co.sugipharmacy.coupon.infrastructure.coupon.InMemoryCouponRepository
import jp.co.sugipharmacy.coupon.infrastructure.event.InMemoryProcessedEventStore
import jp.co.sugipharmacy.coupon.infrastructure.percolator.InMemoryPercolatorAdapter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * イベント駆動プロジェクション: 検証・冪等（eventId 重複排除／version LWW）・投影を確認する。
 */
class EventIngestionServiceTest {

    private val coupons = InMemoryCouponRepository()
    private val couponService = CouponService(coupons)
    private val percolator = InMemoryPercolatorAdapter()
    private val processed = InMemoryProcessedEventStore()
    private val service = EventIngestionService(
        couponService, percolator, AllowAllAttributeCatalog(), RuleValidationProperties(), processed,
    )

    private val from = Instant.parse("2026-07-01T00:00:00Z")
    private val to = Instant.parse("2026-07-31T23:59:59Z")
    private val at = Instant.parse("2026-07-15T00:00:00Z")

    private fun age20sCondition() = mapOf(
        "op" to "and",
        "nodes" to listOf(
            mapOf("op" to "gte", "attr" to "age", "value" to 20),
            mapOf("op" to "lte", "attr" to "age", "value" to 29),
        ),
    )

    private fun approved(
        couponId: String,
        eventId: String,
        version: Long,
        type: CouponEventType = CouponEventType.COUPON_APPROVED,
        status: CouponStatus = CouponStatus.ACTIVE,
        condition: Any? = age20sCondition(),
        distributionType: DistributionType = DistributionType.SEGMENT,
    ) = CouponEvent(
        eventId = eventId, type = type, version = version, occurredAt = at,
        data = CouponEventData(couponId, "1.0", condition, distributionType, from, to, status),
    )

    private fun percolates(couponId: String, age: Int): Boolean =
        percolator.percolate(MemberAttributes.from(mapOf("age" to age)), at).contains(couponId)

    @Test
    fun `CouponApproved はマスタ投影とルールを作る`() {
        val outcome = service.ingest(approved("SEG-1", "e1", 1))
        assertThat(outcome).isEqualTo(IngestOutcome.APPLIED)
        assertThat(coupons.findByIds(listOf("SEG-1"))).hasSize(1)
        assertThat(percolates("SEG-1", 25)).isTrue()
        assertThat(percolates("SEG-1", 35)).isFalse()
    }

    @Test
    fun `CouponSuspended は停止一覧に載る`() {
        service.ingest(approved("SEG-1", "e1", 1))
        service.ingest(approved("SEG-1", "e2", 2, type = CouponEventType.COUPON_SUSPENDED, status = CouponStatus.SUSPENDED))
        assertThat(couponService.getSuspendedCouponIds()).containsExactly("SEG-1")
    }

    @Test
    fun `同一 eventId の再配信は冪等（DUPLICATE・再適用しない）`() {
        assertThat(service.ingest(approved("SEG-1", "e1", 1))).isEqualTo(IngestOutcome.APPLIED)
        assertThat(service.ingest(approved("SEG-1", "e1", 1))).isEqualTo(IngestOutcome.DUPLICATE)
    }

    @Test
    fun `古い version は無視（STALE）、新しい version は適用しルールを置換`() {
        service.ingest(approved("SEG-1", "e1", 5))
        // 版4は古い → STALE
        assertThat(service.ingest(approved("SEG-1", "e0", 4))).isEqualTo(IngestOutcome.STALE)
        // 版6は新しい → 適用、条件を 40代 に置換
        val newer = approved(
            "SEG-1", "e2", 6,
            condition = mapOf("op" to "and", "nodes" to listOf(
                mapOf("op" to "gte", "attr" to "age", "value" to 40),
                mapOf("op" to "lte", "attr" to "age", "value" to 49),
            )),
        )
        assertThat(service.ingest(newer)).isEqualTo(IngestOutcome.APPLIED)
        assertThat(percolates("SEG-1", 25)).isFalse() // 旧ルールは残っていない
        assertThat(percolates("SEG-1", 45)).isTrue()
    }

    @Test
    fun `CouponDeleted はマスタとルールを削除する`() {
        service.ingest(approved("SEG-1", "e1", 1))
        service.ingest(
            CouponEvent("e2", CouponEventType.COUPON_DELETED, 2, at, data = CouponEventData(couponId = "SEG-1")),
        )
        assertThat(coupons.findByIds(listOf("SEG-1"))).isEmpty()
        assertThat(percolates("SEG-1", 25)).isFalse()
    }

    @Test
    fun `不正な条件は拒否し、何も適用しない（DLQ 相当）`() {
        val bad = approved("SEG-BAD", "e1", 1, condition = mapOf("op" to "between", "attr" to "age", "value" to 20))
        assertThatThrownBy { service.ingest(bad) }
            .isInstanceOf(EventRejectedException::class.java)
        assertThat(coupons.findByIds(listOf("SEG-BAD"))).isEmpty()
        assertThat(percolates("SEG-BAD", 25)).isFalse()
    }

    @Test
    fun `必須項目欠落（distributionType なし）は拒否`() {
        val incomplete = CouponEvent(
            "e1", CouponEventType.COUPON_APPROVED, 1, at,
            data = CouponEventData(couponId = "SEG-1", effectiveFrom = from, effectiveTo = to),
        )
        assertThatThrownBy { service.ingest(incomplete) }
            .isInstanceOf(EventRejectedException::class.java)
    }
}
