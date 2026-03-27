> [!IMPORTANT]
> **License Notice**: This project is published under the **FUL License (Proprietary / Source Available)**.
> **Modification and Redistribution are prohibited** without explicit permission.
>
> **Pull Requests are NOT accepted** from the general public and will be closed.
> We strictly limit code contributions to authorized personnel only.
>
> However, **Bug Reports are Welcome!**
> If you find a bug, please [open an Issue](https://github.com/AI-Craftsman-Brotherhood/fulcraft/issues/new?template=bug_report.md). We appreciate your feedback.
>
> For questions or commercial licensing inquiries, contact us at **support@craftsman-bro.com**.

---

## For Users: Bug Reports and Feedback

We value your feedback. If you encounter a bug or have a suggestion:

1. Search [existing Issues](https://github.com/AI-Craftsman-Brotherhood/fulcraft/issues) to avoid duplicates.
2. [Open a new Issue](https://github.com/AI-Craftsman-Brotherhood/fulcraft/issues/new?template=bug_report.md) with a clear description, steps to reproduce, and relevant environment details.
3. Please do **not** include sensitive information (API keys, credentials, personal data) in Issue reports.

---

## For Authorized Contributors: Development Guide

> The following sections are intended for **authorized team members only**.

This guide explains the development workflow, code style and naming conventions, architectural principles and how to work effectively with AI-generated code. Following these guidelines helps ensure consistency and maintainability across the codebase.

### Useful documentation

Before you begin, the following documents may be helpful:

| Document | Description |
| :--- | :--- |
| `docs/quickstart.md` | Initial setup and first run guide |
| `docs/config.md` | Configuration reference and TUI /config editor |
| `docs/troubleshooting.md` | Common errors and solutions |
| `docs/index.md` | Full documentation |
| `CHANGELOG.md` | Release history and compatibility notes |

### Getting started

1. Clone the repository:
   ```bash
   git clone https://github.com/AI-Craftsman-Brotherhood/fulcraft.git
   cd fulcraft
   ```

2. Build the project to ensure everything works:
   ```bash
   ./gradlew :app:build
   ```

3. Create a branch for your feature or bug fix:
   ```bash
   git checkout -b feat/your-feature-name
   ```

4. Implement your changes while following the guidelines below.

5. Run tests to verify there are no regressions:
   ```bash
   ./gradlew :app:test
   ```

6. Format the code using Spotless:
   ```bash
   ./gradlew :app:spotlessApply
   ```

7. Run static analysis tools locally:
   ```bash
   ./gradlew :app:spotlessCheck         # Formatting
   ./gradlew :app:checkstyleMain        # Style rules
   ./gradlew :app:spotbugsMain          # Bytecode bugs
   ./gradlew :app:pmdMain               # Source analysis
   ./gradlew :app:dependencyCheckRuntimeAnalyze # Runtime vulnerabilities
   ./gradlew :app:dependencyCheckToolingAnalyze # Tooling vulnerabilities
   ```
   *Reports are generated in `app/build/reports/`.*

8. Commit your changes using Conventional Commits. Example:
   ```text
   feat: add new selection rule
   fix: resolve parsing error in analyzer
   docs: update README
   style: format code with spotless
   ```

9. Push and open a pull request against the main branch. Fill in the PR template and request a review.

## Code style and naming conventions

We follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) for formatting and naming. Spotless with Google Java Format is configured to enforce formatting automatically. Important points:

### Naming conventions

*   **Packages** use all lowercase letters; words are concatenated without underscores.
*   **Classes and interfaces** use `UpperCamelCase` (PascalCase). Names are typically nouns or noun phrases (e.g. `Character`, `ImmutableList`). Test classes end with `Test`.
*   **Methods** use `lowerCamelCase` and should be verbs or verb phrases (e.g. `sendMessage`, `stop`). Test method names may use underscores to separate logical components.
*   **Constants** (static final and deeply immutable) use `UPPER_SNAKE_CASE`.
*   **Fields** (non-constant) use `lowerCamelCase`.
*   **Parameters and local variables** use `lowerCamelCase`.
*   **Type parameters** use a single capital letter (`T`, `E`) or a descriptive name plus `T` (e.g. `RequestT`).

### Source file structure

*   One top-level class or interface per file; file name matches the class name plus `.java`.
*   Files are encoded in UTF-8 and use spaces for indentation (4 spaces per level). Do not use tabs.
*   Do not use wildcard imports. Sort imports alphabetically within static and non-static groups; separate the groups with one blank line.

### Programming practices

*   **Single Responsibility Principle (SRP)**: A class or method should have [one reason to change](https://en.wikipedia.org/wiki/Single-responsibility_principle). Break up classes that take on multiple responsibilities.
*   **Exception handling**: Never silently ignore exceptions. Either log them, wrap and rethrow, or handle them appropriately. When intentionally swallowing an exception, include a comment explaining why.
*   **@Override**: Always annotate overriding methods.
*   **Immutable objects**: Prefer immutable types for shared state. Mark fields as final when possible.
*   **Logging**: Use SLF4J and avoid `System.out.println` for production code.

## Project structure and architecture

FUL uses a plugin-centric, layered architecture located under `app/src/main/java/com/craftsmanbro/fulcraft`. New functionality should respect the following boundaries:

*   **kernel** - Pipeline orchestration engine.
    * `pipeline/` - `Pipeline`, `PipelineRunner`, `Stage`, `RunContext`.
    * `plugin/` - SPI-based plugin loading (`ActionPlugin`, `PluginRegistryLoader`).
    * `workflow/` - Workflow definitions, retry/failure policies.
*   **plugins** - Feature implementations (one per pipeline phase: `analysis`, `document`, `exploration`, `reporting`, `noop`), each with a consistent internal layout:
    * `flow` - Phase orchestrator and entry points. No business logic.
    * `contract` - Port interfaces and request/response types.
    * `core` - Pure business logic and rules. No I/O or framework dependencies.
    * `adapter` - Implements `contract` ports, delegates to `io`.
    * `io` - Concrete file/API/process operations.
    * `model` - Domain models and value objects.
*   **infrastructure** - Shared technical building blocks (LLM clients, file I/O, JSON, caching, VCS, telemetry, security, etc.) that fulfill the ports defined by the plugins layer.
*   **ui** - User-facing interfaces: CLI entry points (`ui/cli`), TUI (`ui/tui`), and banners.
*   **config / i18n / logging** - Cross-cutting concerns and configurations.
*   **Tests** - Live under `app/src/test/java`, mirroring the package structure of the code under test.

### Strict Dependency Rule

*   The **plugins** layer must NOT directly depend on the **infrastructure** layer.
*   Access to infrastructure (parsing, I/O, external APIs) must be mediated by **Port interfaces** defined in `contract`.
*   Only **Adapter classes** (in `adapter` packages) are allowed to implement these ports and call infrastructure code directly.
*   `core` must NOT depend on `adapter` or `io`.
*   `kernel/pipeline` must NOT reference `plugins/*`.

Refer to the Architecture section in `CLAUDE.md` for package structure and dependency rules.

## Testing guidelines

*   **Framework**: Use JUnit 5 (Jupiter). Mockito is available for mocking dependencies.
*   **Naming**: Test classes should end with `Test`; test methods should state the expected behaviour (e.g. `returnsTrue_whenConditionMet`).
*   **Coverage**:
    *   CI enforces instruction coverage (currently 80%).
    *   Focus on covering error paths and boundary conditions.
    *   Generate a build scan (`./gradlew :app:test --scan`) or check local JaCoCo reports (`app/build/reports/jacoco/`).
*   **Hermetic tests**: Use small fixtures (e.g. under `baseline` or `app/src/test/resources/`) to keep tests self-contained. Mock external dependencies (File I/O, LLM calls).
*   **Test Layers**:
    *   **Unit** (`app/src/test`): Fast (<10s), focused on single classes. Mock simple dependencies. Run with `./gradlew :app:test`.
    *   **Integration** (`app/src/integrationTest`): Validate interaction with local infrastructure (DB, File System, Git). Run with `./gradlew :app:integrationTest`.
    *   **E2E** (`app/src/e2eTest`): Full system regression using external APIs (LLM). Requires `RUN_E2E=true`. Run with `RUN_E2E=true ./gradlew :app:e2eTest`.

## Code review principles

We strive to follow the intent of the [Google Engineering Practices Review Guide](https://google.github.io/eng-practices/review/). Reviews aim to improve the overall health of the codebase, not to criticise individuals. Evaluate whether a change improves the code even if it isn't perfect.

### Review attitude

*   Be kind, specific, and actionable.
*   Prefer questions and suggestions over directives.
*   Focus on project outcomes rather than personal preferences.

### Prohibited behavior

*   Personal attacks or blaming individuals.
*   Nitpicking that does not improve code health.
*   Blocking changes without clear value or shared success criteria.

### Review process and follow-up

*   Agree on primary risks and success criteria early.
*   Record deferred concerns with rationale (issue/TODO/docs).

Focus on:
*   Design and architecture
*   Functional correctness and error handling
*   Complexity and readability
*   Test coverage and reliability
*   Naming and comments
*   Style and consistency
*   Documentation and operational impact

See [Google's Review Guide](https://google.github.io/eng-practices/review/) for more details.

## Security and configuration

*   **Secrets**: Never commit API keys or tokens. Use environment variables or secret managers.
*   **Secret scanning**: Run secret scanning with your team's preferred tooling before opening a PR.
*   **Dependabot**: Automatic dependency updates are configured in `.github/dependabot.yml`. Review Dependabot PRs before merging.
*   **Configuration files**: Keep `config.json` free of secrets. Validate user inputs and paths to avoid injection vulnerabilities.

## AI-assisted code generation

AI tools can accelerate development, but all generated code must be reviewed for compliance:

1.  State responsibilities and locations when prompting the AI. Specify the intended package and layer.
2.  Check naming and formatting. Generated identifiers must follow the conventions described above.
3.  Respect architectural boundaries. Generated classes should live in the correct package and depend only on allowed layers.
4.  Ensure **SRP** - generated classes should not mix unrelated responsibilities.
5.  Write meaningful tests - ask the AI to create at least one normal-case, boundary-case and error-case test. Avoid brittle assertions; verify behaviour and state.
6.  Handle exceptions and logging - confirm generated code properly deals with errors.
7.  Run static analysis and tests before committing. Fix any issues flagged by SpotBugs, Checkstyle, PMD and JaCoCo.
