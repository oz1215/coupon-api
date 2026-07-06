package jp.co.sugipharmacy.coupon.infrastructure.percolator

import com.fasterxml.jackson.databind.ObjectMapper
import jp.co.sugipharmacy.coupon.application.port.PercolatorPort
import jp.co.sugipharmacy.coupon.domain.eligibility.MemberAttributes
import jp.co.sugipharmacy.coupon.domain.rule.Condition
import jp.co.sugipharmacy.coupon.domain.rule.DistributionRule
import org.opensearch.client.Request
import org.opensearch.client.ResponseException
import org.opensearch.client.RestClient
import org.opensearch.client.json.JsonData
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.Refresh
import org.opensearch.client.opensearch._types.query_dsl.Query
import org.opensearch.client.opensearch.core.IndexRequest
import org.opensearch.client.opensearch.core.SearchRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 本番規模想定の OpenSearch percolator 実装（`coupon.percolator=opensearch` でのみ配線）。
 *
 * - register: 条件ASTを OpenSearch クエリDSLへ変換（[ConditionQueryTranslator]）し、
 *   `{ couponId, validFrom, validTo, query }` の1ドキュメントとして percolator
 *   インデックスへ登録する。クエリが参照する属性フィールドは登録前に明示マッピング
 *   （num=double / txt=keyword）を投入する — percolator クエリは登録時に
 *   インデックスのマッピングで解釈されるため、未マッピングのままにしない。
 * - percolate: 属性1件を文書化した percolate クエリと、有効期間フィルタ
 *   （`validFrom <= at <= validTo`・両端含む）を bool.filter で合成した検索を
 *   1回発行し、ヒットした全ドキュメントの couponId 集合を返す。
 *   ルールのループ照合はしない（PercolatorPort の契約）。
 *
 * 文字列/数値の同一視・fail-safe の戦略は [ConditionQueryTranslator] の KDoc を参照。
 */
@Component
@ConditionalOnProperty(name = ["coupon.percolator"], havingValue = "opensearch")
class OpenSearchPercolatorAdapter(
    private val client: OpenSearchClient,
    private val lowLevelClient: RestClient,
    properties: OpenSearchProperties,
) : PercolatorPort {

    private val index = properties.index
    private val objectMapper = ObjectMapper()

    init {
        ensureIndex()
    }

    override fun register(rule: DistributionRule) {
        ensureAttributeFieldsMapped(rule.condition)
        val document = mapOf(
            COUPON_ID_FIELD to rule.couponId,
            VALID_FROM_FIELD to rule.validFrom.toString(),
            VALID_TO_FIELD to rule.validTo.toString(),
            QUERY_FIELD to ConditionQueryTranslator.translate(rule.condition),
        )
        val request = IndexRequest.Builder<Map<String, Any?>>()
            .index(index)
            .document(document)
            // InMemory と同じく登録直後から percolate に反映させる（登録は管理系操作で低頻度）。
            .refresh(Refresh.True)
            .build()
        client.index(request)
    }

    override fun percolate(attributes: MemberAttributes, at: Instant): Set<String> {
        val attributeDocument = attributes.toMap().mapValues { (_, scalar) ->
            buildMap<String, Any> {
                put(ConditionQueryTranslator.TEXT_SUBFIELD, scalar.asText())
                scalar.asNumber()?.let { put(ConditionQueryTranslator.NUMERIC_SUBFIELD, it) }
            }
        }
        val percolateDocument = mapOf(ConditionQueryTranslator.ATTRIBUTES_FIELD to attributeDocument)
        val atText = at.toString()
        val request = SearchRequest.Builder()
            .index(index)
            .size(MAX_MATCHED_RULES)
            .query { q ->
                q.bool { b ->
                    b.filter(
                        Query.of { f ->
                            f.percolate { p -> p.field(QUERY_FIELD).document(JsonData.of(percolateDocument)) }
                        },
                        Query.of { f -> f.range { r -> r.field(VALID_FROM_FIELD).lte(JsonData.of(atText)) } },
                        Query.of { f -> f.range { r -> r.field(VALID_TO_FIELD).gte(JsonData.of(atText)) } },
                    )
                }
            }
            .build()
        val response = client.search(request, Map::class.java)
        return response.hits().hits()
            .mapNotNull { it.source()?.get(COUPON_ID_FIELD) as? String }
            .toSet()
    }

    /** インデックスが無ければ percolator マッピング＋動的テンプレート付きで作成する（冪等）。 */
    private fun ensureIndex() {
        if (client.indices().exists { it.index(index) }.value()) return
        val request = Request("PUT", "/$index")
        request.setJsonEntity(INDEX_DEFINITION)
        try {
            lowLevelClient.performRequest(request)
        } catch (e: ResponseException) {
            // 並行起動で先に作られた場合のみ無害。それ以外は失敗させる。
            if (!e.message.orEmpty().contains("resource_already_exists_exception")) throw e
        }
    }

    /**
     * 条件が参照する属性フィールドの明示マッピングを投入する（冪等）。
     * サブフィールドの型は固定（num=double / txt=keyword）なので、
     * 同じキーを複数ルールが異なる値型で参照しても型衝突は起きない。
     */
    private fun ensureAttributeFieldsMapped(condition: Condition) {
        val fieldTypes = mutableMapOf<String, String>()
        collectFieldTypes(condition, fieldTypes)
        if (fieldTypes.isEmpty()) return
        val request = Request("PUT", "/$index/_mapping")
        request.setJsonEntity(objectMapper.writeValueAsString(nestedProperties(fieldTypes)))
        lowLevelClient.performRequest(request)
    }

    private fun collectFieldTypes(condition: Condition, into: MutableMap<String, String>) {
        when (condition) {
            is Condition.And -> condition.conditions.forEach { collectFieldTypes(it, into) }
            is Condition.Or -> condition.conditions.forEach { collectFieldTypes(it, into) }
            is Condition.Comparison -> when (condition.operator) {
                Condition.Operator.EQ, Condition.Operator.IN ->
                    condition.values.forEach { value ->
                        if (value.asNumber() != null) {
                            into[ConditionQueryTranslator.numericFieldPath(condition.key)] = "double"
                        } else {
                            into[ConditionQueryTranslator.textFieldPath(condition.key)] = "keyword"
                        }
                    }
                Condition.Operator.GTE, Condition.Operator.LTE,
                Condition.Operator.GT, Condition.Operator.LT,
                ->
                    if (condition.values.single().asNumber() != null) {
                        into[ConditionQueryTranslator.numericFieldPath(condition.key)] = "double"
                    }
                // 数値化不能な境界値は match_none に変換されるためマッピング不要。
            }
        }
    }

    /** ドット区切りのフィールドパス群を PUT _mapping 用のネスト構造へ展開する。 */
    private fun nestedProperties(fieldTypes: Map<String, String>): Map<String, Any?> {
        val root = mutableMapOf<String, Any?>()
        for ((path, type) in fieldTypes) {
            val parts = path.split(".")
            var current = root
            for ((i, part) in parts.withIndex()) {
                @Suppress("UNCHECKED_CAST")
                val properties =
                    current.getOrPut("properties") { mutableMapOf<String, Any?>() } as MutableMap<String, Any?>
                if (i == parts.lastIndex) {
                    properties[part] = mutableMapOf("type" to type)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    current = properties.getOrPut(part) { mutableMapOf<String, Any?>() } as MutableMap<String, Any?>
                }
            }
        }
        return root
    }

    companion object {
        private const val QUERY_FIELD = "query"
        private const val COUPON_ID_FIELD = "couponId"
        private const val VALID_FROM_FIELD = "validFrom"
        private const val VALID_TO_FIELD = "validTo"

        /** 1会員が同時に該当し得るルール数の上限（MVPでは十分大きい固定値）。 */
        private const val MAX_MATCHED_RULES = 10_000

        /**
         * percolator インデックス定義。
         * - query: percolator 型（条件ASTを変換したクエリDSLを保持）
         * - couponId: keyword / validFrom・validTo: date（有効期間フィルタ用）
         * - 動的テンプレート: ルールが参照しない属性が percolate 文書に来ても
         *   num は double・txt は keyword として一時マッピングされるようにする。
         *   （ルールが参照するフィールドは登録時に明示マッピングされる）
         */
        private val INDEX_DEFINITION = """
            {
              "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
              "mappings": {
                "dynamic_templates": [
                  {
                    "attribute_numbers": {
                      "path_match": "${ConditionQueryTranslator.ATTRIBUTES_FIELD}.*.${ConditionQueryTranslator.NUMERIC_SUBFIELD}",
                      "mapping": { "type": "double" }
                    }
                  },
                  {
                    "attribute_texts": {
                      "path_match": "${ConditionQueryTranslator.ATTRIBUTES_FIELD}.*.${ConditionQueryTranslator.TEXT_SUBFIELD}",
                      "mapping": { "type": "keyword" }
                    }
                  }
                ],
                "properties": {
                  "$QUERY_FIELD": { "type": "percolator" },
                  "$COUPON_ID_FIELD": { "type": "keyword" },
                  "$VALID_FROM_FIELD": { "type": "date" },
                  "$VALID_TO_FIELD": { "type": "date" }
                }
              }
            }
        """.trimIndent()
    }
}
