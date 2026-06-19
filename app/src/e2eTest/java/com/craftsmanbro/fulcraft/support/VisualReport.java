package com.craftsmanbro.fulcraft.support;

import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Extracts the embedded analysis JSON from a generated {@code analysis_visual.html} so tests can
 * assert on its structure (packages, classes, edges, etc.). The visual report embeds the data in a
 * {@code <script type="application/json" id="analysis-data">…</script>} block.
 */
public final class VisualReport {

  private static final Pattern DATA_SCRIPT =
      Pattern.compile("<script[^>]*id=\"analysis-data\"[^>]*>(.*?)</script>", Pattern.DOTALL);

  private VisualReport() {
    // utility
  }

  /** Parse the embedded analysis-data JSON from an {@code analysis_visual.html} file. */
  public static JsonNode readData(final Path analysisVisualHtml) throws IOException {
    final String html = Files.readString(analysisVisualHtml);
    final Matcher matcher = DATA_SCRIPT.matcher(html);
    if (!matcher.find()) {
      throw new AssertionError("analysis-data script block not found in " + analysisVisualHtml);
    }
    // The data is plain JSON; defensively reverse the </ -> <\/ escaping some writers apply.
    final String json = matcher.group(1).replace("<\\/", "</");
    return JsonMapperFactory.create().readTree(json);
  }
}
