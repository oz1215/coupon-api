package jp.co.sugipharmacy.coupon.interfaces.rest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** エンドポイント越しの一気通貫: 登録 → 逆引き該当判定 → 停止一覧。 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun postJson(path: String, body: String) =
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun registerCoupon(
        couponId: String,
        distributionType: String,
        validFrom: String = "2026-07-01T00:00:00Z",
        validTo: String = "2026-07-31T23:59:59Z",
        status: String = "ACTIVE",
    ) {
        postJson(
            "/coupons",
            """
            {
              "couponId": "$couponId",
              "distributionType": "$distributionType",
              "validFrom": "$validFrom",
              "validTo": "$validTo",
              "status": "$status"
            }
            """.trimIndent(),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `登録 → 属性判定 → 該当クーポンIDが返る（リッチ形 20代ルール + 全員配信 + 停止差し引きなし）`() {
        registerCoupon("SEG-20S", "SEGMENT")
        registerCoupon("ALL-1", "ALL")
        registerCoupon("ALL-SUS", "ALL", status = "SUSPENDED")
        registerCoupon("ALL-OLD", "ALL", validFrom = "2026-06-01T00:00:00Z", validTo = "2026-06-30T23:59:59Z")

        postJson(
            "/distribution-rules",
            """
            {
              "couponId": "SEG-20S",
              "condition": {
                "and": [
                  { "key": "age", "operator": "gte", "value": 20 },
                  { "key": "age", "operator": "lte", "value": 29 }
                ]
              },
              "validFrom": "2026-07-01T00:00:00Z",
              "validTo": "2026-07-31T23:59:59Z"
            }
            """.trimIndent(),
        ).andExpect(status().isCreated)

        postJson(
            "/coupons/eligibility",
            """{ "attributes": { "age": 25 }, "at": "2026-07-15T00:00:00Z" }""",
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons.length()").value(3))
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'SEG-20S')].distributionType").value("SEGMENT"))
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-1')]").exists())
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-SUS')]").exists())
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-OLD')]").doesNotExist())

        // 境界外（30歳）では SEGMENT が落ちる
        postJson(
            "/coupons/eligibility",
            """{ "attributes": { "age": 30 }, "at": "2026-07-15T00:00:00Z" }""",
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'SEG-20S')]").doesNotExist())
    }

    @Test
    fun `簡易形（rules 配列）は eq の OR として判定される`() {
        registerCoupon("SEG-FLAT", "SEGMENT")
        postJson(
            "/distribution-rules",
            """
            {
              "couponId": "SEG-FLAT",
              "condition": { "rules": [ { "key": "age", "value": "20" }, { "key": "age", "value": "30" } ] },
              "validFrom": "2026-07-01T00:00:00Z",
              "validTo": "2026-07-31T23:59:59Z"
            }
            """.trimIndent(),
        ).andExpect(status().isCreated)

        postJson(
            "/coupons/eligibility",
            """{ "attributes": { "age": 20 }, "at": "2026-07-15T00:00:00Z" }""",
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'SEG-FLAT')]").exists())

        postJson(
            "/coupons/eligibility",
            """{ "attributes": { "age": 25 }, "at": "2026-07-15T00:00:00Z" }""",
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'SEG-FLAT')]").doesNotExist())
    }

    @Test
    fun `停止中クーポン一覧を返す（BFF が表示直前に差し引く）`() {
        registerCoupon("ALL-1", "ALL")
        registerCoupon("SEG-SUS", "SEGMENT", status = "SUSPENDED")

        mockMvc.perform(get("/coupons/suspended"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.couponIds.length()").value(1))
            .andExpect(jsonPath("$.couponIds[0]").value("SEG-SUS"))
    }

    @Test
    fun `不正な入力は 400`() {
        // couponId 空
        postJson(
            "/coupons",
            """
            {
              "couponId": "",
              "distributionType": "ALL",
              "validFrom": "2026-07-01T00:00:00Z",
              "validTo": "2026-07-31T23:59:59Z"
            }
            """.trimIndent(),
        ).andExpect(status().isBadRequest)

        // validTo < validFrom（ドメイン不変条件）
        postJson(
            "/coupons",
            """
            {
              "couponId": "CPN-X",
              "distributionType": "ALL",
              "validFrom": "2026-07-31T00:00:00Z",
              "validTo": "2026-07-01T00:00:00Z"
            }
            """.trimIndent(),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("validTo")))

        // 未知の演算子
        postJson(
            "/distribution-rules",
            """
            {
              "couponId": "SEG-X",
              "condition": { "key": "age", "operator": "between", "value": 20 },
              "validFrom": "2026-07-01T00:00:00Z",
              "validTo": "2026-07-31T23:59:59Z"
            }
            """.trimIndent(),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("operator")))

        // スカラーでない属性値
        postJson(
            "/coupons/eligibility",
            """{ "attributes": { "profile": { "age": 20 } } }""",
        ).andExpect(status().isBadRequest)

        // 必須フィールド欠落（attributes なし）
        postJson("/coupons/eligibility", """{ "at": "2026-07-15T00:00:00Z" }""")
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `MVP対象外のAPIは 501 Not Implemented`() {
        mockMvc.perform(get("/coupons/details")).andExpect(status().isNotImplemented)
        mockMvc.perform(get("/cms/coupons/existence")).andExpect(status().isNotImplemented)
        mockMvc.perform(get("/smc/coupons")).andExpect(status().isNotImplemented)
        mockMvc.perform(get("/stores/coupons")).andExpect(status().isNotImplemented)
        mockMvc.perform(get("/products/coupon-ids")).andExpect(status().isNotImplemented)
        postJson("/smooth/coupons/registrability", "{}").andExpect(status().isNotImplemented)
        postJson("/segments/distribution", "{}").andExpect(status().isNotImplemented)
    }
}
