plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
}

group = "jp.co.sugipharmacy"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    // OpenSearch percolator アダプタ（coupon.percolator=opensearch のときのみ配線される）
    implementation("org.opensearch.client:opensearch-java:2.22.0")
    implementation("org.opensearch.client:opensearch-rest-client:2.19.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    // 依存方向の強制（eligibility 側 → member モジュールへの依存を禁止する）
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    // OpenSearch percolator の実機統合テスト（Docker が無ければ skip）
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.opensearch:opensearch-testcontainers:2.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// `./gradlew generateOpenApiDocs` — アプリを一時起動して docs/openapi.yaml を再生成する。
// 仕様の正はコード（アノテーション）。docs/openapi.yaml は生成物なので手で編集しない。
openApi {
    apiDocsUrl.set("http://localhost:8080/v3/api-docs.yaml")
    outputDir.set(layout.projectDirectory.dir("docs"))
    outputFileName.set("openapi.yaml")
}
