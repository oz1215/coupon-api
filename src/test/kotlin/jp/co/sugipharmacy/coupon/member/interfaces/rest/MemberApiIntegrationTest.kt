package jp.co.sugipharmacy.coupon.member.interfaces.rest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * member-coupon-state モジュールのエンドポイント越しの一気通貫と、
 * ガードレール#3（純粋な eligibility の維持）の確認:
 * POST /coupons/eligibility は差し引き前の生集合を返し続け、
 * 差し引きは POST /members/{memberId}/coupon-list（合成層）でのみ行われる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MemberApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun postJson(path: String, body: String) =
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun putJson(path: String, body: String) =
        mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun registerCoupon(couponId: String, distributionType: String = "ALL", status: String = "ACTIVE") {
        postJson(
            "/coupons",
            """
            {
              "couponId": "$couponId",
              "distributionType": "$distributionType",
              "validFrom": "2026-07-01T00:00:00Z",
              "validTo": "2026-07-31T23:59:59Z",
              "status": "$status"
            }
            """.trimIndent(),
        ).andExpect(status().isCreated)
    }

    private val listBody = """{ "attributes": { "age": 25 }, "at": "2026-07-15T00:00:00Z" }"""

    @Test
    fun `表示一覧の合成 - eligibility の生集合から消費済みと停止中が差し引かれる`() {
        registerCoupon("ALL-KEEP")
        registerCoupon("ALL-USED")
        registerCoupon("ALL-SUS", status = "SUSPENDED")
        registerCoupon("SEG-20S", distributionType = "SEGMENT")
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

        // 消費前: eligibility（生集合）にも member の表示一覧にも4件とも出る
        postJson("/members/M-1/coupon-list", listBody)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons.length()").value(3)) // SUSPENDED だけは最初から差し引かれる
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-SUS')]").doesNotExist())

        // M-1 が ALL-USED を利用（POS）
        postJson("/members/M-1/coupons/usage", """{ "couponIds": ["ALL-USED"] }""")
            .andExpect(status().isNoContent)

        // ガードレール#3: eligibility は差し引きなしの生集合のまま（4件・消費済みも停止中も含む）
        postJson("/coupons/eligibility", listBody)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons.length()").value(4))
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-USED')]").exists())
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-SUS')]").exists())

        // 合成層: 消費済み・停止中が差し引かれ、残りだけが表示対象
        postJson("/members/M-1/coupon-list", listBody)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons.length()").value(2))
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-KEEP')]").exists())
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'SEG-20S')].distributionType").value("SEGMENT"))
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-USED')]").doesNotExist())
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-SUS')]").doesNotExist())

        // 会員分離: 別会員 M-2 の表示一覧では ALL-USED は残る
        postJson("/members/M-2/coupon-list", listBody)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coupons[?(@.couponId == 'ALL-USED')]").exists())
    }

    @Test
    fun `選択の更新と取得（POS照会）`() {
        putJson("/members/M-1/coupons/CP-1/selection", """{ "selected": true }""")
            .andExpect(status().isNoContent)
        putJson("/members/M-1/coupons/CP-2/selection", """{ "selected": true }""")
            .andExpect(status().isNoContent)
        putJson("/members/M-1/coupons/CP-2/selection", """{ "selected": false }""")
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/members/M-1/coupons/selected"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.couponIds.length()").value(1))
            .andExpect(jsonPath("$.couponIds[0]").value("CP-1"))

        // 別会員には影響しない
        mockMvc.perform(get("/members/M-2/coupons/selected"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.couponIds.length()").value(0))
    }

    @Test
    fun `お気に入りの更新は204`() {
        putJson("/members/M-1/coupons/CP-1/favorite", """{ "favorite": true }""")
            .andExpect(status().isNoContent)
    }

    @Test
    fun `不正な入力は400`() {
        // selected 欠落
        putJson("/members/M-1/coupons/CP-1/selection", "{}").andExpect(status().isBadRequest)
        // couponIds 空
        postJson("/members/M-1/coupons/usage", """{ "couponIds": [] }""").andExpect(status().isBadRequest)
        // attributes 欠落
        postJson("/members/M-1/coupon-list", """{ "at": "2026-07-15T00:00:00Z" }""").andExpect(status().isBadRequest)
    }

    @Test
    fun `会員ライフサイクル操作（付与・ID統合）はMVP対象外で501`() {
        postJson("/members/M-1/coupons/welcome", "{}").andExpect(status().isNotImplemented)
        postJson("/members/M-1/coupons/event", "{}").andExpect(status().isNotImplemented)
        putJson("/members/coupons/transfer", "{}").andExpect(status().isNotImplemented)
        putJson("/members/coupons/migrate", "{}").andExpect(status().isNotImplemented)
        mockMvc.perform(delete("/members/M-1/coupons")).andExpect(status().isNotImplemented)
    }

    @Test
    fun `旧グローバルスタブの会員キー系パスは撤去済み（404）`() {
        putJson("/coupons/selection", "{}").andExpect(status().isNotFound)
        putJson("/coupons/favorite", "{}").andExpect(status().isNotFound)
        postJson("/coupons/welcome", "{}").andExpect(status().isNotFound)
        postJson("/coupons/event", "{}").andExpect(status().isNotFound)
        mockMvc.perform(get("/pos/coupons/selected")).andExpect(status().isNotFound)
        putJson("/pos/coupons/usage", "{}").andExpect(status().isNotFound)
    }
}
