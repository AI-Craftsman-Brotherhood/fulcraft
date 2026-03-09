# テスト品質ガイドライン

FUL リポジトリのテストを、読みやすく・壊れにくく・保守しやすく保つための指針です。

## 目的

- 仕様変更時に回帰を早期検知する
- 失敗時に原因を短時間で特定できる状態を保つ
- CI で安定して再現できるテスト群を維持する

## 適用範囲

- 主対象: `app/src/test/java/**`
- フィクスチャ: `sample-legacy-project/` またはテスト内で生成する最小入力
- 実行コマンド: `./gradlew :app:test`

## 基本方針

- テストは「実装詳細」ではなく「観測可能な振る舞い」を検証する
- 1テスト1責務を優先し、失敗理由が一目で分かる粒度にする
- 順序依存・時刻依存・環境依存を避け、決定的に実行できるようにする

## 命名規則

- テストクラス名は `*Test.java`
- テストメソッド名は `doesX_whenY` または `shouldX_whenY` 形式で統一する
- 条件と期待結果を名前に含める（例: `doesReturnEmpty_whenInputIsBlank`）

## テストケース設計

- 正常系・境界値・異常系（例外/エラー）を最低1件ずつ含める
- 解析/レポート系は次の失敗系を優先して網羅する
  - 入力ファイルなし
  - 不正フォーマット
  - パース失敗
  - 設定不足
- CLI コマンドのテストでは終了コードと主要メッセージを検証する

## 外部依存の扱い

- ネットワーク・外部プロセス・外部サービスへの実通信は行わない
- 依存はモック/スタブ/フェイクで置き換える
- `Thread.sleep()` を使った待機は避ける
- ファイルI/O は `@TempDir` などの一時領域を使い、後片付け不要にする

## アサーション方針

- 重要な結果フィールド（件数、状態、識別子）を優先して検証する
- 長文メッセージ全文一致は避け、意味のある断片を検証する
- 1テストに過剰なアサーションを詰め込まず、失敗原因を局所化する

## 品質チェック運用

日常のローカル確認:

```bash
./gradlew :app:test
./gradlew :app:spotlessApply :app:spotbugsMain :app:spotbugsTest
```

レポート生成確認（run 成果物が存在する場合）:

```bash
./gradlew :app:run --args="junit-report -p <project-root> --run-id <runId> --format json"
```

- `junit-report` は既存 run の成果物（tasks/results/summary）から集計レポートを生成する
- カバレッジ読込は `report` パッケージに統合され、必要に応じて `config.json` の `quality_gate.coverage_report_path` を参照する
- `--run-id` を省略した場合は最新 run が対象になる

## PR 前チェックリスト

1. 変更した振る舞いに対応するテストを追加または更新した
2. テストはローカルで再現性を持って成功する
3. 既存テストを壊す変更（命名・期待値・入出力形式）には理由を残した
4. レポート/品質関連設定（例: `quality_gate.coverage_report_path`）を変更した場合、影響範囲を説明した

## 関連リンク

- [Quality Gates](../QUALITY_GATES.md)
- [設定リファレンス (`quality_gate`)](config.md)
- [CLI ガイド](cli.md)
- [トラブルシューティング](troubleshooting.md)
