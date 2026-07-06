# coupon-api 設計メモ（MVP）

NCP-2892 クーポン基盤刷新の**実行時API面**。README の設計方針を正とし、その3つの中核概念をコードへ写像する。
スタックは Kotlin + Spring Boot 3（JDK 21 / Gradle Kotlin DSL）。

## 概念 → コードの対応

| 概念 | 実装箇所 |
| --- | --- |
| 逆引き該当判定（percolator）: 条件を事前保存し、属性1件で該当クーポン集合を返す | `application/port/PercolatorPort.kt`（契約）/ `domain/rule/`（条件AST・パーサ・評価）/ `infrastructure/percolator/` |
| member-agnostic: 会員属性を保持せず、会員×クーポンの事前紐づけを作らない | `domain/eligibility/MemberAttributes.kt` — リクエスト毎の値オブジェクト。リポジトリ・永続化は存在しない |
| 差し引きによる即時反映: 停止中はキャッシュに焼き込まず、BFFが表示直前に差し引く | `GET /coupons/suspended`（`CouponService.getSuspendedCouponIds`）。eligibility 結果から SUSPENDED を**除外しない**のは意図的 — 除外するとBFFキャッシュのTTLに縛られ即時停止できない |

パッケージルートは `jp.co.sugipharmacy.coupon`（`src/main/kotlin/` 配下）。

## PercolatorPort（型は一つ、実行は複数）

- 契約: `register(rule)` で配布ルールを事前登録、`percolate(attributes, at)` で属性1件→該当クーポンID集合。**ルールをループして外部システムへ照合する実装は契約違反。**
- `InMemoryPercolatorAdapter`（既定・`coupon.percolator=in-memory`, matchIfMissing）: インフラゼロで動く。プロセス内線形走査だが外部問い合わせは無く、MVPの検証・テストに十分。
- `OpenSearchPercolatorAdapter`（スケルトン・`coupon.percolator=opensearch` でのみ配線）: 本番規模の逆引きインデックス。条件AST→bool/term/terms/rangeクエリの変換方針をコード内に明記。メソッドは実装完了まで TODO で失敗する。ビルド・テストに OpenSearch は不要。

## 条件モデル

- 単一の sealed AST（`Condition` = `Comparison` | `And` | `Or`）に正規化してから評価する（`ConditionParser`）。
  - 簡易形（Confluence仕様）`{ "rules": [ {"key","value"} ... ] }` → **eq の OR** として解釈。
  - リッチ形 `{ "and"/"or": [...] }` + 比較演算子 `eq / gte / lte / gt / lt / in` → 範囲条件（◯歳以上）と AND/OR ネストを表現。
- 評価は誤配布より配布漏れに倒す: 属性欠落・数値化不能は「非該当」。
- 値は `Scalar`（Text/Num/Bool）。数値化できる値同士は `BigDecimal` で数値比較（`"20"` と `20` を同一視 — 属性ソースにより数値が文字列で届くため）。

## eligibility の合成規則

`POST /coupons/eligibility` = **percolate該当のSEGMENT（クーポン有効期間内）∪ 有効期間内のALL**。有効期間は両端を含む。

- INDIVIDUAL は属性では決まらないため対象外（ウェルカム/イベント登録APIの領分・スタブ）。
- 消費済み・選択状態・SUSPENDED の差し引きは行わない（BFFの責務）。
- 返すのはクーポンID＋distributionType のみ。詳細・画像・並び順の整形はBFF/CMS側。
- 判定基準時刻は任意の `at`（省略時はサーバ現在時刻）。

## レイヤ規約

`domain`（Spring・インフラ非依存の純Kotlin）→ `application`（ユースケース＋ポート定義）→ `infrastructure`（アダプタ）/ `interfaces`（RESTコントローラ・DTO Bean Validation・エラー写像）。ドメインの `DomainValidationError` は `interfaces/rest/ApiExceptionHandler` が 400 に写像する。ドメインは HTTP を知らない。

## member-coupon-state（内包モジュール）

会員キーの状態（選択・お気に入り・消費済み、および付与・ID統合のスタブ）は、coupon-api **内の別境界づけられたコンテキスト** `member` パッケージ（`jp.co.sugipharmacy.coupon.member` — 配下に自前の `domain / application / infrastructure / interfaces/rest`）として持つ。別サービスには**しない** — 物理分割は運用上の力学（スケール・チーム分割・デプロイ独立の必要）が実証されるまで先送りする。境界はパッケージとテストで守れるため、いま分割しても分散化のコストだけが先行する。

member-agnostic な該当判定エンジンを毀損しないためのガードレールは3つ。

1. **別モジュール・別データ所有。** `member` は自前のストア（`InMemoryMemberCouponStateRepository` — (memberId, couponId) キー）だけを読み書きし、クーポンマスタ・配布ルールのリポジトリへは書かない。保持するのは会員の**操作状態のみ** — 会員属性は持たず（プロファイルストアの領分）、「配布された」という事前紐づけレコードも作らない。
2. **依存は一方向（member → eligibility のみ）。** eligibility 側（`domain/` `application/` `infrastructure/` `interfaces/`）から `member` パッケージへの import を禁止する。`ArchitectureTest`（ArchUnit）がビルド毎に強制する（ドメイン純度も同テストで強制）。
3. **差し引きは合成層でのみ行い、純粋な eligibility を残す。** `POST /coupons/eligibility` は差し引き前の生集合（該当SEGMENT ∪ ALL）を返し続ける。表示一覧 = eligibility − 消費済み − SUSPENDED の合成は `member` の `MemberCouponListService`（`POST /members/{memberId}/coupon-list`）だけが行う。属性はリクエストで受け取る（BFF がプロファイルストアから渡す）ため、合成エンドポイントでも member-agnostic は保たれる。

## APIリファレンス（生成物・手書き禁止）

エンドポイントの正はコード（コントローラ・DTO の OpenAPI アノテーション）であり、散文の仕様書は別途維持しない。

- 起動中: [Swagger UI](http://localhost:8080/swagger-ui.html) / `GET /v3/api-docs`（JSON）・`GET /v3/api-docs.yaml`
- 静的: [`docs/openapi.yaml`](./openapi.yaml) — `./gradlew generateOpenApiDocs` で再生成する（アプリを一時起動して出力）。手で編集しない。

## MVP境界（スタブ = 501）

- クーポン読み取りモデル・外部連携（クーポン詳細/全件取得・PIT店舗詳細・在庫クーポンID一覧・CMS存在確認・Smooth登録可否・SMC取得・セグメント配布API）: `interfaces/rest/StubsController.kt`。
- 会員ライフサイクル（ウェルカム/イベント登録・ID統合の移行/統合/退会削除）: 会員キー状態を書き換える操作なので `member/interfaces/rest/MemberCouponStubsController.kt` が持つ（将来の実装先＝member モジュール）。
- 選択更新・お気に入り更新・POS選択取得/利用更新はスタブを卒業し、`member` モジュールの実装済みエンドポイント（`/members/{memberId}/...`）になった。

## 対象外（本リポジトリでは作らない）

- ユーザプロファイルストア（会員属性の保管）— 別プロダクト。
- BFF側 eligibility キャッシュ。BFF 経路での表示直前差し引きは引き続き BFF の責務（本体の合成エンドポイントは member モジュール側にのみ存在する）。
- OpenSearch の実配線・接続設定、移行・並行稼働バッチ。
- 管理コンソール（登録・編集・承認・テンプレート・CSV）— `coupon-admin`。
