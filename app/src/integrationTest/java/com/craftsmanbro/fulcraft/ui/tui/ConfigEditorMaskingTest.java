package com.craftsmanbro.fulcraft.ui.tui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigEditorMaskingTest {

  @Test
  void formatScalarForDisplay_masksApiKeyValues(@TempDir Path tempDir) {
    Path configPath = tempDir.resolve("config.json");
    ConfigEditor editor = ConfigEditor.load(configPath);
    List<ConfigEditor.PathSegment> path =
        List.of(ConfigEditor.PathSegment.key("llm"), ConfigEditor.PathSegment.key("api_key"));

    assertEquals("****", editor.formatScalarForDisplay(path, "sk-123"));
    assertEquals("****", editor.summarizeValue(path, "sk-123", 100));
  }
}
