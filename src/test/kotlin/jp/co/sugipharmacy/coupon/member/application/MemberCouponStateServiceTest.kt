package jp.co.sugipharmacy.coupon.member.application

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.member.infrastructure.InMemoryMemberCouponStateRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberCouponStateServiceTest {

    private val service = MemberCouponStateService(InMemoryMemberCouponStateRepository())

    @Test
    fun `選択の設定と解除ができる`() {
        service.setSelection("M-1", "CP-1", true)
        service.setSelection("M-1", "CP-2", true)
        assertEquals(listOf("CP-1", "CP-2"), service.getSelectedCouponIds("M-1"))

        service.setSelection("M-1", "CP-1", false)
        assertEquals(listOf("CP-2"), service.getSelectedCouponIds("M-1"))
    }

    @Test
    fun `お気に入りの設定と解除ができる`() {
        service.setFavorite("M-1", "CP-1", true)
        assertEquals(listOf("CP-1"), service.getFavoriteCouponIds("M-1"))

        service.setFavorite("M-1", "CP-1", false)
        assertTrue(service.getFavoriteCouponIds("M-1").isEmpty())
    }

    @Test
    fun `複数クーポンを一括で消費済みにできる`() {
        service.markUsed("M-1", listOf("CP-1", "CP-2"))
        assertEquals(setOf("CP-1", "CP-2"), service.getUsedCouponIds("M-1"))
    }

    @Test
    fun `フラグは独立している - 消費済みにしても選択・お気に入りは保持される`() {
        service.setSelection("M-1", "CP-1", true)
        service.setFavorite("M-1", "CP-1", true)
        service.markUsed("M-1", listOf("CP-1"))

        assertEquals(listOf("CP-1"), service.getSelectedCouponIds("M-1"))
        assertEquals(listOf("CP-1"), service.getFavoriteCouponIds("M-1"))
        assertEquals(setOf("CP-1"), service.getUsedCouponIds("M-1"))
    }

    @Test
    fun `会員ごとに状態が分離されている`() {
        service.setSelection("M-1", "CP-1", true)
        service.setFavorite("M-1", "CP-2", true)
        service.markUsed("M-1", listOf("CP-3"))

        assertTrue(service.getSelectedCouponIds("M-2").isEmpty())
        assertTrue(service.getFavoriteCouponIds("M-2").isEmpty())
        assertTrue(service.getUsedCouponIds("M-2").isEmpty())

        service.markUsed("M-2", listOf("CP-1"))
        assertEquals(setOf("CP-3"), service.getUsedCouponIds("M-1"))
        assertEquals(setOf("CP-1"), service.getUsedCouponIds("M-2"))
    }

    @Test
    fun `空のmemberIdやcouponIdはドメイン検証で弾かれる`() {
        assertThrows<DomainValidationError> { service.setSelection("", "CP-1", true) }
        assertThrows<DomainValidationError> { service.markUsed("M-1", listOf("")) }
    }
}
