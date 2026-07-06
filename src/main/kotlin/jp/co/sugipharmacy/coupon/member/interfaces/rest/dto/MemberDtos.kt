package jp.co.sugipharmacy.coupon.member.interfaces.rest.dto

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jp.co.sugipharmacy.coupon.domain.coupon.DistributionType
import java.time.Instant

/** member-coupon-state モジュールの REST 入出力。eligibility 側の DTO とは分離して所有する。 */

@Schema(description = "会員のクーポン表示一覧リクエスト。属性は BFF がプロファイルストア（外部）から取得して渡す — 本サービスは属性を保持しない。")
data class MemberCouponListRequest(
    @field:Schema(
        description = "1会員分の属性。BFF がプロファイルストアから取得して渡す（本サービスは保持しない）。",
        example = """{"age":25,"gender":"female","store":"1024"}""",
    )
    @field:NotNull val attributes: Map<String, Any?>,
    @field:Schema(description = "判定基準時刻。省略時はサーバの現在時刻。", example = "2026-07-07T09:00:00Z")
    val at: Instant? = null,
)

@Schema(description = "表示対象クーポン（該当判定の結果から消費済み・停止中を差し引いた後）")
data class DisplayCouponResponse(
    @field:Schema(description = "クーポンID", example = "CP-2026-0001")
    val couponId: String,
    @field:Schema(description = "配布種別（ALL または SEGMENT）")
    val distributionType: DistributionType,
)

@Schema(description = "会員のクーポン表示一覧レスポンス（該当判定 − 消費済み − 停止中）")
data class MemberCouponListResponse(
    val coupons: List<DisplayCouponResponse>,
)

@Schema(description = "クーポン選択更新リクエスト")
data class SelectionUpdateRequest(
    /** Jackson は欠落した primitive を false で埋めるため、nullable + @NotNull で欠落を 400 にする。 */
    @field:Schema(description = "true=選択 / false=選択解除", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull val selected: Boolean?,
)

@Schema(description = "クーポンお気に入り更新リクエスト")
data class FavoriteUpdateRequest(
    /** Jackson は欠落した primitive を false で埋めるため、nullable + @NotNull で欠落を 400 にする。 */
    @field:Schema(description = "true=お気に入り登録 / false=解除", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @field:NotNull val favorite: Boolean?,
)

@Schema(description = "クーポン利用更新（POS）リクエスト。会計で利用されたクーポンを消費済みにする。")
data class CouponUsageRequest(
    @field:ArraySchema(
        schema = Schema(description = "消費済みにするクーポンID"),
        arraySchema = Schema(example = "[\"CP-2026-0001\",\"CP-2026-0002\"]"),
    )
    @field:NotEmpty val couponIds: List<String>,
)

@Schema(description = "会員が選択中のクーポンID一覧（POS 会計時の照会用）")
data class SelectedCouponsResponse(
    @field:Schema(description = "選択中のクーポンID", example = "[\"CP-2026-0001\"]")
    val couponIds: List<String>,
)
