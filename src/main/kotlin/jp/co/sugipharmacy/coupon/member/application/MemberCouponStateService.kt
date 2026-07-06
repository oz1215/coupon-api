package jp.co.sugipharmacy.coupon.member.application

import jp.co.sugipharmacy.coupon.member.application.port.MemberCouponStateRepository
import jp.co.sugipharmacy.coupon.member.domain.MemberCouponState
import org.springframework.stereotype.Service

/**
 * 会員×クーポン状態の更新・参照ユースケース。
 * 選択・お気に入り・消費済みのフラグを (memberId, couponId) キーで upsert する。
 * 該当判定（eligibility）には一切関与しない — 判定は属性から毎回逆引きし、
 * ここは会員の操作結果だけを持つ。
 */
@Service
class MemberCouponStateService(
    private val states: MemberCouponStateRepository,
) {
    fun setSelection(memberId: String, couponId: String, selected: Boolean) {
        upsert(memberId, couponId) { it.copy(selected = selected) }
    }

    fun setFavorite(memberId: String, couponId: String, favorite: Boolean) {
        upsert(memberId, couponId) { it.copy(favorite = favorite) }
    }

    /** POS からの利用確定。複数クーポンを一括で消費済みにする。 */
    fun markUsed(memberId: String, couponIds: List<String>) {
        couponIds.forEach { couponId ->
            upsert(memberId, couponId) { it.copy(used = true) }
        }
    }

    fun getSelectedCouponIds(memberId: String): List<String> =
        states.findByMember(memberId).filter { it.selected }.map { it.couponId }.sorted()

    fun getFavoriteCouponIds(memberId: String): List<String> =
        states.findByMember(memberId).filter { it.favorite }.map { it.couponId }.sorted()

    fun getUsedCouponIds(memberId: String): Set<String> =
        states.findByMember(memberId).filter { it.used }.map { it.couponId }.toSet()

    private fun upsert(memberId: String, couponId: String, mutate: (MemberCouponState) -> MemberCouponState) {
        val current = states.find(memberId, couponId) ?: MemberCouponState(memberId, couponId)
        states.save(mutate(current))
    }
}
