package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.MockHintStrategy;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.ResultBuilder;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.FieldInfo;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MockHintAnalyzerTest {

  @Test
  void collectsInterfaceAbstractAndConstructorTypes() {
    ClassOrInterfaceDeclaration classDecl = parseSampleClass();

    Set<String> interfaces = MockHintAnalyzer.collectInterfaceNames(classDecl);
    Set<String> abstractClasses = MockHintAnalyzer.collectAbstractClassNames(classDecl);
    Set<String> constructorParams = MockHintAnalyzer.collectConstructorParamTypes(classDecl);

    assertTrue(interfaces.contains("Repo"));
    assertTrue(abstractClasses.contains("AbstractService"));
    assertTrue(constructorParams.contains("Repo"));
    assertTrue(constructorParams.contains("PaymentService"));
    assertTrue(constructorParams.contains("AbstractService"));
  }

  @Test
  void analyzeFields_setsMockHintsAndInjectableFlags() {
    ClassOrInterfaceDeclaration classDecl = parseSampleClass();

    List<FieldInfo> fieldInfos = buildFieldInfos(classDecl);
    MockHintAnalyzer.analyzeFields(classDecl, fieldInfos);

    Map<String, FieldInfo> byName =
        fieldInfos.stream().collect(Collectors.toMap(FieldInfo::getName, Function.identity()));

    FieldInfo repo = byName.get("repo");
    assertEquals(MockHintStrategy.HINT_REQUIRED, repo.getMockHint());
    assertTrue(repo.isInjectable());

    FieldInfo abstractService = byName.get("abstractService");
    assertEquals(MockHintStrategy.HINT_REQUIRED, abstractService.getMockHint());
    assertTrue(abstractService.isInjectable());

    FieldInfo paymentService = byName.get("paymentService");
    assertEquals(MockHintStrategy.HINT_RECOMMENDED, paymentService.getMockHint());
    assertTrue(paymentService.isInjectable());

    FieldInfo gateway = byName.get("gateway");
    assertEquals(MockHintStrategy.HINT_RECOMMENDED, gateway.getMockHint());
    assertFalse(gateway.isInjectable());

    FieldInfo count = byName.get("count");
    assertNull(count.getMockHint());
  }

  private static ClassOrInterfaceDeclaration parseSampleClass() {
    String source =
        """
        package com.example;

        public class Sample {
          interface Repo {}
          abstract class AbstractService {}

          private final Repo repo;
          private final AbstractService abstractService;
          private final PaymentService paymentService;
          private Gateway gateway;
          private int count;

          public Sample(Repo repo, AbstractService abstractService, PaymentService paymentService) {
            this.repo = repo;
            this.abstractService = abstractService;
            this.paymentService = paymentService;
          }
        }

        class PaymentService {}
        class Gateway {}
        """;

    CompilationUnit cu = StaticJavaParser.parse(source);
    return cu.getClassByName("Sample").orElseThrow();
  }

  private static List<FieldInfo> buildFieldInfos(ClassOrInterfaceDeclaration classDecl) {
    List<FieldInfo> fields = new ArrayList<>();
    for (FieldDeclaration field : classDecl.getFields()) {
      String visibility = field.getAccessSpecifier().asString().toLowerCase(Locale.ROOT);
      boolean isStatic = field.isStatic();
      boolean isFinal = field.isFinal();
      for (VariableDeclarator declarator : field.getVariables()) {
        fields.add(
            ResultBuilder.fieldInfo(
                declarator.getNameAsString(),
                declarator.getType().asString(),
                visibility,
                isStatic,
                isFinal));
      }
    }
    return fields;
  }
}
