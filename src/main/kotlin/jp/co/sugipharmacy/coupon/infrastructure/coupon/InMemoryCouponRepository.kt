package jp.co.sugipharmacy.coupon.infrastructure.coupon

import jp.co.sugipharmacy.coupon.application.port.CouponRepository
import jp.co.sugipharmacy.coupon.domain.coupon.Coupon
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** クーポンマスタのインメモリ実装（MVP）。本番では管理面が投入するストアへ差し替える。 */
@Repository
class InMemoryCouponRepository : CouponRepository {

    private val store = ConcurrentHashMap<String, Coupon>()

    override fun save(coupon: Coupon) {
        store[coupon.couponId] = coupon
    }

    override fun findByIds(couponIds: Collection<String>): List<Coupon> =
        couponIds.mapNotNull { store[it] }

    override fun findAllDistributionWithinPeriod(at: Instant): List<Coupon> =
        store.values.filter { it.distributionType == DistributionType.ALL && it.isWithinValidPeriod(at) }

    override fun findSuspended(): List<Coupon> =
        store.values.filter { it.isSuspended }

    override fun delete(couponId: String) {
        store.remove(couponId)
    }

    override fun deleteAll() {
        store.clear()
    }
}
