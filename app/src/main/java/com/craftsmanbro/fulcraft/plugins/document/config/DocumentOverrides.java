package com.craftsmanbro.fulcraft.plugins.document.config;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigOverride;
import java.util.ArrayList;
import java.util.List;

/**
 * Document generation specific configuration overrides.
 *
 * <p>Provides a fluent builder API for document command specific options.
 */
public class DocumentOverrides implements ConfigOverride {

  private List<String> files;

  private List<String> dirs;

  private String format;

  private boolean diagram;

  private boolean includeTests;

  private boolean useLlm;

  private boolean singleFile;

  private String diagramFormat;

  public DocumentOverrides withFiles(final List<String> files) {
    this.files = files;
    return this;
  }

  public DocumentOverrides withDirs(final List<String> dirs) {
    this.dirs = dirs;
    return this;
  }

  public DocumentOverrides withFormat(final String format) {
    this.format = format;
    return this;
  }

  public DocumentOverrides withDiagram(final boolean diagram) {
    this.diagram = diagram;
    return this;
  }

  public DocumentOverrides withIncludeTests(final boolean includeTests) {
    this.includeTests = includeTests;
    return this;
  }

  public DocumentOverrides withUseLlm(final boolean useLlm) {
    this.useLlm = useLlm;
    return this;
  }

  public DocumentOverrides withSingleFile(final boolean singleFile) {
    this.singleFile = singleFile;
    return this;
  }

  public DocumentOverrides withDiagramFormat(final String diagramFormat) {
    this.diagramFormat = diagramFormat;
    return this;
  }

  @Override
  public void apply(final Config config) {
    applyFilters(config);
    applyDocsOverrides(config);
  }

  private void applyFilters(final Config config) {
    final List<String> includePaths = new ArrayList<>();
    if (files != null && !files.isEmpty()) {
      includePaths.addAll(files);
    }
    if (dirs != null && !dirs.isEmpty()) {
      includePaths.addAll(dirs);
    }
    if (!includePaths.isEmpty()) {
      if (config.getProject() == null) {
        config.setProject(new Config.ProjectConfig());
      }
      config.getProject().setIncludePaths(includePaths);
    }
  }

  private void applyDocsOverrides(final Config config) {
    if (config.getDocs() == null) {
      config.setDocs(new Config.DocsConfig());
    }
    final DocsConfig docsConfig = config.getDocs();
    if (format != null) {
      docsConfig.setFormat(format);
    }
    if (diagram) {
      docsConfig.setDiagram(true);
    }
    if (includeTests) {
      docsConfig.setIncludeTests(true);
    }
    if (useLlm) {
      docsConfig.setUseLlm(true);
    }
    if (singleFile) {
      docsConfig.setSingleFile(true);
    }
    if (diagramFormat != null) {
      docsConfig.setDiagramFormat(diagramFormat);
    }
  }
}
