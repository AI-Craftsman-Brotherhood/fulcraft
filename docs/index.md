# FUL (Java)

**Javaプロジェクト向け自動ユニットテスト生成ツール** — LLMと静的解析を組み合わせ、高品質なテストを自動生成します。

---

## ✨ 特長

| 機能 | 説明 |
|------|------|
| 🤖 **LLMサポート** | Gemini, OpenAI, Anthropic, Azure OpenAI, Vertex AI, AWS Bedrock, Local (Ollama) に対応 |
| 🔍 **静的解析** | JavaParser + Spoon でクラス構造・依存関係を深く理解 |
| 🖥️ **TUI/CLI** | 非画面の対話CLI (`ful tut`) と自動化向けCLIの両方を提供 |
| 🔄 **自己修復** | コンパイルエラーやテスト失敗を検知し、自動で修正を試行 |
| ️ **Brittle Test 検出** | `Thread.sleep()` やReflection等の脆いパターンを自動検出 |

---

## 🚀 はじめに

詳細な手順は各ドキュメントにまとめています。まずは Quickstart から参照してください。

- [Quickstart](quickstart.md)
- [インストールと実行方法](installation.md)
- [設定リファレンス（要約）](config-reference.md)

---

## 📚 ドキュメント

| トピック | リンク |
|---------|-------|
| 全体ナビゲーション | [index.md](index.md) |
| Quickstart | [quickstart.md](quickstart.md) |
| インストール/実行 | [installation.md](installation.md) |
| CLI / TUI ガイド | [cli-guide.md](cli-guide.md) |
| 出力インターフェース | [interface/index.md](interface/index.md) |
| 設定リファレンス（要約） | [config-reference.md](config-reference.md) |
| LLM 設定詳細 | [llm-configuration.md](llm-configuration.md) |
| アーキテクチャ概要 | [architecture-overview.md](architecture-overview.md) |
| パイプライン詳細 | [design/pipeline_design_spec.md](design/pipeline_design_spec.md) |
| ガバナンス/セキュリティ | [governance.md](governance.md) |
| セキュリティスキャン | [security-scanning.md](security-scanning.md) |
| トラブルシューティング | [troubleshooting.md](troubleshooting.md) |
| プロンプトカスタマイズ | [prompt-customization.md](prompt-customization.md) |
| 品質ゲート | [../QUALITY_GATES.md](../QUALITY_GATES.md) |
| リリース手順 | [../RELEASE.md](../RELEASE.md) |

---

## 🤝 Contributing

コントリビューションを歓迎します！詳細は [CONTRIBUTING.md](../CONTRIBUTING.md) をご覧ください。

---

## 📄 License

このプロジェクトは **FUL License (Proprietary / Source Available)** の下で公開されています。
詳細は [LICENSE](../LICENSE) を参照してください。

### ライセンス要約
*   **個人・学生・非商用利用**: 自身の環境での利用のみ許可されます。
*   **企業・商用利用**: **原則禁止**です。
    *   利用には著作権者の書面による明示的な許可が必要です。
*   **禁止事項**:
    *   ソースコードおよびバイナリの**変更**（Modification）
    *   本ソフトウェアの**再配布**（Redistribution）

### 商用ライセンス・お問い合わせ
商用利用（企業での業務利用など）をご希望の場合は、以下までお問い合わせください。

*   **Email**: support@craftsmann-bro.com
*   **Web**: https://craftsmann-bro.com/contact
