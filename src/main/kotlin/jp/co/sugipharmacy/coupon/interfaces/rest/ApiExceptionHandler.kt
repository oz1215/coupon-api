package jp.co.sugipharmacy.coupon.interfaces.rest

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ErrorResponse(val message: String)

/** ドメイン検証・入力検証の失敗を 400 に写像する。ドメインは HTTP を知らない。 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(DomainValidationError::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleDomainValidation(e: DomainValidationError): ErrorResponse =
        ErrorResponse(e.message ?: "validation error")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBeanValidation(e: MethodArgumentNotValidException): ErrorResponse =
        ErrorResponse(
            e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
                .ifEmpty { "validation error" },
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUnreadable(e: HttpMessageNotReadableException): ErrorResponse =
        ErrorResponse("request body is malformed or missing required fields")
}
