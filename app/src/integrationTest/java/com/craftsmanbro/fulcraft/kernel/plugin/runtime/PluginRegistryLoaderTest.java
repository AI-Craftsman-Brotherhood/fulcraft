package com.craftsmanbro.fulcraft.kernel.plugin.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginRegistryLoaderTest {

  @Test
  void loadDiscoversServicePlugins() {
    PluginRegistry registry = new PluginRegistryLoader().load();

    assertThat(registry.findById(TestRuntimePlugin.PLUGIN_ID)).isPresent();
    assertThat(registry.pluginsFor("report"))
        .anyMatch(plugin -> plugin instanceof TestRuntimePlugin);
  }

  @Test
  void loadContinuesWhenServiceConfigurationErrorOccurs(@TempDir Path tempDir) throws IOException {
    try (URLClassLoader classLoader =
        createServiceLoaderClassLoader(
            tempDir,
            List.of(
                "com.craftsmanbro.fulcraft.kernel.plugin.runtime.DoesNotExistPlugin",
                ContextClassLoaderOnlyPlugin.class.getName()))) {
      PluginRegistry registry = loadWithContextClassLoader(classLoader);

      assertThat(registry.findById(ContextClassLoaderOnlyPlugin.PLUGIN_ID)).isPresent();
      assertThat(registry.pluginsFor("report"))
          .anyMatch(plugin -> plugin instanceof ContextClassLoaderOnlyPlugin);
    }
  }

  @Test
  void loadContinuesWhenPluginMetadataAccessThrows(@TempDir Path tempDir) throws IOException {
    try (URLClassLoader classLoader =
        createServiceLoaderClassLoader(
            tempDir,
            List.of(
                BrokenLoggingRuntimePlugin.class.getName(),
                ContextClassLoaderOnlyPlugin.class.getName()))) {
      PluginRegistry registry = loadWithContextClassLoader(classLoader);

      assertThat(registry.all()).anyMatch(plugin -> plugin instanceof BrokenLoggingRuntimePlugin);
      assertThat(registry.all()).anyMatch(plugin -> plugin instanceof ContextClassLoaderOnlyPlugin);
    }
  }

  @Test
  void loadDiscoversPluginsFromAdditionalPluginClasspath(@TempDir Path tempDir) throws IOException {
    Path serviceFile = tempDir.resolve("META-INF/services/" + ActionPlugin.class.getName());
    Files.createDirectories(serviceFile.getParent());
    Files.writeString(
        serviceFile,
        ContextClassLoaderOnlyPlugin.class.getName() + System.lineSeparator(),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);

    String pluginClasspath =
        tempDir.resolve("missing-entry").toString() + java.io.File.pathSeparator + tempDir;
    PluginRegistry registry = new PluginRegistryLoader(pluginClasspath).load();

    assertThat(registry.findById(ContextClassLoaderOnlyPlugin.PLUGIN_ID)).isPresent();
  }

  private URLClassLoader createServiceLoaderClassLoader(Path tempDir, List<String> providers)
      throws IOException {
    Path serviceFile = tempDir.resolve("META-INF/services/" + ActionPlugin.class.getName());
    Files.createDirectories(serviceFile.getParent());
    Files.write(serviceFile, providers, StandardCharsets.UTF_8);
    URL[] urls = {tempDir.toUri().toURL()};
    return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
  }

  private PluginRegistry loadWithContextClassLoader(ClassLoader classLoader) {
    Thread thread = Thread.currentThread();
    ClassLoader original = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(classLoader);
      return new PluginRegistryLoader().load();
    } finally {
      thread.setContextClassLoader(original);
    }
  }
}
