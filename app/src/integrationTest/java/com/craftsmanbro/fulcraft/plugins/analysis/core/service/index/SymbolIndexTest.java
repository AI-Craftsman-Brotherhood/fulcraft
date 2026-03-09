package com.craftsmanbro.fulcraft.plugins.analysis.core.service.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SymbolIndexTest {

  @TempDir Path tempDir;

  @Test
  void build_requiresNonNullSourceRoots() {
    assertThatThrownBy(() -> SymbolIndex.build(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void wrap_requiresNonNullIndex() {
    assertThatThrownBy(() -> SymbolIndex.wrap(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void build_createsIndexAndDelegatesQueries() throws Exception {
    Path srcRoot = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcRoot);
    Files.writeString(
        srcRoot.resolve("Widget.java"),
        """
        package com.example;
        class Widget {
          void ping() {}
        }
        """);

    SymbolIndex index = SymbolIndex.build(List.of(tempDir.resolve("src/main/java")));

    assertThat(index.hasClass("com.example.Widget")).isTrue();
    assertThat(index.hasMethod("Widget", "ping")).isTrue();
  }

  @Test
  void wrap_exposesUnderlyingIndex() {
    ProjectSymbolIndex delegate = new ProjectSymbolIndex();
    delegate.addClass("com.example.Foo");

    SymbolIndex index = SymbolIndex.wrap(delegate);

    assertThat(index.hasClass("Foo")).isTrue();
    assertThat(index.unwrap()).isSameAs(delegate);
  }

  @Test
  void wrap_delegatesAllQueryMethods() {
    ProjectSymbolIndex delegate = new ProjectSymbolIndex();
    delegate.addClass("com.example.Task");
    delegate.addMethod("com.example.Task", "run", "run()", 0);
    delegate.addMethod("com.example.Task", "run", "run(String)", 1);
    delegate.addField("com.example.Task", "id", "String");

    SymbolIndex index = SymbolIndex.wrap(delegate);

    assertThat(index.hasMethodArity("Task", "run", 1)).isTrue();
    assertThat(index.getMethodOverloadCount("Task", "run")).isEqualTo(2);
    assertThat(index.hasMethodSignature("Task", "run(String)")).isTrue();
    assertThat(index.hasField("Task", "id")).isTrue();
    assertThat(index.getFields("com.example.Task")).containsEntry("id", "String");
    assertThat(index.findClassCandidates("Task", 5)).containsExactly("com.example.Task");
  }
}
