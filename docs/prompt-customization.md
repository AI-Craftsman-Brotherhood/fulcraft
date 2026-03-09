# プロンプトカスタマイズ

このドキュメントは、`ful generate` / `ful run`（JUnit 生成）で使うプロンプトを
**プロジェクト単位で上書きする方法**を説明します。

## 適用範囲

- 対象: JUnit テスト生成パイプライン
- 対象外: `ful document` の LLM プロンプト（現状はクラスパス固定）

## 仕組み（探索優先順位）

テンプレート読み込み時は、次の順で探索されます。

1. `{projectRoot}/.ful/prompts/{ファイル名}`
2. 明示パス（絶対パス or カレントディレクトリ相対）
3. クラスパス内の既定テンプレート（`app/src/main/resources/prompts/`）

`{projectRoot}` は通常 `-p` で指定した対象プロジェクトです。

## 推奨ディレクトリ構成

```text
your-project/
└── .ful/
    └── prompts/
        ├── default_generation.txt
        ├── default_fix.txt
        ├── default_runtime_fix.txt
        ├── fallback_stub.txt
        ├── high_complexity_generation.txt
        ├── split_phase1_validation.txt
        ├── split_phase2_happy_path.txt
        ├── split_phase3_edge_cases.txt
        └── few_shot/
            ├── builder.txt
            ├── data_class.txt
            ├── exception.txt
            ├── general.txt
            ├── inner_class.txt
            ├── service.txt
            └── utility.txt
```

## 上書きできるテンプレート

| ファイル名 | 用途 |
|---|---|
| `default_generation.txt` | 通常の生成プロンプト |
| `high_complexity_generation.txt` | 高複雑度かつ `specialized_prompt` 戦略のとき |
| `split_phase1_validation.txt` | 分割生成フェーズ1 |
| `split_phase2_happy_path.txt` | 分割生成フェーズ2 |
| `split_phase3_edge_cases.txt` | 分割生成フェーズ3 |
| `default_fix.txt` | コンパイル修復プロンプト |
| `default_runtime_fix.txt` | 実行時失敗修復プロンプト |
| `fallback_stub.txt` | 最終フォールバック用スタブ |

## Few-shot 上書き

`few_shot` は `.ful/prompts/few_shot/` 配下のファイルで上書きできます。

- `builder.txt`
- `data_class.txt`
- `exception.txt`
- `general.txt`
- `inner_class.txt`
- `service.txt`
- `utility.txt`

クラス種別に応じて自動選択されます。ネスト型や匿名クラスは `inner_class.txt` が優先されます。

## 最短手順

1. 既定テンプレートをコピー  
`app/src/main/resources/prompts/default_generation.txt` を
`{projectRoot}/.ful/prompts/default_generation.txt` へコピー
2. 変更を入れる  
まずは 1 つのルールだけ変更して差分を小さく保つ
3. 実行して挙動確認  
`ful generate -p {projectRoot} ...` または `ful run -p {projectRoot} ...`

## テンプレート編集時の注意

- 既存プレースホルダ（`{{source_code}}` など）を消すと入力情報が欠落します
- 未知のプレースホルダは空文字に置換されます
- 上書きファイル名は「パス全体」ではなく「ファイル名」で解決されます
  例: `prompts/default_generation.txt` を指定しても、実際の上書き先は
  `.ful/prompts/default_generation.txt`

## 現状の制約（2026-02-11 時点）

- `generation.prompt_template_path` は JUnit 生成の通常経路では未接続
- `generation.few_shot.enabled / examples_dir / max_examples / use_class_type_detection` も未接続
- 実運用でのカスタマイズは、`config` ではなく `.ful/prompts/` 配下のファイル上書きを使ってください

## 参照先

- 既定テンプレート: `app/src/main/resources/prompts/`
- 全体設計: [architecture-overview.md](architecture-overview.md)
