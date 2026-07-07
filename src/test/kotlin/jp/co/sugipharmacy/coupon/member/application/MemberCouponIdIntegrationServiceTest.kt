package jp.co.sugipharmacy.coupon.member.application

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.member.domain.MemberCouponState
import jp.co.sugipharmacy.coupon.member.infrastructure.InMemoryMemberCouponStateRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ID統合（移行・統合・退会削除）のユニットテスト。
 * マージ規則: 同一 couponId の衝突は各フラグ（used/granted/selected/favorite）の OR。
 */
class MemberCouponIdIntegrationServiceTest {

    private val states = InMemoryMemberCouponStateRepository()
    private val service = MemberCouponIdIntegrationService(states)

    @Test
    fun `transfer - 全状態が移り、移行元は空になる`() {
        states.save(MemberCouponState("M-OLD", "CP-1", selected = true, used = true))
        states.save(MemberCouponState("M-OLD", "CP-2", granted = true))

        service.transfer("M-OLD", "M-NEW")

        assertTrue(states.findByMember("M-OLD").isEmpty())
        assertEquals(
            MemberCouponState("M-NEW", "CP-1", selected = true, used = true),
            states.find("M-NEW", "CP-1"),
        )
        assertEquals(
            MemberCouponState("M-NEW", "CP-2", granted = true),
            states.find("M-NEW", "CP-2"),
        )
    }

    @Test
    fun `transfer - 移行先に同一couponIdがあればフラグのORでマージされる`() {
        states.save(MemberCouponState("M-OLD", "CP-1", used = true, favorite = true))
        states.save(MemberCouponState("M-NEW", "CP-1", selected = true, granted = true))

        service.transfer("M-OLD", "M-NEW")

        assertEquals(
            MemberCouponState("M-NEW", "CP-1", selected = true, favorite = true, used = true, granted = true),
            states.find("M-NEW", "CP-1"),
        )
        assertTrue(states.findByMember("M-OLD").isEmpty())
    }

    @Test
    fun `migrate - 状態が和集合として統合され、統合元は空になる`() {
        states.save(MemberCouponState("M-A", "CP-1", granted = true))
        states.save(MemberCouponState("M-A", "CP-2", used = true))
        states.save(MemberCouponState("M-B", "CP-2", favorite = true))
        states.save(MemberCouponState("M-B", "CP-3", selected = true))

        service.migrate("M-A", "M-B")

        assertTrue(states.findByMember("M-A").isEmpty())
        val merged = states.findByMember("M-B").associateBy { it.couponId }
        assertEquals(3, merged.size)
        assertEquals(MemberCouponState("M-B", "CP-1", granted = true), merged["CP-1"])
        // 衝突は OR: used(M-A側) と favorite(M-B側) の両方が立つ
        assertEquals(MemberCouponState("M-B", "CP-2", favorite = true, used = true), merged["CP-2"])
        assertEquals(MemberCouponState("M-B", "CP-3", selected = true), merged["CP-3"])
    }

    @Test
    fun `マージ規則 - 一度でも立ったフラグは統合後も落ちない`() {
        val a = MemberCouponState("M-B", "CP-1", selected = false, favorite = true, used = false, granted = true)
        val b = MemberCouponState("M-A", "CP-1", selected = true, favorite = false, used = true, granted = false)
        assertEquals(
            MemberCouponState("M-B", "CP-1", selected = true, favorite = true, used = true, granted = true),
            a.mergedWith(b),
        )
        // couponId が違う状態はマージできない
        assertThrows<DomainValidationError> {
            a.mergedWith(MemberCouponState("M-A", "CP-2", used = true))
        }
    }

    @Test
    fun `withdraw - 会員の全状態が削除され、他会員には影響しない`() {
        states.save(MemberCouponState("M-1", "CP-1", used = true))
        states.save(MemberCouponState("M-1", "CP-2", granted = true))
        states.save(MemberCouponState("M-2", "CP-1", selected = true))

        service.withdraw("M-1")

        assertTrue(states.findByMember("M-1").isEmpty())
        assertEquals(1, states.findByMember("M-2").size)
    }

    @Test
    fun `withdraw は冪等 - 状態が無くても失敗しない`() {
        service.withdraw("M-NONE")
        assertTrue(states.findByMember("M-NONE").isEmpty())
    }

    @Test
    fun `同一ID・空IDのtransfer,migrateは拒否される`() {
        assertThrows<DomainValidationError> { service.transfer("M-1", "M-1") }
        assertThrows<DomainValidationError> { service.migrate("M-1", "M-1") }
        assertThrows<DomainValidationError> { service.transfer("", "M-1") }
        assertThrows<DomainValidationError> { service.migrate("M-1", " ") }
    }
}
