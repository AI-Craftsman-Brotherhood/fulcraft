package com.craftsmanbro.fulcraft.plugins.analysis.core.service.metric;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisError;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MethodDerivedMetricsComputerTest {

  private final MethodDerivedMetricsComputer computer = new MethodDerivedMetricsComputer();

  @Test
  void setsLoopsAndConditionalsForComplexMethod() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
            public Invoice calculateInvoice(SimpleCustomerData customer, java.util.List<Item> items, String currency, boolean applyDiscount, java.time.LocalDate date) {
                double total = 0;
                for (Item item : items) {
                    if (item.isBillable()) {
                        total += item.price();
                    }
                }
                return applyDiscount ? applyDiscount(total) : total;
            }
            """);

    Optional<AnalysisError> error = computer.compute(method);

    assertThat(error).isEmpty();
    assertThat(method.hasLoops()).isTrue();
    assertThat(method.hasConditionals()).isTrue();
  }

  @Test
  void leavesFlagsFalseWhenNoControlFlowFound() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode("public Foo() { this.value = 1; }");
    method.setHasLoops(false);
    method.setHasConditionals(false);

    Optional<AnalysisError> error = computer.compute(method);

    assertThat(error).isEmpty();
    assertThat(method.hasLoops()).isFalse();
    assertThat(method.hasConditionals()).isFalse();
  }

  @Test
  void returnsErrorWhenSourceMissing() {
    MethodInfo method = new MethodInfo();
    method.setHasLoops(true);
    method.setHasConditionals(false);

    Optional<AnalysisError> error = computer.compute(method, "src/Some.java");

    assertThat(error).isPresent();
    assertThat(error.get().filePath()).isEqualTo("src/Some.java");
    assertThat(method.hasLoops()).isTrue();
    assertThat(method.hasConditionals()).isFalse();
  }

  @Test
  void detectsConditionalsAfterStringWithLineCommentMarker() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
            public void example(boolean flag) {
                String url = "http://craftsmann-bro.com"; if (flag) { return; }
            }
            """);

    Optional<AnalysisError> error = computer.compute(method);

    assertThat(error).isEmpty();
    assertThat(method.hasConditionals()).isTrue();
  }

  @Test
  void ignoresControlFlowInsideTextBlock() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
            public void example() {
                String template = \"\"\"
                    if (value) { return; }
                    for (int i = 0; i < 1; i++) {}
                    while (true) { break; }
                \"\"\";
                int value = 1;
            }
            """);

    Optional<AnalysisError> error = computer.compute(method);

    assertThat(error).isEmpty();
    assertThat(method.hasLoops()).isFalse();
    assertThat(method.hasConditionals()).isFalse();
  }

  @Test
  void returnsEmptyWhenMethodIsNull() {
    Optional<AnalysisError> error = computer.compute(null, "src/Any.java");

    assertThat(error).isEmpty();
  }

  @Test
  void returnsErrorWithUnknownPathWhenFilePathIsBlank() {
    MethodInfo method = new MethodInfo();

    Optional<AnalysisError> error = computer.compute(method, "  ");

    assertThat(error).isPresent();
    assertThat(error.get().filePath()).isEqualTo("unknown");
  }

  @Test
  void ignoresKeywordsInsideCommentsAndStringLiterals() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
            public void example() {
                // if (flag) { while (true) {} }
                /* for (;;) { switch (x) {} } */
                String message = "catch (Exception e) { do {} while(false); }";
                char marker = '?';
                int value = 1;
            }
            """);

    Optional<AnalysisError> error = computer.compute(method);

    assertThat(error).isEmpty();
    assertThat(method.hasLoops()).isFalse();
    assertThat(method.hasConditionals()).isFalse();
  }

  @Test
  void detectsDoLoopAndCatchConditional() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
            public void process() {
                int i = 0;
                do {
                    i++;
                } while (i < 2);
                try {
                    call();
                } catch (RuntimeException ex) {
                    recover(ex);
                }
            }
            """);

    Optional<AnalysisError> error = computer.compute(method);

    assertThat(error).isEmpty();
    assertThat(method.hasLoops()).isTrue();
    assertThat(method.hasConditionals()).isTrue();
  }
}
