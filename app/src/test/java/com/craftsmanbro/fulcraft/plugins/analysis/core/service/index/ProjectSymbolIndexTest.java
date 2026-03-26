package com.craftsmanbro.fulcraft.plugins.analysis.core.service.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectSymbolIndexTest {

  @Test
  void addClass_ignoresNullAndBlankValues() {
    ProjectSymbolIndex index = new ProjectSymbolIndex();

    index.addClass(null);
    index.addClass(" ");

    assertThat(index.hasClass("Foo")).isFalse();
    assertThat(index.findClassCandidates("Foo", 10)).isEmpty();
  }

  @Test
  void addClass_tracksSimpleNamesAndCandidates() {
    ProjectSymbolIndex index = new ProjectSymbolIndex();
    index.addClass("com.zeta.Foo");
    index.addClass("com.alpha.Foo");
    index.addClass("com.alpha.Bar");

    assertThat(index.hasClass("com.zeta.Foo")).isTrue();
    assertThat(index.hasClass("Foo")).isTrue();
    assertThat(index.hasClass("Bar")).isTrue();
    assertThat(index.hasClass("Baz")).isFalse();

    assertThat(index.findClassCandidates("Foo", 0))
        .containsExactly("com.alpha.Foo", "com.zeta.Foo");
    assertThat(index.findClassCandidates("Foo", 1)).containsExactly("com.alpha.Foo");
  }

  @Test
  void hasMethod_fallsBackToGlobalNamesWhenClassUnknown() {
    ProjectSymbolIndex index = new ProjectSymbolIndex();
    index.addMethod("com.example.Foo", "run", "run(String)", 1);

    assertThat(index.hasMethod("com.unknown.Bar", "run")).isTrue();
    assertThat(index.hasMethod("com.unknown.Bar", "missing")).isFalse();
    assertThat(index.hasMethod("com.example.Foo", "run")).isTrue();
    assertThat(index.hasMethod("com.example.Foo", " ")).isFalse();
  }

  @Test
  void hasMethodArity_requiresCandidatesUnlessNegativeParamCount() {
    ProjectSymbolIndex index = new ProjectSymbolIndex();
    index.addClass("com.example.Foo");
    index.addMethod("com.example.Foo", "run", "run(String)", 1);

    assertThat(index.hasMethodArity("Foo", "run", 1)).isTrue();
    assertThat(index.hasMethodArity("Foo", "run", 2)).isFalse();
    assertThat(index.hasMethodArity("Unknown", "run", 1)).isFalse();
    assertThat(index.hasMethodArity("Unknown", "run", -1)).isTrue();
  }

  @Test
  void addMethod_ignoresInvalidArguments() {
    ProjectSymbolIndex index = new ProjectSymbolIndex();

    index.addMethod(null, "run", "run()", 0);
    index.addMethod(" ", "run", "run()", 0);
    index.addMethod("com.example.Foo", " ", "run()", 0);
    index.addMethod("com.example.Foo", null, "run()", 0);

    assertThat(index.hasClass("com.example.Foo")).isFalse();
    assertThat(index.hasMethod("com.example.Foo", "run")).isFalse();
  }

  @Test
  void getMethodOverloadCount_prefersSignaturesThenAritiesThenNames() {
    ProjectSymbolIndex index = new ProjectSymbolIndex();
    index.addMethod("com.example.Foo", "work", "work()", 0);
    index.addMethod("com.example.Foo", "work", "work(String)", 1);
    index.addMethod("com.example.Bar", "ping", null, 0);
    index.addMethod("com.example.Bar", "ping", null, 2);
    index.addMethod("com.example.Baz", "touch", null, -1);

    assertThat(index.getMethodOverloadCount("com.example.Foo", "work")).isEqualTo(2);
    assertThat(index.getMethodOverloadCount("com.example.Bar", "ping")).isEqualTo(2);
    assertThat(index.getMethodOverloadCount("com.example.Baz", "touch")).isEqualTo(1);
    assertThat(index.getMethodOverloadCount("com.example.Missing", "work")).isEqualTo(0);
  }

  @Test
  void getMethodOverloadCount_returnsMaxAcrossSimpleNameCandidates() {
    ProjectSymbolIndex index = new ProjectSymbolIndex();
    index.addMethod("com.first.Worker", "execute", "execute()", 0);
    index.addMethod("com.first.Worker", "execute", "execute(String)", 1);
    index.addMethod("com.second.Worker", "execute", "execute()", 0);

    assertThat(index.getMethodOverloadCount("Worker", "execute")).isEqualTo(2);
  }

  @Test
  void hasMethodSignature_checksCandidates() {
    ProjectSymbolIndex index = new ProjectSymbolIndex();
    index.addClass("com.example.Foo");
    index.addMethod("com.example.Foo", "run", "run(String)", 1);

    assertThat(index.hasMethodSignature("Foo", "run(String)")).isTrue();
    assertThat(index.hasMethodSignature("Foo", "run()")).isFalse();
    assertThat(index.hasMethodSignature("Unknown", "run(String)")).isFalse();
    assertThat(index.hasMethodSignature("Foo", " ")).isFalse();
  }

  @Test
  void addField_tracksNamesAndResolvesAmbiguity() {
    ProjectSymbolIndex index = new ProjectSymbolIndex();
    index.addClass("com.example.Foo");
    index.addField("com.example.Foo", "count", "int");
    index.addField("com.example.Foo", "count", "int");
    index.addField("com.example.Foo", "count", "long");

    Map<String, String> fields = index.getFields("com.example.Foo");
    assertThat(fields).containsEntry("count", "ambiguous");

    assertThat(index.hasField("Foo", "count")).isTrue();
    assertThat(index.hasField("Unknown", "count")).isTrue();
    assertThat(index.hasField("Foo", " ")).isFalse();
    assertThat(index.getFields("com.example.Missing")).isEmpty();
  }

  @Test
  void addField_storesEmptyTypeWhenNullTypeGiven() {
    ProjectSymbolIndex index = new ProjectSymbolIndex();
    index.addField("com.example.Foo", "name", null);

    assertThat(index.getFields("com.example.Foo")).containsEntry("name", "");
    assertThat(index.hasField("Foo", "name")).isTrue();
  }

  @Test
  void findClassCandidates_returnsEmptyForBlankSimpleName() {
    ProjectSymbolIndex index = new ProjectSymbolIndex();
    assertThat(index.findClassCandidates(" ", 10)).isEmpty();
    assertThat(index.findClassCandidates(null, 10)).isEmpty();
  }

  @Test
  void findClassCandidates_returnsSortedListWhenLimitIsZeroOrNegative() {
    ProjectSymbolIndex index = new ProjectSymbolIndex();
    index.addClass("com.zeta.Foo");
    index.addClass("com.alpha.Foo");

    assertThat(index.findClassCandidates("Foo", 0))
        .containsExactly("com.alpha.Foo", "com.zeta.Foo");
    assertThat(index.findClassCandidates("Foo", -1))
        .containsExactly("com.alpha.Foo", "com.zeta.Foo");
  }
}
