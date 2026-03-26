# FUL (Java)

**Automated Unit Test Generator for Java Projects** — Combines LLMs and static analysis to automatically generate high-quality tests.

---

## ✨ Features

| Feature | Description |
|---|---|
| 🤖 **LLM Support** | Supports Gemini, OpenAI, Anthropic, Azure OpenAI, Vertex AI, AWS Bedrock, Local (Ollama) |
| 🔍 **Static Analysis** | Deep understanding of class structure and dependencies using JavaParser + Spoon |
| 🖥️ **TUI/CLI** | Provides both an interactive TUI (`ful tui`) and a CLI (`ful cli`) for automation |
| 🔄 **Self-Healing** | Detects compilation errors and test failures, and automatically attempts repairs |
| 🛡️ **Brittle Test Detection** | Automatically detects brittle patterns like `Thread.sleep()` and Reflection |
| 🚦 **CI Integration / Quality Check** | Visualization of results via GitHub Actions integration and HTML reports |

---

## 📦 Download

Download the latest standalone build from [GitHub Releases](https://github.com/kenya-saginuma-wada/fulcraft/releases/latest).

- `ful-X.Y.Z.zip`: Recommended. Includes the executable JAR, `ful` / `ful.bat`, `config.example.json`, and license files.
- `ful-X.Y.Z.jar`: Standalone executable fat JAR.
- `*.sha256`: Checksums for release verification.

FUL requires **Java 21 or later**.

---

## 🚀 Quick Start

### From Releases

```bash
# 1. Extract the downloaded release archive
unzip ful-X.Y.Z.zip
cd ful-X.Y.Z

# 2. Confirm the packaged CLI works
./ful --version

# 3. Run FUL against your target Java project
cd /path/to/your-java-project
/path/to/ful-X.Y.Z/ful init
/path/to/ful-X.Y.Z/ful run --to GENERATE
```

If you prefer the standalone JAR, run `java -jar ful-X.Y.Z.jar --version`.
On Windows, use `ful.bat`.

### From Source

```bash
./gradlew :app:shadowJar
java -jar app/build/libs/ful-*.jar --version
```

Detailed source-build instructions and provider setup are available in [docs/quickstart.md](docs/quickstart.md).

---

## 📚 Getting Started

Detailed instructions are available in each document. Please start with the Quickstart.

- [Quickstart](docs/quickstart.md)
- [Configuration Reference](docs/config.md)
- [Troubleshooting](docs/troubleshooting.md)

### Optional: Pin Local JDK Resolution (Team Setup)

This project uses Gradle Java toolchain (`Java 21`) by default.
If your environment shows the warning below, configure Gradle to resolve JDK from `JAVA_HOME`:

`Path for java installation '/usr/lib/jvm/openjdk-21' (Common Linux Locations) does not contain a java executable`

```bash
export JAVA_HOME=/path/to/jdk-21
```

`~/.gradle/gradle.properties`:

```properties
org.gradle.java.installations.auto-detect=false
org.gradle.java.installations.fromEnv=JAVA_HOME
org.gradle.java.installations.auto-download=true
```

If needed, you can also pin an explicit path:

```properties
org.gradle.java.installations.paths=/path/to/jdk-21
```

Do not commit machine-specific paths to this repository.

---

## 📚 Documentation

| Topic | Link |
|---|---|
| Global Navigation | [docs/index.md](docs/index.md) |
| Quickstart | [docs/quickstart.md](docs/quickstart.md) |
| CLI Guide | [docs/cli.md](docs/cli.md) |
| Configuration Reference | [docs/config.md](docs/config.md) |
| Configuration Schema | [docs/config-schema.md](docs/config-schema.md) |
| LLM Configuration Details | [docs/llm-configuration.md](docs/llm-configuration.md) |
| Architecture Overview | [docs/architecture.md](docs/architecture.md) |
| AST Overview | [docs/ast-overview.md](docs/ast-overview.md) |
| Governance | [docs/governance.md](docs/governance.md) |
| Security Scanning | [docs/security-scanning.md](docs/security-scanning.md) |
| Troubleshooting | [docs/troubleshooting.md](docs/troubleshooting.md) |
| Auto Review Automation | [docs/auto-review-automation.md](docs/auto-review-automation.md) |
| Prompt Customization | [docs/prompt-customization.md](docs/prompt-customization.md) |
| Logging | [docs/logging.md](docs/logging.md) |
| Test Quality Guidelines | [docs/test-quality-guidelines.md](docs/test-quality-guidelines.md) |
| Quality Gates | [QUALITY_GATES.md](QUALITY_GATES.md) |
| Release Procedures | [RELEASE.md](RELEASE.md) |

---

## 🤝 Contributing

Contributions are restricted. Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details.

---

## 📄 License

This project is released under the **FUL License (Proprietary / Source Available)**.
See [LICENSE](LICENSE) for details.

### License Summary
*   **Personal / Student / Non-commercial Use**: Use is permitted only within your own environment.
*   **Corporate / Commercial Use**: **Strictly Prohibited** in principle.
    *   Explicit written permission from the copyright holder is required for use.
*   **Prohibitions**:
    *   **Modification** of source code or binaries
    *   **Redistribution** of this software

### Commercial License / Inquiries
For commercial use (e.g., business use in an enterprise), please contact us at:

*   **Email**: support@craftsmann-bro.com
*   **Web**: https://craftsman-bro.com/en/contact/
