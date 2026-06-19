package com.craftsmanbro.fulcraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.support.ConfigBuilder;
import com.craftsmanbro.fulcraft.support.MockLlmServer;
import com.craftsmanbro.fulcraft.support.ProjectFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test for {@code ful document}: covers both the non-LLM markdown path and the LLM path
 * driven against a deterministic mock LLM server (no network). Exercises the {@code
 * createDecoratedLlmClient} wiring and the {@code DocumentFlow} LLM branch.
 */
@DisplayName("ful document (end-to-end CLI)")
class CliDocumentLlmE2eTest extends E2eTestBase {

  @Test
  @DisplayName("document without --llm generates markdown files (no LLM server)")
  void documentWithoutLlmGeneratesMarkdown() throws IOException {
    ProjectFixtures.writeMultiTypeProject(workspace);
    // Point the LLM at an unreachable loopback port so the test is hermetic even if the LLM path
    // were unexpectedly taken (it should not be, since --llm is not passed).
    final Path config =
        ConfigBuilder.create().llm("local", "http://127.0.0.1:1/v1", "unused").writeTo(workspace);
    final Path docs = workspace.resolve("docs-nollm");

    final int exitCode =
        runCli("-c", config.toString(), "document", "-o", docs.toString(), workspace.toString());

    assertThat(exitCode).isZero();
    assertThat(markdownFiles(docs)).isNotEmpty();
  }

  @Test
  @DisplayName("document --llm calls the (mock) LLM and writes docs")
  void documentWithLlmUsesMockServer() throws IOException {
    ProjectFixtures.writeMultiTypeProject(workspace);
    try (MockLlmServer llm = new MockLlmServer("# Generated\nMOCK_DOC_BODY")) {
      final Path config =
          ConfigBuilder.create().llm("local", llm.baseUrl(), "local-model").writeTo(workspace);
      final Path docs = workspace.resolve("docs-llm");

      final int exitCode =
          runCli(
              "-c",
              config.toString(),
              "document",
              "--llm",
              "-o",
              docs.toString(),
              workspace.toString());

      assertThat(exitCode).isZero();
      // A real chat-completion call (counted separately from the /v1/models health probe) proves
      // the --llm path wired and invoked the LLM client end-to-end, rather than silently taking the
      // non-LLM path.
      assertThat(llm.completionRequestCount()).isPositive();
      assertThat(markdownFiles(docs)).isNotEmpty();
    }
  }

  private static List<Path> markdownFiles(final Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.walk(dir)) {
      return files
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(".md"))
          .toList();
    }
  }
}
