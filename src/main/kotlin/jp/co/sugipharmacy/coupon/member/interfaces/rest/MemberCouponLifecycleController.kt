package jp.co.sugipharmacy.coupon.member.interfaces.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jp.co.sugipharmacy.coupon.member.application.MemberCouponGrantService
import jp.co.sugipharmacy.coupon.member.application.MemberCouponIdIntegrationService
import jp.co.sugipharmacy.coupon.member.interfaces.rest.dto.GrantCouponRequest
import jp.co.sugipharmacy.coupon.member.interfaces.rest.dto.IdIntegrationRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * member-coupon-state モジュールの会員ライフサイクル操作。
 * 個別付与（ウェルカム/イベント = INDIVIDUAL の付与）と ID統合（移行・統合・退会削除）。
 * いずれも会員キーの状態だけを書き換える — eligibility 側のストア・判定には触れない。
 */
@RestController
@RequestMapping("/members")
@Tag(name = "member-coupon-state", description = "会員キーの状態＝coupon-api内の別モジュール・別データ所有")
class MemberCouponLifecycleController(
    private val grantService: MemberCouponGrantService,
    private val idIntegrationService: MemberCouponIdIntegrationService,
) {
    @Operation(
        summary = "ウェルカムクーポン登録（INDIVIDUAL の個別付与）",
        description = "入会を契機に INDIVIDUAL クーポンを会員へ付与する。クーポンマスタに存在しない、" +
            "または INDIVIDUAL 以外の couponId は 400。付与済み INDIVIDUAL は表示一覧の合成" +
            "（POST /members/{memberId}/coupon-list）にのみ現れ、POST /coupons/eligibility の生集合には現れない。",
    )
    @ApiResponse(responseCode = "201", description = "付与済み")
    @ApiResponse(responseCode = "400", description = "クーポンが存在しない／INDIVIDUAL でない／入力検証エラー")
    @PostMapping("/{memberId}/coupons/welcome")
    @ResponseStatus(HttpStatus.CREATED)
    fun welcome(
        @Parameter(description = "会員ID", example = "M-000123") @PathVariable memberId: String,
        @Valid @RequestBody request: GrantCouponRequest,
    ) {
        grantService.grant(memberId, request.couponId)
    }

    @Operation(
        summary = "イベント型クーポン登録（INDIVIDUAL の個別付与）",
        description = "イベントを契機に INDIVIDUAL クーポンを会員へ付与する。付与の意味・検証はウェルカムと同一" +
            "（契機が違うだけ）。クーポンマスタに存在しない、または INDIVIDUAL 以外の couponId は 400。",
    )
    @ApiResponse(responseCode = "201", description = "付与済み")
    @ApiResponse(responseCode = "400", description = "クーポンが存在しない／INDIVIDUAL でない／入力検証エラー")
    @PostMapping("/{memberId}/coupons/event")
    @ResponseStatus(HttpStatus.CREATED)
    fun event(
        @Parameter(description = "会員ID", example = "M-000123") @PathVariable memberId: String,
        @Valid @RequestBody request: GrantCouponRequest,
    ) {
        grantService.grant(memberId, request.couponId)
    }

    @Operation(
        summary = "顧客別クーポン移行（ID統合）",
        description = "旧ID（fromMemberId）の会員キー状態をすべて新ID（toMemberId）へ移す。" +
            "同一 couponId が衝突したら各フラグ（used/granted/selected/favorite）の OR でマージし、移行後に旧IDの状態を空にする。",
    )
    @ApiResponse(responseCode = "200", description = "移行済み")
    @ApiResponse(responseCode = "400", description = "入力検証エラー（同一ID・空IDなど）")
    @PutMapping("/coupons/transfer")
    fun transfer(@Valid @RequestBody request: IdIntegrationRequest) {
        idIntegrationService.transfer(request.fromMemberId, request.toMemberId)
    }

    @Operation(
        summary = "顧客別クーポン統合（ID統合）",
        description = "fromMemberId の会員キー状態を toMemberId へ統合（和集合）する。" +
            "同一 couponId の衝突は各フラグの OR でマージし、統合後に fromMemberId の状態を空にする。" +
            "レガシーの会員種別パターン分岐は MVP 対象外（transfer と同じマージ規則へ正規化）。",
    )
    @ApiResponse(responseCode = "200", description = "統合済み")
    @ApiResponse(responseCode = "400", description = "入力検証エラー（同一ID・空IDなど）")
    @PutMapping("/coupons/migrate")
    fun migrate(@Valid @RequestBody request: IdIntegrationRequest) {
        idIntegrationService.migrate(request.fromMemberId, request.toMemberId)
    }

    @Operation(
        summary = "顧客別クーポン削除（退会）",
        description = "退会に伴い会員キー状態（選択・お気に入り・消費済み・付与済み）をすべて削除する。冪等。",
    )
    @ApiResponse(responseCode = "204", description = "削除済み")
    @DeleteMapping("/{memberId}/coupons")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(
        @Parameter(description = "会員ID", example = "M-000123") @PathVariable memberId: String,
    ) {
        idIntegrationService.withdraw(memberId)
    }
}
