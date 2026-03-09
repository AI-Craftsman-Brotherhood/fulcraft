# セキュリティと脆弱性スキャン (Security Scanning)

FUL は「Shift-Left」アプローチを採用し、開発ライフサイクルの各段階で自動化されたセキュリティチェックを実施しています。また、実行時においても機密情報の保護機能を提供します。

## 1. CI における自動スキャン

GitHub Actions を利用した包括的な検査パイプラインを構築しています。

### SCA (Software Composition Analysis) - 依存関係チェック

OSS 依存関係に含まれる既知の脆弱性 (CVE) を検出します。

- **OWASP Dependency-Check**:
  - 実行タイミング: 週次 (`dependency-check.yml`)、および手動トリガー
  - 動作: 依存ライブラリを NVD (National Vulnerability Database) と照合
  - 設定: CVSS スコア 8.0 以上で警告/失敗
- **Dependabot**:
  - 実行タイミング: 週次
  - 動作: Gradle / GitHub Actions 依存関係の自動更新 PR を作成
  - **Dependency Review**: PR 作成時に、変更された依存関係の高リスク検知をブロック

### Secret Scanning (Prevention) - 機密情報の混入防止

ローカルの pre-commit secret scanning 設定は現在このリポジトリには同梱していません。
必要な場合は、各開発環境・組織ポリシーに合わせて `git-secrets` / `detect-secrets` などを導入してください。

### SAST (Static Application Security Testing) - 静的セキュリティ解析

ソースコード自体を解析し、潜在的なセキュリティバグや脆弱なパターンを検出します。

- **GitHub CodeQL**:
  - 対象言語: Java
  - 分類: インジェクション、安全でないデシリアライズ、ハードコードされた認証情報など
  - レポート: GitHub Security タブに統合
  - 実行: `.github/workflows/ci.yml` の `codeql` ジョブで実行
- **SpotBugs**:
  - 検出: 潜在的なバグ、リソースリーク、NullPointer系の問題（セキュリティバグ以外も含む）

### コード品質と衛生管理 (Code Hygiene)

セキュリティの基盤となるコード品質を維持するためのチェックです。

- **Spotless**: コードフォーマットの強制（可読性の確保）
- **Checkstyle**: コーディング規約の遵守
- **PMD**: 不要なコード、非効率な実装の検出

---

## 2. 実行時の保護機構

FUL 自身の実行時にも、ユーザーの機密情報を保護するための機構が組み込まれています。
LLM 送信ポリシーやプロンプトレダクションの詳細は [governance.md](governance.md) を参照してください。

### 機密情報のマスキング (Secret Masking)

`com.craftsmanbro.fulcraft.infrastructure.security.SecretMasker` ユーティリティにより、ログ出力やコンソール表示に含まれる機密情報を自動的に `****` に置換します。

**対象パターン:**
- **API キー / トークン**: `x-api-key`, `Authorization: Bearer ...`
- **秘密鍵**: PEM 形式 (`-----BEGIN PRIVATE KEY-----`)
- **JWT (JSON Web Tokens)**: ドット区切りの長い Base64 文字列検出
- **長いランダム文字列**: エントロピーの高いトークンらしき文字列

補足:
- ログ出力は `MaskedPatternLayout` を通じてマスキングされます。
- CLI のエラーメッセージ表示でも `SecretMasker` が適用されます。

### 機密ロジックの検出 (Sensitive Logic Detection)

`com.craftsmanbro.fulcraft.kernel.pipeline.interceptor.SecurityFilterInterceptor` は選定フェーズ (`SELECT`) の PRE フックで動作します。`SELECT` は `generate` ターゲットとして workflow ノード（例: `junit-select`）内で実行されるため、実行タイミングはプラグイン内の `SelectStage` 呼び出し前になります。

- **目的**: 認証・認可・暗号化などに関わるメソッドに対して、不用意にテスト（特に外部LLMへのコード送信）を行わないよう警告します。
- **検出ロジック**:
  - メソッド名: `password`, `secret`, `token`, `credential`, `encrypt`, `decrypt`, `authenticate`, `authorize`, `apikey`, `privatekey`, `signin`, `signout`, `login`, `logout` などを含むもの
  - アノテーション: `@PreAuthorize`, `@PostAuthorize`, `@Secured`, `@RolesAllowed`, `@PermitAll`, `@DenyAll`, `@Encrypt`, `@Decrypt`
- **動作**:
  - 検出結果は `security.sensitive.method_ids` として `RunContext` に記録されます。
  - SelectStage 実行時に `selected=false` にされ、除外理由 `security_sensitive` が付与されます。
  - ログと警告としても通知されます。

---

## 3. 脆弱性対応プロセス

自動スキャンにより脆弱性が検知された場合の標準フローです。

1. **検知とトリアージ**:
   - CI ログまたは GitHub Security タブで詳細を確認。
   - 影響範囲（本番コードに含まれるか、テスト依存のみか）を特定。

2. **修正 (Remediation)**:
   - **バージョン更新**: 修正パッチが含まれるバージョンへ更新。
   - **代替ライブラリ**: 更新がない場合、安全な代替手段への置き換えを検討。
   - **除外**: 実際の利用箇所に影響しない場合（誤検知など）、抑制設定 (`suppression.xml` 等) を追加し、理由を明記する。

3. **緊急対応**:
   - CVSS スコアが高く、かつ実際に悪用可能な場合、直ちに Hotfix をリリースする。

---

## 関連ファイル

- ワークフロー定義: `.github/workflows/`
  - `build.yml`: 本体ビルド/テスト
  - `ci.yml`: CI まとめ（検査の入口）
  - `dependency-check.yml`: OWASP Dependency-Check
  - `dependency-review.yml`: Dependency Review
  - `release.yml`: リリース
- ソースコード:
  - `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/security/impl/SecretMasker.java`
  - `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/logging/impl/MaskedPatternLayout.java`
  - `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/interceptor/SecurityFilterInterceptor.java`
