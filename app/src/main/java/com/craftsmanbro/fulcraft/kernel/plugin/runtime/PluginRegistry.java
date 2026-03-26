package com.craftsmanbro.fulcraft.kernel.plugin.runtime;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** Registry for action plugins. */
public final class PluginRegistry {

  private final List<ActionPlugin> plugins;

  public PluginRegistry(final List<ActionPlugin> plugins) {
    final List<ActionPlugin> copied =
        List.copyOf(
            Objects.requireNonNull(
                plugins, MessageSource.getMessage("kernel.common.error.argument_null", "plugins")));
    ensureUniqueIds(copied);
    this.plugins = copied;
  }

  public List<ActionPlugin> pluginsFor(final String kind) {
    Objects.requireNonNull(
        kind, MessageSource.getMessage("kernel.common.error.argument_null", "kind"));
    final String normalizedKind = PluginKind.normalizeRequired(kind, "kind");
    final List<ActionPlugin> matches = new ArrayList<>();
    for (final ActionPlugin plugin : plugins) {
      if (plugin == null) {
        continue;
      }
      try {
        final String pluginKind = PluginKind.normalizeNullable(plugin.kind());
        if (normalizedKind.equals(pluginKind)) {
          matches.add(plugin);
        }
      } catch (RuntimeException ignored) {
        // Keep lookups resilient when registry contains plugins with broken metadata access.
      }
    }
    return List.copyOf(matches);
  }

  public Optional<ActionPlugin> findById(final String pluginId) {
    Objects.requireNonNull(
        pluginId, MessageSource.getMessage("kernel.common.error.argument_null", "pluginId"));
    for (final ActionPlugin plugin : plugins) {
      if (plugin == null) {
        continue;
      }
      try {
        if (pluginId.equals(plugin.id())) {
          return Optional.of(plugin);
        }
      } catch (RuntimeException ignored) {
        // Skip plugins that cannot expose metadata reliably.
      }
    }
    return Optional.empty();
  }

  public List<ActionPlugin> all() {
    return plugins;
  }

  private void ensureUniqueIds(final List<ActionPlugin> plugins) {
    final TreeSet<String> seen = new TreeSet<>();
    final TreeSet<String> duplicates = new TreeSet<>();
    for (final ActionPlugin plugin : plugins) {
      if (plugin == null) {
        continue;
      }
      final String id;
      try {
        id = plugin.id();
      } catch (RuntimeException ignored) {
        // Some legacy/test plugins may fail metadata lookup; keep them in registry.
        continue;
      }
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException(
            MessageSource.getMessage("kernel.plugin.registry.error.plugin_id_blank"));
      }
      if (!seen.add(id)) {
        duplicates.add(id);
      }
    }
    if (!duplicates.isEmpty()) {
      throw new IllegalArgumentException(
          MessageSource.getMessage(
              "kernel.plugin.registry.error.duplicate_plugin_ids", String.join(", ", duplicates)));
    }
  }
}
