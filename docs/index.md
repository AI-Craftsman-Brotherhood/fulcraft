# FUL (Java)

**Automated unit test generation for Java projects**. FUL combines LLMs and static analysis to generate high-quality tests.

---

## Features

| Capability | Description |
|------|------|
| LLM support | Supports Gemini, OpenAI, Anthropic, Azure OpenAI, Vertex AI, AWS Bedrock, and local providers such as Ollama |
| Static analysis | Uses JavaParser and Spoon to understand class structure and dependencies |
| CLI and TUT | Provides both automation-friendly CLI commands and the non-visual interactive `ful tut` mode |
| Self-healing | Detects compile errors and test failures, then attempts automated fixes |
| Brittle test detection | Detects fragile patterns such as `Thread.sleep()` and reflection |
| CI integration | Works with GitHub Actions and exposes result summaries via reporting commands |

---

## Start Here

Use these pages first:

- [Quickstart](quickstart.md)
- [CLI Guide](cli.md)
- [Configuration Reference](config.md)

---

## Documentation

| Topic | Link |
|---------|-------|
| Home | [index.md](index.md) |
| Quickstart | [quickstart.md](quickstart.md) |
| CLI Guide | [cli.md](cli.md) |
| Configuration Reference | [config.md](config.md) |
| Config Schema | [config-schema.md](config-schema.md) |
| LLM Configuration | [llm-configuration.md](llm-configuration.md) |
| Architecture Overview | [architecture.md](architecture.md) |
| AST Overview | [ast-overview.md](ast-overview.md) |
| Plugin Architecture | [plugin-architecture.md](plugin-architecture.md) |
| Logging | [logging.md](logging.md) |
| Governance | [governance.md](governance.md) |
| Security Scanning | [security-scanning.md](security-scanning.md) |
| Prompt Customization | [prompt-customization.md](prompt-customization.md) |
| Test Quality Guidelines | [test-quality-guidelines.md](test-quality-guidelines.md) |
| Troubleshooting | [troubleshooting.md](troubleshooting.md) |
| Quality Gates | [../QUALITY_GATES.md](../QUALITY_GATES.md) |
| Release Procedure | [../RELEASE.md](../RELEASE.md) |

---

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](../CONTRIBUTING.md) for details.

---

## License

This project is published under the **FUL License (Proprietary / Source Available)**.
See [LICENSE](../LICENSE) for the full terms.

### License Summary

- Personal, student, and non-commercial use: allowed only within your own environment.
- Corporate and commercial use: prohibited by default.
- Written permission from the copyright holder is required for such use.
- Prohibited actions:
- Modification of the source code or binaries.
- Redistribution of the software.

### Commercial Licensing / Contact

For commercial use, including business use inside a company, contact:

- Email: support@craftsmann-bro.com
- Web: https://craftsmann-bro.com/contact
