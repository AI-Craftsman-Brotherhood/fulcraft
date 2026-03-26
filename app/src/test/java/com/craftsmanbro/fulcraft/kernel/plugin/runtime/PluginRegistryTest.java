package com.craftsmanbro.fulcraft.kernel.plugin.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginRegistryTest {

  @Test
  void constructorDefensivelyCopiesAndFreezesList() {
    ActionPlugin first = new StubPlugin("first", "generate");
    ActionPlugin second = new StubPlugin("second", "report");
    List<ActionPlugin> plugins = new ArrayList<>(List.of(first, second));

    PluginRegistry registry = new PluginRegistry(plugins);

    plugins.clear();

    assertThat(registry.all()).containsExactly(first, second);
    assertThrows(UnsupportedOperationException.class, () -> registry.all().add(first));
  }

  @Test
  void pluginsForFiltersByKind() {
    ActionPlugin generate = new StubPlugin("generate", "generate");
    ActionPlugin report = new StubPlugin("report", "report");
    ActionPlugin analyze = new StubPlugin("analyze", "analyze");
    PluginRegistry registry = new PluginRegistry(List.of(generate, report, analyze));

    assertThat(registry.pluginsFor("generate")).containsExactly(generate);
    assertThat(registry.pluginsFor("report")).containsExactly(report);
  }

  @Test
  void findByIdReturnsMatchWhenUnique() {
    ActionPlugin plugin = new StubPlugin("shared", "analyze");
    PluginRegistry registry = new PluginRegistry(List.of(plugin));

    assertThat(registry.findById("shared")).contains(plugin);
    assertThat(registry.findById("missing")).isEmpty();
  }

  @Test
  void pluginsForSkipsPluginsWhenKindLookupThrows() {
    ActionPlugin broken = new KindThrowingPlugin("broken");
    ActionPlugin report = new StubPlugin("report", "report");
    PluginRegistry registry = new PluginRegistry(List.of(broken, report));

    assertThat(registry.pluginsFor("report")).containsExactly(report);
  }

  @Test
  void constructorRejectsDuplicateIds() {
    ActionPlugin first = new StubPlugin("shared", "analyze");
    ActionPlugin second = new StubPlugin("shared", "report");

    assertThrows(IllegalArgumentException.class, () -> new PluginRegistry(List.of(first, second)));
  }

  @Test
  void nullArgumentsAreRejected() {
    PluginRegistry registry = new PluginRegistry(List.of(new StubPlugin("ok", "generate")));

    assertThrows(NullPointerException.class, () -> new PluginRegistry(null));
    assertThrows(NullPointerException.class, () -> registry.pluginsFor(null));
    assertThrows(NullPointerException.class, () -> registry.findById(null));
  }

  private static final class StubPlugin implements ActionPlugin {
    private final String id;
    private final String kind;

    private StubPlugin(String id, String kind) {
      this.id = id;
      this.kind = kind;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String kind() {
      return kind;
    }

    @Override
    public PluginResult execute(PluginContext context) {
      return PluginResult.success(id, "ok");
    }
  }

  private static final class KindThrowingPlugin implements ActionPlugin {
    private final String id;

    private KindThrowingPlugin(String id) {
      this.id = id;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String kind() {
      throw new IllegalStateException("kind lookup failed");
    }

    @Override
    public PluginResult execute(PluginContext context) {
      return PluginResult.success(id, "ok");
    }
  }
}
