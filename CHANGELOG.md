# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `analysis.language_level` configuration option to control the Java source level used by both JavaParser and Spoon. Accepts `JAVA_8`..`JAVA_21`, `BLEEDING_EDGE`, `POPULAR`, and `CURRENT` (case-insensitive; underscores/spaces/hyphens accepted). Defaults to `JAVA_21` (LTS).
- `JavaParserFactory` as a single entry point for constructing analysis-path JavaParser instances with a consistent language level.
- Internationalized HTML output for `ful explore`; locale follows `MessageSource` and propagates to the `<html lang>` attribute.
- New i18n keys (`explore.html.*`, `analysis.language_level.warn.unknown`) for English and Japanese.
- v1 schema (`config-schema-v1.json`) now accepts top-level keys `brittle_test_rules`, `mocking`, `local_fix`, `log`, `docs`, `cache`, `interceptors`, and `verification` as opaque objects to unblock partially-modeled sections.

### Changed
- Default analysis engine, when `analysis.engine` is unset, is now `composite` (JavaParser + Spoon merged) — matching the CLI wiring in `DefaultServiceFactory.createDefaultAnalysisPort()`. Previously, the `AnalysisPortFactory` documentation indicated `SPOON` as the default, which did not match runtime behavior.
- Project-scope view in the visual analysis report (`analysis_visual.html`) now lays out **packages** instead of individual classes for the top-level overview, with class-level detail available on drill-down.
- Dependencies upgraded: `javaparser` `3.25.7 → 3.27.0`, `spoon` `10.4.2 → 11.2.1` to obtain first-class enum support for `JAVA_19`–`JAVA_21` and a newer JDT.
- CodeQL analysis is now active in CI (previously gated by `if: false`).

### Fixed
- Visual analysis report no longer counts unresolved lowercase-leading method chains (e.g. `item.product` from `item.product().code()`) as external dependencies.
- Visual analysis report no longer surfaces classes whose package matches an internal package as external dependencies when their sources failed to parse.
- Default Java language level used in analysis paths now matches across JavaParser (`JAVA_21`) and Spoon (`compliance level 21`) — resolving cases where Java 14+ records / sealed types / pattern matching produced `Record Declarations are not supported` parse errors.
- `analysis.engine` configuration is now honored at runtime. Previously the `--engine` CLI flag carried a hard default of `composite`, which silently shadowed the configured value, so `analysis.engine` had no effect. Resolution order is now: explicit `--engine` flag → `analysis.engine` config → `composite` default. Applies to `analyze`, `run`, `report`, and `document`.
- The JavaParser analysis engine now extracts `record` and `enum` types (record components, synthesized canonical constructors and accessors, enum constants and constructors). Previously only classes and interfaces were traversed, so `--engine javaparser` silently dropped records and enums.
- Analysis `file_path` is now project-root-relative (e.g. `src/main/java/com/foo/Bar.java`) consistently across the JavaParser and Spoon engines. The Spoon engine previously emitted source-root-relative paths, which split report/document output for the same package across two directory trees.
- The composite engine no longer emits duplicate methods. Parameter types are resolved through the declaring class's imports so both engines agree on a method's identity; constructors are consistently named `<init>`; an enum's implicit constructor is no longer counted twice; and within-class duplicates are dropped during merge.
- The `language_level` aliases `POPULAR` and `CURRENT` now resolve to the same concrete level in both engines (`POPULAR` = Java 17, `CURRENT` = Java 21), instead of differing between JavaParser and Spoon.

### Added (tests)
- Smoke integration test for the composite analysis pipeline and an opt-in end-to-end CLI test for `ful analyze`, populating the previously-empty `integrationTest`/`e2eTest` source sets.

### Removed
- Internal-only documents from `docs/proposals/` that are not appropriate for the public repository.

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
