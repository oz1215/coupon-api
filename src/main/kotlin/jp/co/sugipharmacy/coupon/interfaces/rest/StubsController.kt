package jp.co.sugipharmacy.coupon.interfaces.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * MVP境界の明示。以下は NCP-2892 API仕様に載る本サービスの提供面だが、
 * 今回のMVPでは実装しない（501）。クーポン読み取りモデル・coupon-admin・外部連携の領分。
 * 会員キーの操作（選択・お気に入り・利用・付与・ID統合）はここではなく
 * member-coupon-state モジュール（`member` パッケージ）が持つ。
 */
@RestController
@Tag(name = "stubs", description = "MVP対象外のAPI面（NCP-2892 API仕様には載るが本MVPでは未実装）。常に 501 を返す。")
@ApiResponse(responseCode = "501", description = "MVP対象外（未実装）")
class StubsController {

    private fun notImplemented(api: String): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "$api is out of MVP scope")

    /** クーポン詳細情報取得 / 全件取得 */
    @Operation(summary = "クーポン詳細情報取得 / 全件取得")
    @GetMapping("/coupons/details")
    fun couponDetails(): Nothing = notImplemented("クーポン詳細情報取得 / 全件取得")

    /** 店舗用クーポン詳細情報取得（PIT） */
    @Operation(summary = "店舗用クーポン詳細情報取得（PIT）")
    @GetMapping("/stores/coupons")
    fun storeCoupons(): Nothing = notImplemented("店舗用クーポン詳細情報取得（PIT）")

    /** 商品紐づきクーポンID一覧取得（在庫） */
    @Operation(summary = "商品紐づきクーポンID一覧取得（在庫）")
    @GetMapping("/products/coupon-ids")
    fun productCouponIds(): Nothing = notImplemented("商品紐づきクーポンID一覧取得（在庫）")

    /** クーポン存在確認（CMS） */
    @Operation(summary = "クーポン存在確認（CMS）")
    @GetMapping("/cms/coupons/existence")
    fun cmsExistence(): Nothing = notImplemented("クーポン存在確認（CMS）")

    /** クーポン登録可否（Smooth） */
    @Operation(summary = "クーポン登録可否（Smooth）")
    @PostMapping("/smooth/coupons/registrability")
    fun smoothRegistrability(): Nothing = notImplemented("クーポン登録可否（Smooth）")

    /** クーポン情報取得（SMC） */
    @Operation(summary = "クーポン情報取得（SMC）")
    @GetMapping("/smc/coupons")
    fun smcCoupons(): Nothing = notImplemented("クーポン情報取得（SMC）")

    // セグメント配布API（旧 SMC/DMP push）は廃止。配布ルールは coupon-admin で登録され、
    // coupon-api はイベント購読（/internal/events → EventIngestionService）で受領する。
}
