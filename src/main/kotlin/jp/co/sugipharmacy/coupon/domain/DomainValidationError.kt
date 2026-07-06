package jp.co.sugipharmacy.coupon.domain

/** ドメイン不変条件・入力正規化の違反。interfaces 層で 400 Bad Request に写像される。 */
class DomainValidationError(message: String) : RuntimeException(message)
