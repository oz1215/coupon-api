package jp.co.sugipharmacy.coupon.member.application.port

import jp.co.sugipharmacy.coupon.member.domain.MemberCouponState

/**
 * 会員×クーポン状態の保管口。member-coupon-state モジュール専用のストアであり、
 * クーポンマスタ（CouponRepository）・配布ルール（percolator）とはデータ所有を分離する。
 * 他モジュールのリポジトリへ読み書きしてはならない。
 */
interface MemberCouponStateRepository {
    /** (memberId, couponId) キーで upsert。 */
    fun save(state: MemberCouponState)

    fun find(memberId: String, couponId: String): MemberCouponState?

    fun findByMember(memberId: String): List<MemberCouponState>

    /** 会員の全状態を削除する（退会・ID統合の移行元クリア）。 */
    fun deleteByMember(memberId: String)
}
