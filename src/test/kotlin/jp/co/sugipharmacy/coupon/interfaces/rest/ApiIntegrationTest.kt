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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * エンドポイント越しの一気通貫: クーポン投入 → イベントで配布ルール投影 → 逆引き該当判定 → 停止一覧。
 * 配布ルールの外部登録APIは廃止され、取り込みは /internal/events（イベント購読の継ぎ目）。
 */
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
            """{"couponId":"$couponId","distributionType":"$distributionType","validFrom":"$validFrom","validTo":"$validTo","status":"$status"}""",
        ).andExpect(status().isCreated)
    }

    /** SEGMENT クーポンを配布条件つきで CouponApproved イベントとして投影する。 */
    private fun approveSegment(couponId: String, conditionJson: String, eventId: String = "evt-$couponId") {
        postJson(
            "/internal/events",
            """
            {
              "eventId": "$eventId", "type": "CouponApproved", "version": 1,
              "occurredAt": "2026-07-01T00:00:00Z",
              "data": {
                "couponId": "$couponId", "schemaVersion": "1.0",
                "distributionType": "SEGMENT", "status": "ACTIVE",
                "effectiveFrom": "2026-07-01T00:00:00Z", "effectiveTo": "2026-07-31T23:59:59Z",
                "condition": $conditionJson
              }
            }
            """.trimIndent(),
        ).andExpect(status().isAccepted)
    }

    @Test
    fun `イベント投影 → 属性判定 → 該当クーポンIDが返る（20代AND + 全員配信 + 停止差し引きなし）`() {
        registerCoupon("ALL-1", "ALL")
        registerCoupon("ALL-SUS", "ALL", status = "SUSPENDED")
        registerCoupon("ALL-OLD", "ALL", validFrom = "2026-06-01T00:00:00Z", validTo = "2026-06-30T23:59:59Z")
        approveSegment(
            "SEG-20S",
            """{"op":"and","nodes":[{"op":"gte","attr":"age","value":20},{"op":"lte","attr":"age","value":29}]}""",
        )

        postJson("/coupons/eligibility", """{ "attributes": { "age": 25 }, "at": "2026-07-15T00:00:00Z" }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons.length()").value(3))
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'SEG-20S')].distributionType").value("SEGMENT"))
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-1')]").exists())
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-SUS')]").exists())
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-OLD')]").doesNotExist())

        postJson("/coupons/eligibility", """{ "attributes": { "age": 30 }, "at": "2026-07-15T00:00:00Z" }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'SEG-20S')]").doesNotExist())
    }

    @Test
    fun `正準ORは eq の論理和として判定される`() {
        approveSegment(
            "SEG-OR",
            """{"op":"or","nodes":[{"op":"eq","attr":"age","value":20},{"op":"eq","attr":"age","value":30}]}""",
        )

        postJson("/coupons/eligibility", """{ "attributes": { "age": 20 }, "at": "2026-07-15T00:00:00Z" }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'SEG-OR')]").exists())

        postJson("/coupons/eligibility", """{ "attributes": { "age": 25 }, "at": "2026-07-15T00:00:00Z" }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'SEG-OR')]").doesNotExist())
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
    fun `不正な入力は problem+json（400・422）`() {
        // couponId 空 → 400
        postJson(
            "/coupons",
            """{"couponId":"","distributionType":"ALL","validFrom":"2026-07-01T00:00:00Z","validTo":"2026-07-31T23:59:59Z"}""",
        ).andExpect(status().isBadRequest)

        // validTo < validFrom（ドメイン不変条件）→ 400 problem+json（detail に validTo）
        postJson(
            "/coupons",
            """{"couponId":"CPN-X","distributionType":"ALL","validFrom":"2026-07-31T00:00:00Z","validTo":"2026-07-01T00:00:00Z"}""",
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("validTo")))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))

        // 未知の演算子を含むイベント条件 → 422（適用せず DLQ 相当）、pointer と code
        postJson(
            "/internal/events",
            """
            {
              "eventId": "evt-bad", "type": "CouponApproved", "version": 1,
              "occurredAt": "2026-07-01T00:00:00Z",
              "data": {
                "couponId": "SEG-BAD", "distributionType": "SEGMENT", "status": "ACTIVE",
                "effectiveFrom": "2026-07-01T00:00:00Z", "effectiveTo": "2026-07-31T23:59:59Z",
                "condition": { "op": "between", "attr": "age", "value": 20 }
              }
            }
            """.trimIndent(),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("RULE_INVALID"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("op")))

        // スカラーでない属性値 → 400
        postJson("/coupons/eligibility", """{ "attributes": { "profile": { "age": 20 } } }""")
            .andExpect(status().isBadRequest)

        // 必須フィールド欠落（attributes なし）→ 400
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
    }

    @Test
    fun `廃止されたエンドポイントは 404（外部ルール登録・SMC DMP push）`() {
        postJson("/distribution-rules", "{}").andExpect(status().isNotFound)
        postJson("/segments/distribution", "{}").andExpect(status().isNotFound)
        // 参考: 選択更新は member モジュールへ移設済み（旧 /coupons/selection は無い）
        mockMvc.perform(put("/coupons/selection")).andExpect(status().isNotFound)
    }
}
