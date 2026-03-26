package com.craftsmanbro.fulcraft.kernel.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RunMetadataTest {

  @Test
  void shouldStoreAndRetrieveTypedValues() {
    RunMetadata metadata = new RunMetadata();

    metadata.put("count", 3);
    metadata.put("label", "ok");

    assertThat(metadata.get("count", Integer.class)).contains(3);
    assertThat(metadata.get("count", String.class)).isEmpty();
    assertThat(metadata.get("label", String.class)).contains("ok");
  }

  @Test
  void shouldRemoveValues() {
    RunMetadata metadata = new RunMetadata();

    metadata.put("key", "value");
    metadata.remove("key");

    assertThat(metadata.get("key", String.class)).isEmpty();
  }

  @Test
  void shouldProvideImmutableSnapshot() {
    RunMetadata metadata = new RunMetadata();
    metadata.put("key", "value");

    Map<String, Object> snapshot = metadata.snapshot();

    assertThat(snapshot).containsEntry("key", "value");
    assertThatThrownBy(() -> snapshot.put("other", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldPreserveInsertionOrderInSnapshot() {
    RunMetadata metadata = new RunMetadata();

    metadata.put("first", 1);
    metadata.put("second", 2);

    assertThat(metadata.snapshot().keySet()).containsExactly("first", "second");
  }

  @Test
  void shouldRejectNullArguments() {
    RunMetadata metadata = new RunMetadata();

    assertThatThrownBy(() -> metadata.put(null, "value"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("key");
    assertThatThrownBy(() -> metadata.put("key", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("value");
    assertThatThrownBy(() -> metadata.remove(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("key");
    assertThatThrownBy(() -> metadata.get(null, String.class))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("key");
    assertThatThrownBy(() -> metadata.get("key", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("type");
  }
}
