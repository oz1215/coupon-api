package jp.co.sugipharmacy.coupon.domain

/**
 * ドメイン不変条件・入力正規化の違反。interfaces 層で 400 Bad Request（problem+json）に写像される。
 * `pointer` は不正箇所を指す JSON Pointer（条件ASTの検証で使う。無ければ null）。
 */
class DomainValidationError(message: String, val pointer: String? = null) : RuntimeException(message)
