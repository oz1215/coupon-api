package jp.co.sugipharmacy.coupon.interfaces.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jp.co.sugipharmacy.coupon.application.CouponService
import jp.co.sugipharmacy.coupon.application.EligibilityService
import jp.co.sugipharmacy.coupon.application.RegisterCouponInput
import jp.co.sugipharmacy.coupon.interfaces.rest.dto.EligibilityRequest
import jp.co.sugipharmacy.coupon.interfaces.rest.dto.EligibilityResponse
import jp.co.sugipharmacy.coupon.interfaces.rest.dto.EligibleCouponResponse
import jp.co.sugipharmacy.coupon.interfaces.rest.dto.RegisterCouponRequest
import jp.co.sugipharmacy.coupon.interfaces.rest.dto.SuspendedCouponsResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/coupons")
@Tag(name = "coupons", description = "クーポンマスタ登録・該当判定（eligibility）・緊急停止一覧")
class CouponsController(
    private val couponService: CouponService,
    private val eligibilityService: EligibilityService,
) {
    /** クーポンマスタ登録（upsert）。eligibility 判定のためのシードパス。 */
    @Operation(summary = "クーポンマスタ登録（upsert）", description = "eligibility 判定のためのシードパス。同一 couponId は上書き。")
    @ApiResponse(responseCode = "201", description = "登録済み")
    @ApiResponse(
        responseCode = "400",
        description = "入力検証・ドメイン検証エラー",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterCouponRequest) {
        couponService.register(
            RegisterCouponInput(
                couponId = request.couponId,
                distributionType = request.distributionType,
                validFrom = request.validFrom,
                validTo = request.validTo,
                status = request.status,
            ),
        )
    }

    /**
     * クーポン一覧取得（属性に基づく該当判定＋全員配信の付与）。
     * 消費済み・選択状態・SUSPENDED の差し引きは行わない（BFF の責務）。
     */
    @Operation(
        summary = "クーポン一覧取得（属性に基づく該当判定＋全員配信の付与。差し引きなしの生集合）",
        description = "percolate 該当の SEGMENT（有効期間内）∪ 有効期間内の ALL の生集合を返す。" +
            "消費済み・選択状態・SUSPENDED の差し引きは行わない（会員向け表示一覧の合成は " +
            "POST /members/{memberId}/coupon-list、BFF 経路の差し引きは BFF の責務）。",
    )
    @ApiResponse(responseCode = "200", description = "該当クーポン（ID＋distributionType）の一覧")
    @ApiResponse(
        responseCode = "400",
        description = "属性・判定基準時刻の検証エラー",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @PostMapping("/eligibility")
    fun eligibility(@Valid @RequestBody request: EligibilityRequest): EligibilityResponse {
        val eligible = eligibilityService.findEligibleCoupons(
            rawAttributes = request.attributes,
            at = request.at ?: Instant.now(),
        )
        return EligibilityResponse(
            coupons = eligible.map { EligibleCouponResponse(it.couponId, it.distributionType) },
        )
    }

    /** 緊急停止中クーポンIDの小さな一覧。BFF が表示直前に差し引く（即時反映）。 */
    @Operation(
        summary = "緊急停止中クーポンID一覧",
        description = "BFF が表示直前に eligibility 結果から差し引く（キャッシュ TTL に依らず即時反映）。",
    )
    @GetMapping("/suspended")
    fun suspended(): SuspendedCouponsResponse =
        SuspendedCouponsResponse(couponIds = couponService.getSuspendedCouponIds())
}
