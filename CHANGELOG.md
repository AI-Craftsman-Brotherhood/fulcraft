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

## [1.0.0] - 2026-01-05

### Added
- Dynamic selection prep and SPI stabilization for selection-related extensions.
- AST-based DynamicResolver with confidence scoring for dynamic string and reflection analysis.
- Document generation and reporting flows integrated into the CLI pipeline.

### Changed
- Pipeline wiring and stage orchestration clarified around `analyze` -> `select` -> `generate` -> `run` -> `report`.

### Compatibility
- Requires Java 21 or later.
- Supports Gradle and Maven projects via wrapper/classpath resolution.
- Config schema `v1` (`config-schema-v1.json`).

### Notes
- No breaking changes reported.

---

## [0.1.0] - 2025-12-15

### Added
- Initial OSS release with CLI pipeline (`analyze`, `select`, `generate`, `run`, `report`).
- LLM-backed test generation with deterministic mode and retry handling.
- Static analysis via JavaParser and Spoon.

### Compatibility
- Requires Java 21 or later.
- Config schema `v1` baseline.

### Notes
- Initial release; no breaking changes reported.
