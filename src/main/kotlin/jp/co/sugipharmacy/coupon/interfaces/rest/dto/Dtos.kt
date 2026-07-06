package jp.co.sugipharmacy.coupon.interfaces.rest.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jp.co.sugipharmacy.coupon.domain.coupon.CouponStatus
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import java.time.Instant

/**
 * REST 入出力の形。必須項目は Bean Validation（＋ jackson-module-kotlin の
 * 非null強制）で弾き、条件・属性の構造検証はドメインのパーサ／値オブジェクトが担う。
 */

@Schema(description = "クーポンマスタ登録（upsert）リクエスト")
data class RegisterCouponRequest(
    @field:Schema(description = "クーポンID", example = "CP-2026-0001")
    @field:NotBlank val couponId: String,
    @field:Schema(description = "配布種別。ALL=全員配信 / SEGMENT=属性該当 / INDIVIDUAL=個別付与（属性では決まらない）")
    @field:NotNull val distributionType: DistributionType,
    @field:Schema(description = "有効期間の開始（含む）", example = "2026-07-01T00:00:00Z")
    @field:NotNull val validFrom: Instant,
    @field:Schema(description = "有効期間の終了（含む）", example = "2026-07-31T23:59:59Z")
    @field:NotNull val validTo: Instant,
    @field:Schema(description = "状態。SUSPENDED は緊急停止中（/coupons/suspended に載る）", defaultValue = "ACTIVE")
    val status: CouponStatus = CouponStatus.ACTIVE,
)

@Schema(description = "配布ルール事前登録リクエスト")
data class RegisterDistributionRuleRequest(
    @field:Schema(description = "対象クーポンID", example = "CP-2026-0001")
    @field:NotBlank val couponId: String,
    /** 簡易形（rules 配列）またはリッチ形（and/or＋演算子）。ConditionParser が正規化する。 */
    @field:Schema(
        description = "配布条件。簡易形 {\"rules\":[{\"key\",\"value\"}...]}（eq の OR）またはリッチ形 {\"and\"/\"or\":[...]}＋eq/gte/lte/gt/lt/in。",
        example = """{"and":[{"key":"age","op":"gte","value":20},{"key":"age","op":"lte","value":39}]}""",
    )
    @field:NotNull val condition: Any,
    @field:Schema(description = "ルール有効期間の開始（含む）", example = "2026-07-01T00:00:00Z")
    @field:NotNull val validFrom: Instant,
    @field:Schema(description = "ルール有効期間の終了（含む）", example = "2026-07-31T23:59:59Z")
    @field:NotNull val validTo: Instant,
)

@Schema(description = "該当判定（eligibility）リクエスト")
data class EligibilityRequest(
    /** 1会員分の属性。BFF がプロファイルストアから取得して渡す。本サービスは保持しない。 */
    @field:Schema(
        description = "1会員分の属性。BFF がプロファイルストアから取得して渡す（本サービスは保持しない）。",
        example = """{"age":25,"gender":"female","store":"1024"}""",
    )
    @field:NotNull val attributes: Map<String, Any?>,
    /** 判定基準時刻。省略時はサーバの現在時刻。 */
    @field:Schema(description = "判定基準時刻。省略時はサーバの現在時刻。", example = "2026-07-07T09:00:00Z")
    val at: Instant? = null,
)

@Schema(description = "該当クーポン（IDと配布種別のみ。詳細・画像・並び順の整形は BFF/CMS 側）")
data class EligibleCouponResponse(
    @field:Schema(description = "クーポンID", example = "CP-2026-0001")
    val couponId: String,
    @field:Schema(description = "配布種別（ALL または SEGMENT。INDIVIDUAL は eligibility の対象外）")
    val distributionType: DistributionType,
)

@Schema(description = "該当判定（eligibility）レスポンス")
data class EligibilityResponse(
    val coupons: List<EligibleCouponResponse>,
)

@Schema(description = "緊急停止中クーポンID一覧。BFF が表示直前に差し引く。")
data class SuspendedCouponsResponse(
    @field:Schema(description = "SUSPENDED 状態のクーポンID", example = "[\"CP-2026-0009\"]")
    val couponIds: List<String>,
)
