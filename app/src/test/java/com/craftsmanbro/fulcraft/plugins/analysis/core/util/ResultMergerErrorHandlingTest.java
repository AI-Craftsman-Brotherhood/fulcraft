package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisError;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultMergerErrorHandlingTest {

  @Test
  void fallbackParseProducesWarnNotErrorAndDedupes() {
    MethodInfo method = new MethodInfo();
    method.setName("a");
    method.setSignature("a()");
    method.setMethodId("C#a()");
    method.setSourceCode(
        """
            public void a() {}
            public void b() {}
            """);

    ClassInfo cls = new ClassInfo();
    cls.setFqn("C");
    cls.setFilePath("C.java");
    cls.setMethods(new ArrayList<>(List.of(method)));

    AnalysisResult primary = new AnalysisResult();
    primary.setClasses(List.of(cls));
    // pre-populate duplicate error
    primary.setAnalysisErrors(
        new ArrayList<>(
            List.of(
                new AnalysisError(
                    "C.java",
                    "Unable to parse method for branch summary",
                    null,
                    "C#a()",
                    AnalysisError.Severity.ERROR))));

    AnalysisResult merged = new ResultMerger().merge(primary, new AnalysisResult());

    assertThat(merged.getAnalysisErrors()).hasSize(1);
    AnalysisError err = merged.getAnalysisErrors().getFirst();
    assertThat(err.severity()).isEqualTo(AnalysisError.Severity.WARN);
    assertThat(err.methodId()).isEqualTo("C#a()");
    assertThat(err.message()).contains("fallback_used=true");
  }

  @Test
  void constructorBranchSummaryDoesNotProduceErrorSeverity() {
    MethodInfo ctor = new MethodInfo();
    ctor.setName("ComplexInvoiceService");
    ctor.setSignature("ComplexInvoiceService()");
    ctor.setSourceCode("public ComplexInvoiceService() { this.totalAmount = 0; }");

    ClassInfo cls = new ClassInfo();
    cls.setFqn("com.example.legacy.ComplexInvoiceService");
    cls.setFilePath("ComplexInvoiceService.java");
    cls.setMethods(new ArrayList<>(List.of(ctor)));

    AnalysisResult primary = new AnalysisResult();
    primary.setClasses(List.of(cls));

    AnalysisResult merged = new ResultMerger().merge(primary, new AnalysisResult());

    assertThat(merged.getAnalysisErrors())
        .noneMatch(err -> err.severity() == AnalysisError.Severity.ERROR);
    MethodInfo mergedCtor = merged.getClasses().getFirst().getMethods().getFirst();
    assertThat(mergedCtor.getBranchSummary()).isNotNull();
  }
}
