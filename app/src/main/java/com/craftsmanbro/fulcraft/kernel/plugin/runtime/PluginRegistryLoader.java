package com.craftsmanbro.fulcraft.kernel.plugin.runtime;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.logging.LoggerPort;
import com.craftsmanbro.fulcraft.logging.LoggerPortProvider;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;

/** Loads action plugins using ServiceLoader. */
public class PluginRegistryLoader {

  private static final LoggerPort LOG = LoggerPortProvider.getLogger(PluginRegistryLoader.class);

  private final String pluginClasspath;

  public PluginRegistryLoader() {
    this(null);
  }

  public PluginRegistryLoader(final String pluginClasspath) {
    this.pluginClasspath = pluginClasspath;
  }

  public PluginRegistry load() {
    final List<ActionPlugin> plugins = new ArrayList<>();
    final LinkedHashSet<String> loadedPluginIds = new LinkedHashSet<>();
    loadFromServiceLoader(ServiceLoader.load(ActionPlugin.class), plugins, loadedPluginIds);
    loadFromExternalPluginClasspath(plugins, loadedPluginIds);
    return new PluginRegistry(plugins);
  }

  private void loadFromExternalPluginClasspath(
      final List<ActionPlugin> plugins, final LinkedHashSet<String> loadedPluginIds) {
    final List<URL> externalUrls = resolveExternalUrls();
    if (externalUrls.isEmpty()) {
      return;
    }
    final URL[] urls = externalUrls.toArray(URL[]::new);
    final ClassLoader parent = Thread.currentThread().getContextClassLoader();
    try (URLClassLoader classLoader = new URLClassLoader(urls, parent)) {
      loadFromServiceLoader(
          ServiceLoader.load(ActionPlugin.class, classLoader), plugins, loadedPluginIds);
    } catch (IOException e) {
      LOG.warn(msg("kernel.plugin.loader.warn.classloader_close_failed"), e.getMessage());
    }
  }

  private List<URL> resolveExternalUrls() {
    if (pluginClasspath == null || pluginClasspath.isBlank()) {
      return List.of();
    }
    final List<URL> urls = new ArrayList<>();
    final String[] entries = pluginClasspath.split(File.pathSeparator);
    for (final String entry : entries) {
      if (entry == null || entry.isBlank()) {
        continue;
      }
      final String trimmed = entry.trim();
      try {
        final Path path = Path.of(trimmed).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
          LOG.warn(msg("kernel.plugin.loader.warn.classpath_entry_missing"), path);
          continue;
        }
        urls.add(path.toUri().toURL());
      } catch (InvalidPathException e) {
        LOG.warn(msg("kernel.plugin.loader.warn.classpath_entry_invalid"), trimmed, e.getMessage());
      } catch (MalformedURLException e) {
        LOG.warn(
            msg("kernel.plugin.loader.warn.classpath_entry_unusable"), trimmed, e.getMessage());
      }
    }
    return urls;
  }

  private void loadFromServiceLoader(
      final ServiceLoader<ActionPlugin> serviceLoader,
      final List<ActionPlugin> plugins,
      final LinkedHashSet<String> loadedPluginIds) {
    final Iterator<ActionPlugin> iterator = serviceLoader.iterator();
    ActionPlugin plugin;
    while ((plugin = nextPluginOrNull(iterator)) != null) {
      registerPlugin(plugin, plugins, loadedPluginIds);
    }
  }

  private void registerPlugin(
      final ActionPlugin plugin,
      final List<ActionPlugin> plugins,
      final LinkedHashSet<String> loadedPluginIds) {
    try {
      String pluginId = null;
      try {
        pluginId = plugin.id();
      } catch (RuntimeException e) {
        // Keep plugin even when metadata lookup fails to preserve previous behavior.
        LOG.warn(
            msg(
                "kernel.plugin.loader.warn.service_load_failed",
                plugin.getClass().getName(),
                e.getMessage()));
      }
      if (pluginId != null) {
        if (!loadedPluginIds.add(pluginId)) {
          LOG.warn(
              msg("kernel.plugin.loader.warn.duplicate_plugin_id"),
              pluginId,
              plugin.getClass().getName());
          return;
        }
      }
      plugins.add(plugin);
      if (pluginId != null) {
        LOG.debug(msg("kernel.plugin.loader.log.loaded_plugin"), pluginId, plugin.kind());
      } else {
        LOG.debug(
            msg("kernel.plugin.loader.log.loaded_plugin"),
            plugin.getClass().getName(),
            plugin.kind());
      }
    } catch (RuntimeException e) {
      LOG.warn(
          msg(
              "kernel.plugin.loader.warn.service_load_failed",
              plugin.getClass().getName(),
              e.getMessage()));
    }
  }

  private ActionPlugin nextPluginOrNull(final Iterator<ActionPlugin> iterator) {
    while (true) {
      try {
        if (!iterator.hasNext()) {
          return null;
        }
        return iterator.next();
      } catch (java.util.ServiceConfigurationError e) {
        LOG.warn(msg("kernel.plugin.loader.warn.service_configuration_error", e.getMessage()));
      }
    }
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
