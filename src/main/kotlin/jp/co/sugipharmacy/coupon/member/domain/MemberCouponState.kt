package jp.co.sugipharmacy.coupon.member.domain

import jp.co.sugipharmacy.coupon.domain.DomainValidationError

/**
 * 会員×クーポンの相互作用状態（選択・お気に入り・消費済み・付与済み）。
 * member-coupon-state 境界づけられたコンテキストの中核モデル。
 *
 * 保持するのは「会員がそのクーポンに対して行った（受けた）操作の状態」だけである。
 * - 会員属性（年齢・性別・店舗など）は保持しない — プロファイルストア（外部）の領分。
 * - SEGMENT/ALL について「配布された」という事前紐づけレコードは作らない — 該当判定は毎回
 *   eligibility 側が属性から逆引きする（member-agnostic を毀損しない）。
 * - `granted` は INDIVIDUAL クーポンの個別付与（ウェルカム/イベント）のみを表す。
 *   INDIVIDUAL は属性で決まらないため eligibility の対象外であり、付与記録は
 *   事前紐づけの復活ではなく「会員キー操作の結果」— このモジュールの所有物である。
 *
 * 会員は不透明な memberId 文字列で識別する。
 */
data class MemberCouponState(
    val memberId: String,
    val couponId: String,
    val selected: Boolean = false,
    val favorite: Boolean = false,
    val used: Boolean = false,
    val granted: Boolean = false,
) {
    init {
        if (memberId.isBlank()) {
            throw DomainValidationError("memberId must not be empty")
        }
        if (couponId.isBlank()) {
            throw DomainValidationError("couponId must not be empty")
        }
    }

    /**
     * ID統合（transfer / migrate）のマージ規則: 同一 couponId で衝突したら各フラグの OR を取る。
     * used = a.used || b.used / granted = a.granted || b.granted /
     * selected = a.selected || b.selected / favorite = a.favorite || b.favorite。
     * 「一度でも消費済みなら消費済み」「一度でも付与済みなら付与済み」に倒す（誤配布より配布漏れ側の安全）。
     */
    fun mergedWith(other: MemberCouponState): MemberCouponState {
        if (couponId != other.couponId) {
            throw DomainValidationError("cannot merge states of different coupons (\"$couponId\" vs \"${other.couponId}\")")
        }
        return copy(
            selected = selected || other.selected,
            favorite = favorite || other.favorite,
            used = used || other.used,
            granted = granted || other.granted,
        )
    }
}
