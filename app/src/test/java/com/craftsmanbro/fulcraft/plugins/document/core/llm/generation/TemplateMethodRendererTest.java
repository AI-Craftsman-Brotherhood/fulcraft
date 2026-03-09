package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.MethodDocClassifier;
import org.junit.jupiter.api.Test;

class TemplateMethodRendererTest {

  private final TemplateMethodRenderer renderer =
      new TemplateMethodRenderer(new MethodDocClassifier());

  @Test
  void render_shouldReturnEmptyWhenMethodIsNull() {
    assertThat(renderer.render(null, 1, true)).isEmpty();
  }

  @Test
  void render_shouldRenderGetterSectionWithReturnExpressionInJapanese() {
    MethodInfo method = new MethodInfo();
    method.setName("getId");
    method.setSignature("String getId()");
    method.setSourceCode(
        """
            String getId() {
              return this.id;
            }
            """);

    String rendered = renderer.render(method, 2, true);

    assertThat(rendered).contains("### 3.2 getId");
    assertThat(rendered).contains("#### 3.2.3 事後条件");
    assertThat(rendered).contains("- `this.id` を直接返す。");
    assertThat(rendered).contains("- `getId` が `this.id` を返却すること。");
  }

  @Test
  void render_shouldRenderSetterSectionWithAssignmentInEnglish() {
    MethodInfo method = new MethodInfo();
    method.setName("setName");
    method.setSignature("void setName(String name)");
    method.setSourceCode(
        """
            void setName(String name) {
              this.name = name;
            }
            """);

    String rendered = renderer.render(method, 1, false);

    assertThat(rendered).contains("#### 3.1.2 Preconditions");
    assertThat(rendered).contains("- Argument is accepted as-is (including null).");
    assertThat(rendered).contains("- Assignment `this.name = name` is executed.");
    assertThat(rendered).contains("- Verify assignment outcome of `this.name = name`.");
    assertThat(rendered).contains("#### 3.1.6 Dependencies");
    assertThat(rendered).contains("- None");
  }

  @Test
  void render_shouldRenderEqualsSpecificTextInEnglish() {
    MethodInfo method = new MethodInfo();
    method.setName("equals");
    method.setSignature("boolean equals(Object other)");
    method.setSourceCode(
        """
            boolean equals(Object other) {
              return other != null;
            }
            """);

    String rendered = renderer.render(method, 3, false);

    assertThat(rendered).contains("- Compared argument may be null.");
    assertThat(rendered)
        .contains("- Verify equality results for equal, unequal, and null comparisons.");
  }

  @Test
  void render_shouldUseGetterFallbackTextsWhenNoReturnExpressionExists() {
    MethodInfo method = new MethodInfo();
    method.setName("getState");
    method.setSignature("String getState()");
    method.setSourceCode(
        """
            String getState() {
              log();
            }
            """);

    String rendered = renderer.render(method, 4, false);

    assertThat(rendered).contains("- Returns the corresponding value.");
    assertThat(rendered).contains("- Calling `getState` returns the stored value.");
    assertThat(rendered).contains("- Verify that `getState` return value matches expectation.");
  }

  @Test
  void render_shouldUseSetterFallbackTextsWhenAssignmentIsNotExtractable() {
    MethodInfo method = new MethodInfo();
    method.setName("setState");
    method.setSignature("void setState(String state)");
    method.setSourceCode(
        """
            void setState(String state) {
              return = state;
            }
            """);

    String rendered = renderer.render(method, 5, false);

    assertThat(rendered).contains("- Argument value is reflected to internal state.");
    assertThat(rendered).contains("- Verify that internal state is updated after `setState`.");
    assertThat(rendered).doesNotContain("Assignment `");
  }

  @Test
  void render_shouldRenderCanonicalAndUnknownTemplates() {
    MethodInfo toStringMethod = new MethodInfo();
    toStringMethod.setName("toString");
    toStringMethod.setSignature("String toString()");
    toStringMethod.setSourceCode("String toString() { return value; }");

    MethodInfo hashCodeMethod = new MethodInfo();
    hashCodeMethod.setName("hashCode");
    hashCodeMethod.setSignature("int hashCode()");
    hashCodeMethod.setSourceCode("int hashCode() { return 1; }");

    MethodInfo unknownMethod = new MethodInfo();
    unknownMethod.setName("process");
    unknownMethod.setSignature("void process()");
    unknownMethod.setSourceCode("void process() {}");

    String toStringRendered = renderer.render(toStringMethod, 6, false);
    String hashCodeRendered = renderer.render(hashCodeMethod, 7, false);
    String unknownRendered = renderer.render(unknownMethod, 8, false);

    assertThat(toStringRendered).contains("- Returns a string representation of the object.");
    assertThat(hashCodeRendered).contains("- Returns a hash value based on object state.");
    assertThat(unknownRendered).contains("- Operation completes.");
    assertThat(unknownRendered).contains("- Executes the `process` implementation.");
  }

  @Test
  void render_shouldRenderEqualsSpecificTextInJapanese() {
    MethodInfo method = new MethodInfo();
    method.setName("equals");
    method.setSignature("boolean equals(Object other)");
    method.setSourceCode(
        """
            boolean equals(Object other) {
              return other != null;
            }
            """);

    String rendered = renderer.render(method, 9, true);

    assertThat(rendered).contains("- 比較対象引数は null を許容する。");
    assertThat(rendered).contains("- 同値・非同値・null 比較の結果を検証する。");
  }

  @Test
  void render_shouldFallbackToUnknownNameAndSignatureWhenMethodFieldsAreMissing() {
    MethodInfo method = new MethodInfo();
    method.setName(null);
    method.setSignature(null);
    method.setSourceCode("");

    String rendered = renderer.render(method, 10, false);

    assertThat(rendered).contains("### 3.10 unknown");
    assertThat(rendered).contains("- Signature: `unknown()`");
    assertThat(rendered).contains("- Verify behavior of `unknown`.");
  }

  @Test
  void render_shouldUseNameBasedSignatureFallbackWhenSignatureIsMissing() {
    MethodInfo method = new MethodInfo();
    method.setName("process");
    method.setSignature(null);
    method.setSourceCode("void process() {}");

    String rendered = renderer.render(method, 11, false);

    assertThat(rendered).contains("- Signature: `process()`");
  }

  @Test
  void render_shouldRenderCanonicalAndUnknownTemplatesInJapanese() {
    MethodInfo toStringMethod = new MethodInfo();
    toStringMethod.setName("toString");
    toStringMethod.setSignature("String toString()");
    toStringMethod.setSourceCode("String toString() { return value; }");

    MethodInfo hashCodeMethod = new MethodInfo();
    hashCodeMethod.setName("hashCode");
    hashCodeMethod.setSignature("int hashCode()");
    hashCodeMethod.setSourceCode("int hashCode() { return 1; }");

    MethodInfo unknownMethod = new MethodInfo();
    unknownMethod.setName("work");
    unknownMethod.setSignature("void work()");
    unknownMethod.setSourceCode("void work() {}");

    String toStringRendered = renderer.render(toStringMethod, 12, true);
    String hashCodeRendered = renderer.render(hashCodeMethod, 13, true);
    String unknownRendered = renderer.render(unknownMethod, 14, true);

    assertThat(toStringRendered).contains("- オブジェクトの文字列表現を返す。");
    assertThat(hashCodeRendered).contains("- オブジェクト状態に基づくハッシュ値を返す。");
    assertThat(unknownRendered).contains("- 処理が完了する。");
    assertThat(unknownRendered).contains("- `work` の動作を検証する。");
  }

  @Test
  void render_shouldRenderGetterReturnExpressionInEnglish() {
    MethodInfo method = new MethodInfo();
    method.setName("getValue");
    method.setSignature("String getValue()");
    method.setSourceCode(
        """
            String getValue() {
              return cache.value;
            }
            """);

    String rendered = renderer.render(method, 15, false);

    assertThat(rendered).contains("- Returns `cache.value` directly.");
    assertThat(rendered).contains("- `getValue` returns `cache.value`.");
    assertThat(rendered).contains("- Verify that `getValue` returns `cache.value`.");
  }

  @Test
  void render_shouldRenderSetterAssignmentInJapanese() {
    MethodInfo method = new MethodInfo();
    method.setName("setValue");
    method.setSignature("void setValue(String value)");
    method.setSourceCode(
        """
            void setValue(String value) {
              this.value = value;
            }
            """);

    String rendered = renderer.render(method, 16, true);

    assertThat(rendered).contains("- 代入 `this.value = value` が実行される。");
    assertThat(rendered).contains("- `setValue` は `this.value = value` を実行する。");
    assertThat(rendered).contains("- `this.value = value` の代入結果を検証する。");
  }
}
