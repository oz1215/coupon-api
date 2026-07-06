package jp.co.sugipharmacy.coupon.infrastructure.percolator

import jp.co.sugipharmacy.coupon.domain.Scalar
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import jp.co.sugipharmacy.coupon.domain.rule.Condition
import jp.co.sugipharmacy.coupon.domain.rule.DistributionRule
import org.apache.http.HttpHost
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opensearch.client.RestClient
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.transport.rest_client.RestClientTransport
import org.opensearch.testcontainers.OpensearchContainer
import org.testcontainers.DockerClientFactory
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * OpenSearch percolator 実装の実機統合テスト（Testcontainers）。
 * Docker が無い環境では skip され、既定の `./gradlew build` を壊さない。
 *
 * 中核は InMemory との**同値性（parity）**: 同じルール集合＋同じ属性ベクトルを
 * 両実装へ流し、返る couponId 集合が完全一致することを確認する。
 *
 * colima + Docker Engine 29 で実行する場合の注意（Docker Desktop なら不要）:
 * Testcontainers 1.21 は API バージョン未指定時に 1.32 を送り、Engine 29
 * （最小 1.40）に拒否されて本テストが skip になる。次の環境変数で実行する。
 * ```
 * DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
 * TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
 * api.version=1.44 ./gradlew test
 * ```
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenSearchPercolatorAdapterIntegrationTest {

    private lateinit var container: OpensearchContainer<*>
    private lateinit var restClient: RestClient
    private lateinit var client: OpenSearchClient
    private val indexCounter = AtomicInteger()

    private val from = Instant.parse("2026-07-01T00:00:00Z")
    private val to = Instant.parse("2026-07-31T23:59:59Z")
    private val at = Instant.parse("2026-07-15T12:00:00Z")

    @BeforeAll
    fun startOpenSearch() {
        assumeTrue(
            DockerClientFactory.instance().isDockerAvailable,
            "Docker が利用できないため OpenSearch 統合テストを skip",
        )
        container = OpensearchContainer<Nothing>(DockerImageName.parse("opensearchproject/opensearch:2.19.1"))
        container.start()
        restClient = RestClient.builder(HttpHost.create(container.httpHostAddress)).build()
        @Suppress("DEPRECATION")
        client = OpenSearchClient(RestClientTransport(restClient, JacksonJsonpMapper()))
    }

    @AfterAll
    fun stopOpenSearch() {
        if (::restClient.isInitialized) restClient.close()
        if (::container.isInitialized) container.stop()
    }

    /** テストごとに独立インデックスの新しいアダプタを作る（登録済みルールの汚染を避ける）。 */
    private fun newAdapter(): OpenSearchPercolatorAdapter {
        val properties = OpenSearchProperties(index = "distribution-rules-it-${indexCounter.incrementAndGet()}")
        return OpenSearchPercolatorAdapter(client, restClient, properties)
    }

    private fun scalars(vararg values: Any) = values.map { Scalar.of(it)!! }

    private fun comparison(key: String, op: Condition.Operator, vararg values: Any) =
        Condition.Comparison(key, op, scalars(*values))

    private fun rule(couponId: String, condition: Condition, validFrom: Instant = from, validTo: Instant = to) =
        DistributionRule(couponId, condition, validFrom, validTo)

    // ---- 代表ルール集合（eq / in / range / and / or / or-of-and / fail-safe） ----

    private fun representativeRules() = listOf(
        rule("CPN-EQ-STR", comparison("gender", Condition.Operator.EQ, "female")),
        // 条件値が文字列 "20" — 数値 20 の属性とも一致しなければならない
        rule("CPN-EQ-NUMTXT", comparison("age", Condition.Operator.EQ, "20")),
        rule("CPN-EQ-BOOL", comparison("appUser", Condition.Operator.EQ, true)),
        rule("CPN-GTE20", comparison("age", Condition.Operator.GTE, 20)),
        rule("CPN-LTE65", comparison("age", Condition.Operator.LTE, 65)),
        rule("CPN-GT20", comparison("age", Condition.Operator.GT, 20)),
        rule("CPN-LT20", comparison("age", Condition.Operator.LT, 20)),
        // in — 数値化できる店舗コードと純文字列コードの混在
        rule("CPN-IN", comparison("store", Condition.Operator.IN, "001", "002", "A03")),
        rule(
            "CPN-AND",
            Condition.And(
                listOf(
                    comparison("age", Condition.Operator.GTE, 20),
                    comparison("gender", Condition.Operator.EQ, "female"),
                ),
            ),
        ),
        rule(
            "CPN-OR",
            Condition.Or(
                listOf(
                    comparison("age", Condition.Operator.GTE, 60),
                    comparison("appUser", Condition.Operator.EQ, true),
                ),
            ),
        ),
        rule(
            "CPN-OR-OF-AND",
            Condition.Or(
                listOf(
                    Condition.And(
                        listOf(
                            comparison("age", Condition.Operator.GTE, 20),
                            comparison("age", Condition.Operator.LTE, 29),
                            comparison("gender", Condition.Operator.EQ, "male"),
                        ),
                    ),
                    Condition.And(
                        listOf(
                            comparison("age", Condition.Operator.GTE, 30),
                            comparison("gender", Condition.Operator.EQ, "female"),
                        ),
                    ),
                ),
            ),
        ),
        // fail-safe: 数値化できない境界値の range は決して該当しない
        rule("CPN-BAD-RANGE", comparison("age", Condition.Operator.GTE, "abc")),
    )

    /** 属性ベクトル（境界値 19/20/21・型ゆらぎ・属性欠落・数値化不能を含む）。 */
    private fun attributeVectors(): List<Map<String, Any?>> = listOf(
        mapOf("age" to 19, "gender" to "female", "store" to "001", "appUser" to false),
        mapOf("age" to 20, "gender" to "female", "store" to "A03", "appUser" to true),
        mapOf("age" to 21, "gender" to "male", "store" to "003", "appUser" to false),
        // 属性側が文字列の数値（"20" と 20 の同一視 — 双方向）
        mapOf("age" to "20", "gender" to "male", "appUser" to "true"),
        // 小数表現ゆらぎ（20.0 == 20）と店舗コードの数値同一視（"2" == "002"）
        mapOf("age" to 20.0, "gender" to "female", "store" to "2"),
        mapOf("age" to 29, "gender" to "male", "appUser" to true),
        mapOf("age" to 30, "gender" to "female"),
        mapOf("age" to 60, "gender" to "male"),
        mapOf("age" to 65, "gender" to "other", "store" to "002"),
        mapOf("age" to 66, "gender" to "female"),
        // 属性欠落（age なし）— range 系・and は非該当に倒れる
        mapOf("gender" to "female", "store" to "001"),
        // 数値化できない age — 数値比較は非該当、eq は文字列比較
        mapOf("age" to "abc", "gender" to "female", "appUser" to true),
        // 真偽値属性と文字列 "false"
        mapOf("age" to 25, "appUser" to false, "gender" to "unknown"),
        mapOf(),
    )

    @Test
    fun `代表ルール集合に対する percolate 結果が期待どおり`() {
        val adapter = newAdapter()
        representativeRules().forEach(adapter::register)

        val hits = adapter.percolate(
            MemberAttributes.from(mapOf("age" to 20, "gender" to "female", "store" to "A03", "appUser" to true)),
            at,
        )

        assertThat(hits).containsExactlyInAnyOrder(
            "CPN-EQ-STR", // gender eq female
            "CPN-EQ-NUMTXT", // age 20 == "20"
            "CPN-EQ-BOOL", // appUser true
            "CPN-GTE20", "CPN-LTE65", // 境界を含む
            "CPN-IN", // store A03
            "CPN-AND", // 20歳以上かつ female
            "CPN-OR", // appUser true
        )
    }

    @Test
    fun `parity - 同じルールと属性ベクトルで InMemory と完全一致する`() {
        val inMemory = InMemoryPercolatorAdapter()
        val openSearch = newAdapter()
        representativeRules().forEach {
            inMemory.register(it)
            openSearch.register(it)
        }

        for (vector in attributeVectors()) {
            val attributes = MemberAttributes.from(vector)
            val expected = inMemory.percolate(attributes, at)
            val actual = openSearch.percolate(attributes, at)
            assertThat(actual)
                .describedAs("attributes=%s", vector)
                .isEqualTo(expected)
        }
    }

    @Test
    fun `parity - 有効期間フィルタは両端を含み InMemory と一致する`() {
        val inMemory = InMemoryPercolatorAdapter()
        val openSearch = newAdapter()
        val rules = listOf(
            rule("CPN-JULY", comparison("age", Condition.Operator.GTE, 20), from, to),
            rule(
                "CPN-AUGUST",
                comparison("age", Condition.Operator.GTE, 20),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T23:59:59Z"),
            ),
        )
        rules.forEach {
            inMemory.register(it)
            openSearch.register(it)
        }
        val attributes = MemberAttributes.from(mapOf("age" to 30))

        val instants = listOf(from.minusSeconds(1), from, at, to, to.plusSeconds(1), Instant.parse("2026-08-15T00:00:00Z"))
        for (instant in instants) {
            val expected = inMemory.percolate(attributes, instant)
            val actual = openSearch.percolate(attributes, instant)
            assertThat(actual).describedAs("at=%s", instant).isEqualTo(expected)
        }

        // 具体値の確認（両端を含む・期間外は空）
        assertThat(openSearch.percolate(attributes, from)).containsExactly("CPN-JULY")
        assertThat(openSearch.percolate(attributes, to)).containsExactly("CPN-JULY")
        assertThat(openSearch.percolate(attributes, from.minusSeconds(1))).isEmpty()
        assertThat(openSearch.percolate(attributes, Instant.parse("2026-08-15T00:00:00Z")))
            .containsExactly("CPN-AUGUST")
    }
}
