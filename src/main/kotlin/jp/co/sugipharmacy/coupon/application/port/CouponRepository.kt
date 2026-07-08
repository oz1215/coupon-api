package jp.co.sugipharmacy.coupon.application.port

import jp.co.sugipharmacy.coupon.domain.coupon.Coupon
import java.time.Instant

/** クーポンマスタ（該当判定に必要な最小投影）の保管口。save は upsert。 */
interface CouponRepository {
    fun save(coupon: Coupon)

    fun findByIds(couponIds: Collection<String>): List<Coupon>

    /** 全員配信（ALL）かつ at 時点で有効期間内のクーポン。 */
    fun findAllDistributionWithinPeriod(at: Instant): List<Coupon>

    /** 緊急停止中クーポン（BFF が差し引くための一覧）。 */
    fun findSuspended(): List<Coupon>

    /** クーポンマスタ投影から削除する（削除イベント用）。存在しなければ無操作。 */
    fun delete(couponId: String)

    /** 全件削除（全件resync でマスタ投影を作り直す前段）。 */
    fun deleteAll()
}
