package jp.co.sugipharmacy.coupon.member.domain

import jp.co.sugipharmacy.coupon.domain.DomainValidationError

/**
 * 会員×クーポンの相互作用状態（選択・お気に入り・消費済み）。
 * member-coupon-state 境界づけられたコンテキストの中核モデル。
 *
 * 保持するのは「会員がそのクーポンに対して行った操作の状態」だけである。
 * - 会員属性（年齢・性別・店舗など）は保持しない — プロファイルストア（外部）の領分。
 * - 「配布された」という事前紐づけレコードも作らない — 該当判定は毎回
 *   eligibility 側が属性から逆引きする（member-agnostic を毀損しない）。
 *
 * 会員は不透明な memberId 文字列で識別する。
 */
data class MemberCouponState(
    val memberId: String,
    val couponId: String,
    val selected: Boolean = false,
    val favorite: Boolean = false,
    val used: Boolean = false,
) {
    init {
        if (memberId.isBlank()) {
            throw DomainValidationError("memberId must not be empty")
        }
        if (couponId.isBlank()) {
            throw DomainValidationError("couponId must not be empty")
        }
    }
}
