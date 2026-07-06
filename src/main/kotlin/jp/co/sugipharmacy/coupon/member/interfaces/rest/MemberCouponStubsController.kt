package jp.co.sugipharmacy.coupon.member.interfaces.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * member-coupon-state モジュールの会員ライフサイクル操作（MVP対象外・501）。
 * 付与（ウェルカム/イベント）と ID 統合（移行・統合・退会削除）は会員キーの状態を
 * 書き換える操作なので、このモジュールが将来の実装先。今は薄いスタブに留める。
 */
@RestController
@Tag(name = "member-coupon-state", description = "会員キーの状態＝coupon-api内の別モジュール・別データ所有")
@ApiResponse(responseCode = "501", description = "MVP対象外（未実装）")
class MemberCouponStubsController {

    private fun notImplemented(api: String): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "$api is out of MVP scope")

    @Operation(summary = "ウェルカムクーポン登録", description = "入会時の個別付与（INDIVIDUAL）。MVP対象外・501。")
    @PostMapping("/members/{memberId}/coupons/welcome")
    fun welcome(@PathVariable memberId: String): Nothing = notImplemented("ウェルカムクーポン登録")

    @Operation(summary = "イベント型クーポン登録", description = "イベント起点の個別付与（INDIVIDUAL）。MVP対象外・501。")
    @PostMapping("/members/{memberId}/coupons/event")
    fun event(@PathVariable memberId: String): Nothing = notImplemented("イベント型クーポン登録")

    @Operation(summary = "顧客別クーポン移行（ID統合）", description = "旧IDから新IDへ会員キー状態を移す。MVP対象外・501。")
    @PutMapping("/members/coupons/transfer")
    fun transfer(): Nothing = notImplemented("顧客別クーポン移行（ID統合）")

    @Operation(summary = "顧客別クーポン統合（ID統合）", description = "複数IDの会員キー状態を統合する。MVP対象外・501。")
    @PutMapping("/members/coupons/migrate")
    fun migrate(): Nothing = notImplemented("顧客別クーポン統合（ID統合）")

    @Operation(summary = "顧客別クーポン削除（退会）", description = "退会に伴い会員キー状態を削除する。MVP対象外・501。")
    @DeleteMapping("/members/{memberId}/coupons")
    fun withdraw(@PathVariable memberId: String): Nothing = notImplemented("顧客別クーポン削除（退会）")
}
