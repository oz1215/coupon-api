package jp.co.sugipharmacy.coupon.domain.eligibility

import jp.co.sugipharmacy.coupon.domain.DomainValidationError
import jp.co.sugipharmacy.coupon.domain.Scalar
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MemberAttributesTest {

    @Test
    fun `文字列・数値・真偽値の属性を受け付ける`() {
        val attributes = MemberAttributes.from(
            mapOf("age" to 25, "gender" to "F", "boughtAlcoholWithin30Days" to true),
        )
        assertThat(attributes.get("age")).isEqualTo(Scalar.of(25))
        assertThat(attributes.get("gender")).isEqualTo(Scalar.Text("F"))
        assertThat(attributes.get("boughtAlcoholWithin30Days")).isEqualTo(Scalar.Bool(true))
    }

    @Test
    fun `存在しないキーは null`() {
        assertThat(MemberAttributes.from(emptyMap()).get("age")).isNull()
    }

    @Test
    fun `スカラーでない属性値（object・array・null）は拒否`() {
        assertThatThrownBy { MemberAttributes.from(mapOf("history" to listOf("a", "b"))) }
            .isInstanceOf(DomainValidationError::class.java).hasMessageContaining("history")
        assertThatThrownBy { MemberAttributes.from(mapOf("profile" to mapOf("age" to 20))) }
            .isInstanceOf(DomainValidationError::class.java)
        assertThatThrownBy { MemberAttributes.from(mapOf("age" to null)) }
            .isInstanceOf(DomainValidationError::class.java)
    }
}
