package jp.co.sugipharmacy.coupon.application

import jp.co.sugipharmacy.coupon.domain.rule.ConditionLimits
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * 配布条件の受領時検証の上限（`coupon.rule.*`）。
 * TODO: 具体値は coupon-rule-schema / AST仕様 §4 で確定予定（現状は暫定）。
 */
@Component
@ConfigurationProperties(prefix = "coupon.rule")
class RuleValidationProperties {
    var maxDepth: Int = 8
    var maxInValues: Int = 100

    fun toLimits(): ConditionLimits = ConditionLimits(maxDepth = maxDepth, maxInValues = maxInValues)
}
