package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalConfigValueLoaderTest {

  @TempDir Path tempDir;

  @Test
  void load_returnsEmptyForMissingDirectory() {
    ExternalConfigValueLoader loader = new ExternalConfigValueLoader();

    assertThat(loader.load(null)).isEmpty();
    assertThat(loader.load(tempDir.resolve("missing"))).isEmpty();
  }

  @Test
  void load_readsSimplePropertiesAndYamlValues() throws IOException {
    Path resources = tempDir.resolve("resources");
    Files.createDirectories(resources);

    Files.writeString(
        resources.resolve("a.properties"),
        "simple.key=value\n" + "dup=fromProperties\n" + "placeholder=${ENV_VAR}\n" + "empty=\n",
        StandardCharsets.UTF_8);

    Files.writeString(
        resources.resolve("b.yaml"),
        "title: \"Hello World\" # comment\n"
            + "count: 42\n"
            + "quotedSingle: 'single value'\n"
            + "inlineHash: \"value # not comment\"\n"
            + "dup: fromYaml\n"
            + "list: [a, b]\n"
            + "object: {a: b}\n"
            + "multiline: |\n"
            + "  line1\n"
            + "indented:\n"
            + "  child: value\n",
        StandardCharsets.UTF_8);

    Map<String, String> values = new ExternalConfigValueLoader().load(resources);

    assertThat(values)
        .containsEntry("simple.key", "value")
        .containsEntry("title", "Hello World")
        .containsEntry("count", "42")
        .containsEntry("quotedSingle", "single value")
        .containsEntry("inlineHash", "value # not comment")
        .containsEntry("dup", "fromProperties");

    assertThat(values)
        .doesNotContainKey("placeholder")
        .doesNotContainKey("empty")
        .doesNotContainKey("list")
        .doesNotContainKey("object")
        .doesNotContainKey("multiline")
        .doesNotContainKey("indented")
        .doesNotContainKey("child");
  }

  @Test
  void load_handlesYamlEdgeCasesAndYmlExtension() throws IOException {
    Path resources = tempDir.resolve("resources");
    Files.createDirectories(resources);

    Files.writeString(
        resources.resolve("edge.yml"),
        """
            # comment
             indented: ignored
            : missingKey
            noColonLine
            emptyValue:
            objectValue: {a: b}
            listValue: [a, b]
            folded: >
              line1
            placeholder: ${FROM_ENV}
            url: http://craftsmann-bro.com#a
            quotedSingle: 'single # keep'
            quotedDouble: "double # keep"
            plainWithComment: value # remove this
            """,
        StandardCharsets.UTF_8);

    Files.writeString(resources.resolve("ignored.txt"), "ignored: value", StandardCharsets.UTF_8);

    Map<String, String> values = new ExternalConfigValueLoader().load(resources);

    assertThat(values)
        .containsEntry("url", "http://craftsmann-bro.com#a")
        .containsEntry("quotedSingle", "single # keep")
        .containsEntry("quotedDouble", "double # keep")
        .containsEntry("plainWithComment", "value");

    assertThat(values)
        .doesNotContainKey("indented")
        .doesNotContainKey("missingKey")
        .doesNotContainKey("emptyValue")
        .doesNotContainKey("objectValue")
        .doesNotContainKey("listValue")
        .doesNotContainKey("folded")
        .doesNotContainKey("placeholder")
        .doesNotContainKey("ignored");
  }
}
