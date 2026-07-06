package jp.co.sugipharmacy.coupon.interfaces.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jp.co.sugipharmacy.coupon.application.DistributionRuleService
import jp.co.sugipharmacy.coupon.application.RegisterDistributionRuleInput
import jp.co.sugipharmacy.coupon.interfaces.rest.dto.RegisterDistributionRuleRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/distribution-rules")
@Tag(name = "distribution-rules", description = "配布ルール（逆引き該当判定の条件）の事前登録")
class DistributionRulesController(
    private val distributionRuleService: DistributionRuleService,
) {
    /** 配布ルールの事前登録。条件は登録時に AST へ正規化され、不正なら 400。 */
    @Operation(
        summary = "配布ルールの事前登録",
        description = "条件は簡易形（rules 配列＝eq の OR）またはリッチ形（and/or＋eq/gte/lte/gt/lt/in）。" +
            "登録時に AST へ正規化され、不正なら 400。",
    )
    @ApiResponse(responseCode = "201", description = "登録済み")
    @ApiResponse(
        responseCode = "400",
        description = "条件の正規化・検証エラー",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterDistributionRuleRequest) {
        distributionRuleService.register(
            RegisterDistributionRuleInput(
                couponId = request.couponId,
                condition = request.condition,
                validFrom = request.validFrom,
                validTo = request.validTo,
            ),
        )
    }
}
