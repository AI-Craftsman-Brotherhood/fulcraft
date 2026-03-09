# Docs Refresh + mdBook Plan

## 0. 目的

- `docs/` 直下ドキュメントを、現行実装（2026-03 時点）に整合させる。
- 直下ドキュメントを入口として迷わない情報設計に整理する。
- 最終的に `mdBook` で静的ドキュメントサイト化する。

## 1. スコープ

対象: `docs/` 直下の以下ファイル

- `index.md`
- `quickstart.md`
- `cli.md`
- `config.md`
- `config-schema.md`
- `architecture.md`
- `ast-overview.md`
- `llm-configuration.md`
- `logging.md`
- `governance.md`
- `security-scanning.md`
- `prompt-customization.md`
- `test-quality-guidelines.md`
- `troubleshooting.md`

参考（導線/整合確認）:

- `docs/plugins/**`
- `docs/design/**`
- `README.md`
- `app/src/main/java/**`
- `app/src/main/resources/schema/config-schema-v1.json`
- `app/src/main/resources/workflows/default-workflow.json`

## 2. 現状ギャップ（初期観測）

- `docs/index.md` に存在しないリンクがある
- `installation.md`
- `config-reference.md`
- `cli-guide.md`
- `interface/index.md`
- `architecture-overview.md`
- `docs/prompt-customization.md` に `architecture-overview.md` 参照がある
- 実行モデルは workflow 中心へ移行済みのため、旧来説明（固定ステージ前提）が残っている可能性がある

## 3. 進め方（フェーズ）

### Phase 1: 事実ソース固定

1. ドキュメントの一次ソースを明示する（実装優先）
- CLI: `ui/cli/command/*Command.java`
- 設定: `Config.java` + `config-schema-v1.json`
- 実行経路: `ui/cli/wiring/PipelineFactory.java`, `kernel/workflow/*`
- プラグイン: `kernel/plugin/*`, `plugins/*`, `META-INF/services/*`

2. 直下ドキュメントごとに「正」とする参照先を表にする
- 例: `cli.md` は `RunCommand`/`AnalyzeCommand`/`DocumentCommand`/`ExploreCommand` を主参照

3. 参照先とズレる記述を issue 化（ファイル単位）

完了条件:

- 更新対象14ファイルすべてに対して、一次ソース対応表が作成されている

### Phase 2: 直下 docs 最新化

1. `index.md` を再設計
- 存在しないリンクを除去
- 直下 docs への正しい導線に置換
- `plugins/` と `design/` への入口リンクを整理

2. 優先更新（利用頻度高）
- `quickstart.md`
- `cli.md`
- `config.md`
- `config-schema.md`
- `architecture.md`

3. 追随更新（周辺）
- `llm-configuration.md`
- `logging.md`
- `governance.md`
- `security-scanning.md`
- `prompt-customization.md`
- `test-quality-guidelines.md`
- `troubleshooting.md`
- `ast-overview.md`

4. リンク整合
- 直下 docs 間リンクを相互検証
- `README.md` の docs リンクと整合させる

完了条件:

- 直下 docs の内部リンク切れ 0 件
- 少なくとも `index / quickstart / cli / config / architecture` は実装参照付きで更新済み

### Phase 3: mdBook 化

1. mdBook 基盤追加
- リポジトリルートに `book.toml` を追加
- `src = "docs"` を基本方針とする（既存 docs を移動しない）
- `docs/SUMMARY.md` を追加

2. 章構成を固定
- 直下 docs を上位章
- `plugins/` と `design/` を下位章として編成

3. mdBook ビルド確認
- `mdbook build`（ローカル）
- 警告（リンク切れ/存在しないファイル参照）を解消

4. 自動化（任意だが推奨）
- `scripts/` に docs build/check コマンドを追加
- CI に docs build ジョブを追加

完了条件:

- `mdbook build` が成功
- 生成物（`book/`）で主要ページ遷移が成立

## 4. 実行順（推奨）

1. `index.md` を最初に修正（入口の崩れを止血）
2. `cli.md` / `quickstart.md` / `config*.md` を更新
3. `architecture.md` と `plugins/architecture.md` の整合を確認
4. 残り周辺 docs を更新
5. 最後に mdBook 化して全体リンクを再検証

## 5. 検証項目

- リンク検証
- `docs/*.md` の相対リンクが実在ファイルを指す
- コマンド整合
- `cli.md` 記載コマンドが `@Command(name=...)` と一致
- 設定整合
- `config.md` / `config-schema.md` が `Config.java` と schema に一致
- 実行モデル整合
- workflow 記述が `PipelineFactory`/`WorkflowLoader`/`WorkflowPlanResolver` と一致

## 6. リスクと対応

- リスク: 実装変更が docs 更新中に追従できなくなる
- 対応: フェーズごとに参照コミットを固定し、最後に差分再確認

- リスク: mdBook 導入時に相対リンク仕様の差でリンクが崩れる
- 対応: `SUMMARY.md` 起点でリンク再解決し、mdBook build warning を 0 にする

- リスク: `docs/` と `README.md` の説明が乖離
- 対応: Phase 2 の最終タスクで相互参照チェックを必須化

## 7. 成果物

- 更新済み `docs/` 直下ドキュメント一式
- `book.toml`
- `docs/SUMMARY.md`
- （任意）docs build/check 用スクリプトと CI ジョブ
