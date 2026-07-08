package jp.co.sugipharmacy.coupon.application.event.port

/**
 * 冪等取り込みの記録口。`eventId` の重複排除と、`couponId` ごとの
 * last-writer-wins（適用済み version）を担保する。
 * TODO: MVP は in-memory（プロセス内）。永続化ストア（DynamoDB 等）へ差し替える。
 */
interface ProcessedEventStore {
    /** この eventId を適用済みとして既に見たか。 */
    fun isProcessed(eventId: String): Boolean

    /** couponId に最後に適用した version（無ければ null）。 */
    fun lastAppliedVersion(couponId: String): Long?

    /** 適用成功を記録する（eventId を既知に、couponId の version を更新）。 */
    fun recordApplied(eventId: String, couponId: String, version: Long)

    /** 全消去（全件resync の前段）。 */
    fun clear()
}
