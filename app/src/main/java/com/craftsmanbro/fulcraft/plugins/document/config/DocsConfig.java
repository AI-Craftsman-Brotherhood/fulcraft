package com.craftsmanbro.fulcraft.plugins.document.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocsConfig {
  private static final String FORMAT_MARKDOWN = "markdown";

  /** Output format: "markdown" (default), "html", "pdf", or "all". */
  @JsonProperty("format")
  private String format = FORMAT_MARKDOWN;

  /** Whether to generate dependency diagrams (Mermaid/PlantUML). */
  @JsonProperty("diagram")
  private Boolean diagram = false;

  /** Whether to include links to related tests. */
  @JsonProperty("include_tests")
  private Boolean includeTests = false;

  /** Whether to use LLM for enhanced documentation. */
  @JsonProperty("use_llm")
  private Boolean useLlm = false;

  /** Diagram format: "mermaid" (default) or "plantuml". */
  @JsonProperty("diagram_format")
  private String diagramFormat = "mermaid";

  /** Whether to generate a single combined document. */
  @JsonProperty("single_file")
  private Boolean singleFile = false;

  /**
   * Method rendering mode: "legacy" (all methods via LLM) or "hybrid" (simple methods via template,
   * complex methods via LLM).
   */
  @JsonProperty("method_render_mode")
  private String methodRenderMode = "hybrid";

  /** Test output root directory for resolving test links. */
  @JsonProperty("test_output_root")
  private String testOutputRoot;

  public String getFormat() {
    return format != null ? format : FORMAT_MARKDOWN;
  }

  public void setFormat(final String format) {
    this.format = format;
  }

  public boolean isDiagram() {
    return Boolean.TRUE.equals(diagram);
  }

  public void setDiagram(final Boolean diagram) {
    this.diagram = diagram;
  }

  public boolean isIncludeTests() {
    return Boolean.TRUE.equals(includeTests);
  }

  public void setIncludeTests(final Boolean includeTests) {
    this.includeTests = includeTests;
  }

  public boolean isUseLlm() {
    return Boolean.TRUE.equals(useLlm);
  }

  public void setUseLlm(final Boolean useLlm) {
    this.useLlm = useLlm;
  }

  public String getDiagramFormat() {
    return diagramFormat != null ? diagramFormat : "mermaid";
  }

  public void setDiagramFormat(final String diagramFormat) {
    this.diagramFormat = diagramFormat;
  }

  public boolean isSingleFile() {
    return Boolean.TRUE.equals(singleFile);
  }

  public void setSingleFile(final Boolean singleFile) {
    this.singleFile = singleFile;
  }

  public String getMethodRenderMode() {
    return methodRenderMode != null ? methodRenderMode : "hybrid";
  }

  public void setMethodRenderMode(final String methodRenderMode) {
    this.methodRenderMode = methodRenderMode;
  }

  public String getTestOutputRoot() {
    return testOutputRoot;
  }

  public void setTestOutputRoot(final String testOutputRoot) {
    this.testOutputRoot = testOutputRoot;
  }

  /** Returns true if markdown format is requested. */
  public boolean isMarkdownFormat() {
    final String f = getFormat();
    return FORMAT_MARKDOWN.equalsIgnoreCase(f) || "md".equalsIgnoreCase(f);
  }

  /** Returns true if HTML format is requested. */
  public boolean isHtmlFormat() {
    return "html".equalsIgnoreCase(getFormat());
  }

  /** Returns true if PDF format is requested. */
  public boolean isPdfFormat() {
    return "pdf".equalsIgnoreCase(getFormat());
  }

  /** Returns true if all formats are requested. */
  public boolean isAllFormats() {
    return "all".equalsIgnoreCase(getFormat());
  }
}
