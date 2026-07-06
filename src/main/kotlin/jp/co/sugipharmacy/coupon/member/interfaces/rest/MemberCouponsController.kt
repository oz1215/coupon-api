package jp.co.sugipharmacy.coupon.member.interfaces.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jp.co.sugipharmacy.coupon.member.application.MemberCouponListService
import jp.co.sugipharmacy.coupon.member.application.MemberCouponStateService
import jp.co.sugipharmacy.coupon.member.interfaces.rest.dto.CouponUsageRequest
import jp.co.sugipharmacy.coupon.member.interfaces.rest.dto.DisplayCouponResponse
import jp.co.sugipharmacy.coupon.member.interfaces.rest.dto.FavoriteUpdateRequest
import jp.co.sugipharmacy.coupon.member.interfaces.rest.dto.MemberCouponListRequest
import jp.co.sugipharmacy.coupon.member.interfaces.rest.dto.MemberCouponListResponse
import jp.co.sugipharmacy.coupon.member.interfaces.rest.dto.SelectedCouponsResponse
import jp.co.sugipharmacy.coupon.member.interfaces.rest.dto.SelectionUpdateRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * member-coupon-state モジュールの REST 面。会員キーの状態（選択・お気に入り・消費済み）と、
 * 表示一覧の合成（eligibility − 消費済み − 停止中）を提供する。
 * eligibility 側のコントローラ・サービスへ差し引きロジックを持ち込まないこと。
 */
@RestController
@RequestMapping("/members")
@Tag(name = "member-coupon-state", description = "会員キーの状態＝coupon-api内の別モジュール・別データ所有")
class MemberCouponsController(
    private val listService: MemberCouponListService,
    private val stateService: MemberCouponStateService,
) {
    /** 表示一覧の合成。旧 CCAP0401 の置き換えだが、属性はリクエストで受け取り member-agnostic を維持する。 */
    @Operation(
        summary = "会員のクーポン表示一覧（該当判定 − 消費済み − 停止中）",
        description = "EligibilityService の生の結果から、会員の消費済みクーポンと緊急停止中（SUSPENDED）を" +
            "差し引いて返す合成エンドポイント。属性は BFF がプロファイルストアから取得して渡す（本サービスは保持しない）。",
    )
    @ApiResponse(responseCode = "200", description = "表示対象クーポン（ID＋distributionType）の一覧")
    @PostMapping("/{memberId}/coupon-list")
    fun couponList(
        @Parameter(description = "会員ID（不透明な識別子）", example = "M-000123")
        @PathVariable memberId: String,
        @Valid @RequestBody request: MemberCouponListRequest,
    ): MemberCouponListResponse {
        val coupons = listService.getDisplayCoupons(
            memberId = memberId,
            rawAttributes = request.attributes,
            at = request.at ?: Instant.now(),
        )
        return MemberCouponListResponse(
            coupons = coupons.map { DisplayCouponResponse(it.couponId, it.distributionType) },
        )
    }

    @Operation(summary = "クーポン選択更新", description = "会員がクーポンを選択／選択解除する。")
    @ApiResponse(responseCode = "204", description = "更新済み")
    @PutMapping("/{memberId}/coupons/{couponId}/selection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateSelection(
        @Parameter(description = "会員ID", example = "M-000123") @PathVariable memberId: String,
        @Parameter(description = "クーポンID", example = "CP-2026-0001") @PathVariable couponId: String,
        @Valid @RequestBody request: SelectionUpdateRequest,
    ) {
        stateService.setSelection(memberId, couponId, requireNotNull(request.selected))
    }

    @Operation(summary = "クーポンお気に入り更新", description = "会員がクーポンをお気に入り登録／解除する。")
    @ApiResponse(responseCode = "204", description = "更新済み")
    @PutMapping("/{memberId}/coupons/{couponId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateFavorite(
        @Parameter(description = "会員ID", example = "M-000123") @PathVariable memberId: String,
        @Parameter(description = "クーポンID", example = "CP-2026-0001") @PathVariable couponId: String,
        @Valid @RequestBody request: FavoriteUpdateRequest,
    ) {
        stateService.setFavorite(memberId, couponId, requireNotNull(request.favorite))
    }

    @Operation(
        summary = "クーポン利用更新（POS）",
        description = "会計で利用されたクーポンを消費済みにする。以降、表示一覧の合成で差し引かれる。",
    )
    @ApiResponse(responseCode = "204", description = "消費済みとして記録済み")
    @PostMapping("/{memberId}/coupons/usage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markUsage(
        @Parameter(description = "会員ID", example = "M-000123") @PathVariable memberId: String,
        @Valid @RequestBody request: CouponUsageRequest,
    ) {
        stateService.markUsed(memberId, request.couponIds)
    }

    @Operation(summary = "顧客選択クーポン取得（POS）", description = "会員が選択中のクーポンID一覧。POS が会計時に照会する。")
    @ApiResponse(responseCode = "200", description = "選択中のクーポンID一覧")
    @GetMapping("/{memberId}/coupons/selected")
    fun selectedCoupons(
        @Parameter(description = "会員ID", example = "M-000123") @PathVariable memberId: String,
    ): SelectedCouponsResponse =
        SelectedCouponsResponse(couponIds = stateService.getSelectedCouponIds(memberId))
}
