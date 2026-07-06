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
- `OpenSearchPercolatorAdapter`（`coupon.percolator=opensearch` でのみ配線）: 本番規模の逆引きインデックス実装。詳細は下記「OpenSearch percolator 実装」。既定のビルド・テストに OpenSearch は不要（統合テストは Docker が無ければ skip）。

## OpenSearch percolator 実装

`coupon.percolator=opensearch` で有効化。接続は `coupon.opensearch.*`（host/port/scheme/index — 既定 localhost:9200/http/`distribution-rules`）。クライアントは `opensearch-java` + 低レベル `opensearch-rest-client`（`OpenSearchPercolatorConfig` — Bean もプロパティ束縛もこのモードでのみ生成）。手元検証は同梱の compose で:

```
docker compose up -d
./gradlew bootRun --args='--coupon.percolator=opensearch'
```

### インデックス（`distribution-rules`）

1ルール = 1ドキュメント `{ couponId, validFrom, validTo, query }`。

| フィールド | 型 | 役割 |
| --- | --- | --- |
| `query` | percolator | 条件ASTを変換したクエリDSL（逆引きの本体） |
| `couponId` | keyword | ヒット結果から回収する返却値 |
| `validFrom` / `validTo` | date | 有効期間フィルタ（percolate 検索側の bool.filter で `validFrom <= at <= validTo`・両端含む） |
| `attrs.<key>.num` | double | 属性のうち数値化できる値。動的テンプレート `attrs.*.num` + 登録時の明示 PUT _mapping |
| `attrs.<key>.txt` | keyword | 属性の文字列表現（常に格納）。動的テンプレート `attrs.*.txt` + 同上 |

percolator クエリは**登録時**にインデックスのマッピングで解釈されるため、ルールが参照するフィールドは登録前に明示マッピングを冪等 PUT する（動的テンプレートは「ルールが参照しない属性」が percolate 文書に来たときの保険）。

### 条件AST → クエリDSL（`ConditionQueryTranslator`）

| AST | クエリ |
| --- | --- |
| `Comparison(eq)` | `term`（値が数値化可能なら `attrs.<key>.num`、不能なら `attrs.<key>.txt`） |
| `Comparison(in)` | `terms`（数値系/文字列系に振り分け。混在時は `bool.should` + msm=1 で束ねる） |
| `Comparison(gte/lte/gt/lt)` | `range`（`attrs.<key>.num`）。境界値が数値化不能なら `match_none` |
| `And` | `bool.filter`（再帰） |
| `Or` | `bool.should` + `minimum_should_match: 1`（再帰） |

**文字列/数値の同一視**（`"20"` と `20`）: `Scalar.looselyEquals` は「双方が数値化できるなら数値比較、それ以外は文字列比較」。OpenSearch は1フィールド1型なので、属性1つを `num`（double・数値化できる場合のみ）と `txt`（keyword・常時）の2サブフィールドへ写し、条件値も同じ規則で振り分ける。文字列表現が等しいのに数値化可能性が食い違う組は存在しないため、この2面戦略は looselyEquals と同じ結果を与える（`20.0` と `"20"` の一致も num 側で成立）。

**fail-safe（誤配布より配布漏れ）の再現**: 属性欠落・数値化不能な属性 → 該当サブフィールドが percolate 文書に無く不一致。数値化不能な条件境界値 → `match_none`。いずれも InMemory の「非該当」と同じに倒れる。

**InMemory との既知の差（実用上無視できる範囲）**: (1) 数値比較が BigDecimal（任意精度）ではなく double — 2^53 超の整数や極端な精度の小数は丸まり得る。(2) `validFrom/validTo` と `at` は date 型（ミリ秒精度）— ナノ秒単位の境界は丸まる。(3) 1回の percolate が返すルール数は上限 10,000（`size` 固定・MVPでは十分）。

**同値性の担保**: `OpenSearchPercolatorAdapterIntegrationTest`（Testcontainers・要 Docker、無ければ skip）が同じルール集合＋同じ属性ベクトル（eq・境界値 19/20/21 を含む range・in・and・or・or-of-and・属性欠落・数値化不能・有効期間の両端）を両実装へ流し、結果集合の完全一致を検証する。変換の構造自体は `ConditionQueryTranslatorTest`（Docker 不要・常時実行）が固定する。

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
- OpenSearch の本番クラスタ構成（認証・TLS・シャーディング設計）、移行・並行稼働バッチ。実配線と接続設定自体は実装済み（上記「OpenSearch percolator 実装」）。
- 管理コンソール（登録・編集・承認・テンプレート・CSV）— `coupon-admin`。
