package jp.co.sugipharmacy.coupon.member.application

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.member.application.port.MemberCouponStateRepository
import org.springframework.stereotype.Service

/**
 * ID統合（移行 transfer / 統合 migrate）と退会削除のユースケース。
 * 会員キー状態の付け替え・削除であり、member モジュール自身のストアだけを読み書きする。
 *
 * - transfer / migrate は MVP では同じ操作に正規化する: from の全状態を to へ移し、
 *   同一 couponId が衝突したら各フラグの OR でマージ（MemberCouponState.mergedWith）、
 *   最後に from を空にする。エンドポイントは業務上の意図（旧ID→新IDの移行 / 複数IDの統合）を
 *   区別するため2つ残す。レガシーの会員種別パターン分岐は MVP 対象外。
 * - withdraw は会員の全状態を削除する。
 */
@Service
class MemberCouponIdIntegrationService(
    private val states: MemberCouponStateRepository,
) {
    /** 顧客別クーポン移行（旧IDから新IDへ）。 */
    fun transfer(fromMemberId: String, toMemberId: String) = mergeInto(fromMemberId, toMemberId)

    /** 顧客別クーポン統合（複数IDの状態を1つへ）。 */
    fun migrate(fromMemberId: String, toMemberId: String) = mergeInto(fromMemberId, toMemberId)

    /** 退会に伴う会員キー状態の全削除。 */
    fun withdraw(memberId: String) {
        states.deleteByMember(memberId)
    }

    private fun mergeInto(fromMemberId: String, toMemberId: String) {
        if (fromMemberId.isBlank() || toMemberId.isBlank()) {
            throw DomainValidationError("fromMemberId and toMemberId must not be empty")
        }
        if (fromMemberId == toMemberId) {
            throw DomainValidationError("fromMemberId and toMemberId must differ")
        }
        states.findByMember(fromMemberId).forEach { fromState ->
            val moved = fromState.copy(memberId = toMemberId)
            val existing = states.find(toMemberId, fromState.couponId)
            states.save(existing?.mergedWith(moved) ?: moved)
        }
        states.deleteByMember(fromMemberId)
    }
}
