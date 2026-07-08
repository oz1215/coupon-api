package jp.co.sugipharmacy.coupon.application.port

/**
 * 配布条件で使える属性名（`attr`）の語彙。正はプロファイルストアの属性カタログ（別件）。
 * カタログ外の属性を参照するルールは受領時に弾く。
 */
interface AttributeCatalog {
    fun isKnown(attr: String): Boolean
}
