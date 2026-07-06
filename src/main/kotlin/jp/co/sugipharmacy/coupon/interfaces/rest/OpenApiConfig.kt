package jp.co.sugipharmacy.coupon.interfaces.rest

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI の info ブロック。仕様の正はコード側のアノテーションであり、
 * `/v3/api-docs(.yaml)` と `docs/openapi.yaml` はそこから生成される。
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun couponApiOpenApi(
        @Value("\${spring.application.version:0.1.0}") version: String,
    ): OpenAPI = OpenAPI().info(
        Info()
            .title("coupon-api")
            .description(
                "クーポン基盤の実行時統合API。会員×クーポンの事前紐づけを持たず、" +
                    "会員属性を受け取って該当クーポンを判定・返却する。",
            )
            .version(version),
    )
}
