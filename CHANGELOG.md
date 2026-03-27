# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- *(Add new features here)*

### Changed
- *(Add changes in existing functionality here)*

### Deprecated
- *(Add soon-to-be removed features here)*

### Removed
- *(Add removed features here)*

### Fixed
- *(Add any bug fixes here)*

### Security
- *(Add vulnerabilities fixed here)*

---

## [0.1.0] - 2026-03-27

### Added
- Initial release of FUL — LLM-powered Java codebase analysis, documentation, and exploration tool.
- CLI pipeline with pipeline stages: `analyze`, `document`, `explore`, `report`.
- Static analysis via JavaParser and Spoon with AST-based DynamicResolver and confidence scoring.
- LLM-driven documentation generation for Java projects.
- Interactive codebase exploration via TUI (Lanterna).
- Multi-provider LLM support (OpenAI, Anthropic, Gemini, Azure OpenAI, Vertex AI, AWS Bedrock, Ollama).
- SPI-based plugin architecture for extensible pipeline stages.
- Resilience4j integration for retry, circuit breaker, and rate limiting on LLM calls.
- Run summaries, quality gate evaluation, and HTML report generation.
- Configuration via `config.json` with JSON Schema validation.

### Compatibility
- Requires Java 21 or later.
- Supports Gradle and Maven projects via wrapper/classpath resolution.
- Config schema `v1` (`config-schema-v1.json`).

### Notes
- Initial release.
