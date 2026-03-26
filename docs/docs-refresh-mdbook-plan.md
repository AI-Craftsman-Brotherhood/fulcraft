# Docs Refresh + mdBook Plan

## 0. Goals

- Align the documents directly under `docs/` with the current implementation as of March 2026.
- Reorganize those top-level documents so they work as a clear entry point.
- Eventually publish the documentation as a static site through `mdBook`.

## 1. Scope

Target files directly under `docs/`:

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

Reference material used for navigation and consistency checks:

- `docs/plugins/**`
- `docs/design/**`
- `README.md`
- `app/src/main/java/**`
- `app/src/main/resources/schema/config-schema-v1.json`
- `app/src/main/resources/workflows/default-workflow.json`

## 2. Initial Gaps

- `docs/index.md` referenced files that did not exist.
- Examples included `installation.md`, `config-reference.md`, `cli-guide.md`, `interface/index.md`, and `architecture-overview.md`.
- `docs/prompt-customization.md` also referenced `architecture-overview.md`.
- The runtime model is workflow-centric now, but some older explanations still assume fixed pipeline stages.

## 3. Phases

### Phase 1: Lock Down Source of Truth

1. Identify primary implementation sources for each document.
   - CLI: `ui/cli/command/*Command.java`
   - Configuration: `Config.java` plus `config-schema-v1.json`
   - Execution flow: `ui/cli/wiring/PipelineFactory.java`, `kernel/workflow/*`
   - Plugins: `kernel/plugin/*`, `plugins/*`, `META-INF/services/*`
2. Create a source-of-truth table for every top-level doc.
3. Open file-level issues for descriptions that drift from implementation.

Completion condition:

- All 14 target files have a primary-source mapping.

### Phase 2: Refresh Top-Level Docs

1. Redesign `index.md`.
   - Remove broken links.
   - Replace them with correct navigation to top-level docs.
   - Clarify entry links into `plugins/` and `design/`.
2. Update the highest-traffic pages first.
   - `quickstart.md`
   - `cli.md`
   - `config.md`
   - `config-schema.md`
   - `architecture.md`
3. Update surrounding reference pages.
   - `llm-configuration.md`
   - `logging.md`
   - `governance.md`
   - `security-scanning.md`
   - `prompt-customization.md`
   - `test-quality-guidelines.md`
   - `troubleshooting.md`
   - `ast-overview.md`
4. Recheck links.
   - Verify internal links among top-level docs.
   - Keep docs links consistent with `README.md`.

Completion condition:

- Zero broken internal links among top-level docs.
- At minimum, `index`, `quickstart`, `cli`, `config`, and `architecture` are refreshed against implementation sources.

### Phase 3: Convert to mdBook

1. Add the mdBook foundation.
   - Add `book.toml` at the repository root.
   - Keep `src = "docs"` so existing docs do not need to move.
   - Add `docs/SUMMARY.md`.
2. Freeze chapter structure.
   - Treat top-level docs as top chapters.
   - Organize `plugins/` and `design/` beneath them.
3. Verify the mdBook build.
   - Run `mdbook build` locally.
   - Resolve warnings about broken links or missing files.
4. Optionally automate it.
   - Add build or check scripts under `scripts/`.
   - Add a docs build job to CI.

Completion condition:

- `mdbook build` succeeds.
- Main page navigation works in the generated `book/` output.

## 4. Recommended Order

1. Fix `index.md` first to restore the entry point.
2. Update `cli.md`, `quickstart.md`, and `config*.md`.
3. Reconcile `architecture.md` with `plugins/architecture.md`.
4. Update the remaining supporting docs.
5. Convert to mdBook and run a full link check at the end.

## 5. Verification Items

- Relative links in `docs/*.md` point to real files.
- Commands documented in `cli.md` match `@Command(name=...)` definitions.
- `config.md` and `config-schema.md` match `Config.java` and the schema.
- Workflow descriptions match `PipelineFactory`, `WorkflowLoader`, and `WorkflowPlanResolver`.

## 6. Risks and Responses

- Risk: implementation changes while documentation work is in progress.
  - Response: pin a reference commit per phase and recheck drift at the end.
- Risk: mdBook relative-link behavior breaks existing links.
  - Response: re-resolve links from `SUMMARY.md` and drive mdBook warnings to zero.
- Risk: `docs/` diverges from `README.md`.
  - Response: make cross-checking with `README.md` a required final task in Phase 2.

## 7. Deliverables

- Refreshed documentation files directly under `docs/`
- `book.toml`
- `docs/SUMMARY.md`
- Optionally, docs build or check scripts and a CI job
