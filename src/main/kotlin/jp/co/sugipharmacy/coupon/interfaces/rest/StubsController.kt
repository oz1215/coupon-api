package jp.co.sugipharmacy.coupon.interfaces.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * MVP境界の明示。以下は NCP-2892 API仕様に載る本サービスの提供面だが、
 * 今回のMVPでは実装しない（501）。中核＝逆引き該当判定に集中する。
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

    /** クーポン選択更新 */
    @Operation(summary = "クーポン選択更新")
    @PutMapping("/coupons/selection")
    fun selection(): Nothing = notImplemented("クーポン選択更新")

    /** クーポンお気に入り更新 */
    @Operation(summary = "クーポンお気に入り更新")
    @PutMapping("/coupons/favorite")
    fun favorite(): Nothing = notImplemented("クーポンお気に入り更新")

    /** ウェルカムクーポン登録 */
    @Operation(summary = "ウェルカムクーポン登録")
    @PostMapping("/coupons/welcome")
    fun welcome(): Nothing = notImplemented("ウェルカムクーポン登録")

    /** イベント型クーポン登録 */
    @Operation(summary = "イベント型クーポン登録")
    @PostMapping("/coupons/event")
    fun event(): Nothing = notImplemented("イベント型クーポン登録")

    /** 使用済み・顧客選択クーポン取得（POS） */
    @Operation(summary = "使用済み・顧客選択クーポン取得（POS）")
    @GetMapping("/pos/coupons/selected")
    fun posSelected(): Nothing = notImplemented("使用済み・顧客選択クーポン取得（POS）")

    /** クーポン利用更新（POS） */
    @Operation(summary = "クーポン利用更新（POS）")
    @PutMapping("/pos/coupons/usage")
    fun posUsage(): Nothing = notImplemented("クーポン利用更新（POS）")

    /** 顧客別クーポン移行（ID統合） */
    @Operation(summary = "顧客別クーポン移行（ID統合）")
    @PutMapping("/members/coupons/transfer")
    fun transfer(): Nothing = notImplemented("顧客別クーポン移行（ID統合）")

    /** 顧客別クーポン統合（ID統合） */
    @Operation(summary = "顧客別クーポン統合（ID統合）")
    @PutMapping("/members/coupons/migrate")
    fun migrate(): Nothing = notImplemented("顧客別クーポン統合（ID統合）")

    /** 顧客別クーポン削除（ID統合） */
    @Operation(summary = "顧客別クーポン削除（ID統合）")
    @DeleteMapping("/members/coupons/delete")
    fun memberCouponDelete(): Nothing = notImplemented("顧客別クーポン削除（ID統合）")

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

    /** セグメント配布API（SMC / DMP・新設） */
    @Operation(summary = "セグメント配布API（SMC / DMP・新設）")
    @PostMapping("/segments/distribution")
    fun segmentDistribution(): Nothing = notImplemented("セグメント配布API（SMC / DMP）")
}
