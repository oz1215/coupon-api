package jp.co.sugipharmacy.coupon.interfaces.rest

import jp.co.sugipharmacy.coupon.application.event.EventRejectedException
import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * ドメイン検証・入力検証・イベント受領拒否を RFC 9457 problem+json に写像する。
 * ドメインは HTTP を知らない。機械可読な `code`、条件ASTの不正は `pointer`（JSON Pointer）を載せる。
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(DomainValidationError::class)
    fun handleDomainValidation(e: DomainValidationError): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.message ?: "validation error", e.pointer)

    /** 検証に失敗したイベント（`/internal/events`）。トランスポート層では DLQ 相当。 */
    @ExceptionHandler(EventRejectedException::class)
    fun handleEventRejected(e: EventRejectedException): ProblemDetail =
        problem(HttpStatus.UNPROCESSABLE_ENTITY, e.code, e.message ?: "event rejected", e.pointer)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBeanValidation(e: MethodArgumentNotValidException): ProblemDetail =
        problem(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
                .ifEmpty { "validation error" },
            null,
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "request body is malformed or missing required fields", null)

    private fun problem(status: HttpStatus, code: String, detail: String, pointer: String?): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(status, detail)
        pd.setProperty("code", code)
        if (pointer != null) pd.setProperty("pointer", pointer)
        return pd
    }
}
