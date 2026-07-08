package jp.co.sugipharmacy.coupon.interfaces.rest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** /internal/events（イベント取り込みの継ぎ目）越しの冪等・投影・resync を確認する。 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InternalEventsIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun postJson(path: String, body: String) =
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun approvedEvent(couponId: String, eventId: String, version: Int) = """
        {
          "eventId": "$eventId", "type": "CouponApproved", "version": $version,
          "occurredAt": "2026-07-01T00:00:00Z",
          "data": {
            "couponId": "$couponId", "schemaVersion": "1.0",
            "distributionType": "SEGMENT", "status": "ACTIVE",
            "effectiveFrom": "2026-07-01T00:00:00Z", "effectiveTo": "2026-07-31T23:59:59Z",
            "condition": { "op": "eq", "attr": "age_band", "value": "20s" }
          }
        }
    """.trimIndent()

    @Test
    fun `イベント受領で投影され eligibility に反映、再配信は冪等`() {
        postJson("/internal/events", approvedEvent("SEG-1", "e1", 1))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.outcome").value("APPLIED"))

        // 同一 eventId → DUPLICATE
        postJson("/internal/events", approvedEvent("SEG-1", "e1", 1))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.outcome").value("DUPLICATE"))

        postJson("/coupons/eligibility", """{ "attributes": { "age_band": "20s" }, "at": "2026-07-15T00:00:00Z" }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'SEG-1')]").exists())
    }

    @Test
    fun `CouponSuspended で停止一覧に載る`() {
        postJson("/internal/events", approvedEvent("SEG-1", "e1", 1)).andExpect(status().isAccepted)
        postJson(
            "/internal/events",
            """
            {
              "eventId": "e2", "type": "CouponSuspended", "version": 2,
              "occurredAt": "2026-07-02T00:00:00Z",
              "data": {
                "couponId": "SEG-1", "distributionType": "SEGMENT", "status": "SUSPENDED",
                "effectiveFrom": "2026-07-01T00:00:00Z", "effectiveTo": "2026-07-31T23:59:59Z",
                "condition": { "op": "eq", "attr": "age_band", "value": "20s" }
              }
            }
            """.trimIndent(),
        ).andExpect(status().isAccepted)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/coupons/suspended"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.couponIds[0]").value("SEG-1"))
    }

    @Test
    fun `全件resync でスナップショットから読みモデルを作り直す`() {
        // 先に別クーポンを入れておく → resync で消えるべき
        postJson("/internal/events", approvedEvent("OLD-1", "old", 1)).andExpect(status().isAccepted)

        postJson(
            "/internal/events/resync",
            """{ "events": [ ${approvedEvent("SEG-2", "s1", 1)} ] }""",
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.outcome").value("RESYNCED"))

        postJson("/coupons/eligibility", """{ "attributes": { "age_band": "20s" }, "at": "2026-07-15T00:00:00Z" }""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'SEG-2')]").exists())
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'OLD-1')]").doesNotExist())
    }
}
