package jp.co.sugipharmacy.coupon.infrastructure.percolator

import org.apache.http.HttpHost
import org.opensearch.client.RestClient
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.transport.rest_client.RestClientTransport
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenSearch percolator の接続設定（`coupon.opensearch.*`）。
 * `coupon.percolator=opensearch` のときのみ束縛・使用される。
 */
@ConfigurationProperties(prefix = "coupon.opensearch")
data class OpenSearchProperties(
    val host: String = "localhost",
    val port: Int = 9200,
    val scheme: String = "http",
    /** percolator インデックス名。 */
    val index: String = "distribution-rules",
)

/**
 * OpenSearch クライアントの配線。`coupon.percolator=opensearch` のときのみ
 * Bean が生成される — 既定（in-memory）では OpenSearch は一切不要。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["coupon.percolator"], havingValue = "opensearch")
@EnableConfigurationProperties(OpenSearchProperties::class)
class OpenSearchPercolatorConfig {

    /** 低レベルクライアント。インデックス作成・マッピング更新の生JSON送信にも使う。 */
    @Bean(destroyMethod = "close")
    fun openSearchRestClient(properties: OpenSearchProperties): RestClient =
        RestClient.builder(HttpHost(properties.host, properties.port, properties.scheme)).build()

    @Suppress("DEPRECATION") // RestClientTransport は非推奨だが opensearch-rest-client との併用構成として採用
    @Bean
    fun openSearchClient(restClient: RestClient): OpenSearchClient =
        OpenSearchClient(RestClientTransport(restClient, JacksonJsonpMapper()))
}
