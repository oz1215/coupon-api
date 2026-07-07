package jp.co.sugipharmacy.coupon.member.infrastructure

import jp.co.sugipharmacy.coupon.member.application.port.MemberCouponStateRepository
import jp.co.sugipharmacy.coupon.member.domain.MemberCouponState
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * 会員×クーポン状態のインメモリ実装（MVP）。
 * クーポンマスタの InMemoryCouponRepository とは別のストア — データ所有を物理的にも分離し、
 * 将来 member-coupon-state をサービス分割する際にデータ移行境界がそのまま切れるようにする。
 */
@Repository
class InMemoryMemberCouponStateRepository : MemberCouponStateRepository {

    private val store = ConcurrentHashMap<Pair<String, String>, MemberCouponState>()

    override fun save(state: MemberCouponState) {
        store[state.memberId to state.couponId] = state
    }

    override fun find(memberId: String, couponId: String): MemberCouponState? =
        store[memberId to couponId]

    override fun findByMember(memberId: String): List<MemberCouponState> =
        store.values.filter { it.memberId == memberId }

    override fun deleteByMember(memberId: String) {
        store.keys.removeIf { it.first == memberId }
    }
}
