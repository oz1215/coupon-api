package jp.co.sugipharmacy.coupon.infrastructure.attribute

import jp.co.sugipharmacy.coupon.application.port.AttributeCatalog
import org.springframework.stereotype.Component

/**
 * 属性カタログの暫定実装。全ての属性名を既知として通す。
 *
 * TODO: プロファイルストアの属性カタログ（`attr` 語彙の正）と接続し、カタログ外の
 * 属性を弾く。カタログの所在・取得方法はプロファイルストア側と合意（変更ブリーフ §4 未確定）。
 */
@Component
class AllowAllAttributeCatalog : AttributeCatalog {
    override fun isKnown(attr: String): Boolean = true
}
