package jp.co.sugipharmacy.coupon.domain.eligibility

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.domain.Scalar

/**
 * 1会員分の属性（年齢・性別・行動フラグ等）。リクエストごとに受け取る
 * 値オブジェクトであり、本サービスは永続化しない（member-agnostic）。
 */
class MemberAttributes private constructor(
    private val attributes: Map<String, Scalar>,
) {
    fun get(key: String): Scalar? = attributes[key]

    fun has(key: String): Boolean = attributes.containsKey(key)

    /** アダプタ向けの読み取り専用スナップショット（例: percolate 用ドキュメント化）。 */
    fun toMap(): Map<String, Scalar> = attributes

    companion object {
        fun from(raw: Map<String, Any?>): MemberAttributes =
            MemberAttributes(
                raw.mapValues { (key, value) ->
                    Scalar.of(value)
                        ?: throw DomainValidationError("attribute \"$key\" must be a string, number, or boolean")
                },
            )
    }
}
