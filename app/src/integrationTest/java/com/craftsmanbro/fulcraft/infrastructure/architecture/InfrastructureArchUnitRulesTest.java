package com.craftsmanbro.fulcraft.infrastructure.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InfrastructureArchUnitRulesTest {

  private static final String INFRA_PACKAGE = "com.craftsmanbro.fulcraft.infrastructure..";
  private static final String PLUGIN_PACKAGE_PREFIX = "com.craftsmanbro.fulcraft.plugins.";
  private static final Set<String> LEGACY_NON_PORT_CONTRACT_NAMES =
      Set.of("TasksFileFormat", "TasksFileReader", "LlmClientProvider", "TokenUsageAware");
  private static final Set<String> ALLOWED_PLUGIN_DEPENDENCY_CLASSES =
      Set.of(
          "com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.DefaultBuildTool",
          "com.craftsmanbro.fulcraft.infrastructure.coverage.impl.CoverageLoaderAdapterFactory",
          "com.craftsmanbro.fulcraft.infrastructure.coverage.impl.CoverageLoaderAdapterFactory$CoverageLoaderBridge",
          "com.craftsmanbro.fulcraft.infrastructure.coverage.impl.JacocoCoverageAdapter",
          "com.craftsmanbro.fulcraft.infrastructure.fs.impl.SourceFileManager",
          "com.craftsmanbro.fulcraft.infrastructure.io.impl.JsonTasksFileFormat",
          "com.craftsmanbro.fulcraft.infrastructure.io.impl.JsonlTasksFileFormat",
          "com.craftsmanbro.fulcraft.infrastructure.io.impl.JsonlTasksFileFormat$EntryIterator",
          "com.craftsmanbro.fulcraft.infrastructure.io.impl.StructuredTasksFileReader",
          "com.craftsmanbro.fulcraft.infrastructure.io.impl.StructuredTasksFileReader$EntryIterator",
          "com.craftsmanbro.fulcraft.infrastructure.io.impl.YamlTasksFileFormat",
          "com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileFormat",
          "com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileEntry",
          "com.craftsmanbro.fulcraft.infrastructure.llm.impl.config.LlmConfigResolver",
          "com.craftsmanbro.fulcraft.infrastructure.parser.impl.xml.JacocoXmlReportParser",
          "com.craftsmanbro.fulcraft.infrastructure.parser.impl.xml.StaticAnalysisXmlReportParser");

  @Test
  void infrastructureMustNotDependOnCoreFeaturePlugins() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(INFRA_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.craftsmanbro.fulcraft.plugins.analysis..",
                "com.craftsmanbro.fulcraft.plugins.reporting..",
                "com.craftsmanbro.fulcraft.plugins.document..",
                "com.craftsmanbro.fulcraft.plugins.exploration..");

    rule.check(importedClasses());
  }

  @Test
  void infrastructureMustNotDependOnKernelLayer() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(INFRA_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.craftsmanbro.fulcraft.kernel..");

    rule.check(importedClasses());
  }

  @Test
  void infrastructurePluginDependenciesMustBeAllowlisted() {
    ArchRule rule =
        classes().that().resideInAPackage(INFRA_PACKAGE).should(useOnlyAllowlistedPluginDeps());

    rule.check(importedClasses());
  }

  @Test
  void contractPackageMustContainOnlyInterfaces() {
    ArchRule rule =
        classes().that().resideInAPackage(INFRA_PACKAGE + "contract..").should().beInterfaces();

    rule.check(importedClasses());
  }

  @Test
  void contractInterfaceNamesMustUsePortSuffixOrDocumentedLegacyNames() {
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage(INFRA_PACKAGE + "contract..")
            .and()
            .areInterfaces()
            .should(havePortSuffixOrLegacyException());

    rule.check(importedClasses());
  }

  @Test
  void internalPackageMustNotExposePublicTypes() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(INFRA_PACKAGE + "internal..")
            .should()
            .bePublic()
            .allowEmptyShould(true);

    rule.check(importedClasses());
  }

  @Test
  void infrastructureClassesMustResideInCanonicalFeatureSubpackages() {
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage(INFRA_PACKAGE)
            .should()
            .resideInAnyPackage(
                "com.craftsmanbro.fulcraft.infrastructure..contract..",
                "com.craftsmanbro.fulcraft.infrastructure..model..",
                "com.craftsmanbro.fulcraft.infrastructure..impl..",
                "com.craftsmanbro.fulcraft.infrastructure..exception..",
                "com.craftsmanbro.fulcraft.infrastructure..internal..");

    rule.check(importedClasses());
  }

  private static JavaClasses importedClasses() {
    return new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeJars())
        .importPath(Path.of("build/classes/java/main"));
  }

  private static ArchCondition<JavaClass> havePortSuffixOrLegacyException() {
    return new ArchCondition<>("have name ending with Port or listed in legacy exceptions") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        String simpleName = item.getSimpleName();
        boolean accepted =
            simpleName.endsWith("Port") || LEGACY_NON_PORT_CONTRACT_NAMES.contains(simpleName);
        if (!accepted) {
          String message =
              item.getFullName()
                  + " must end with 'Port' or be added to LEGACY_NON_PORT_CONTRACT_NAMES";
          events.add(SimpleConditionEvent.violated(item, message));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> useOnlyAllowlistedPluginDeps() {
    return new ArchCondition<>("use plugin dependencies only from allowlisted classes") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (ALLOWED_PLUGIN_DEPENDENCY_CLASSES.contains(item.getFullName())) {
          return;
        }

        List<String> pluginDependencies =
            item.getDirectDependenciesFromSelf().stream()
                .map(dependency -> dependency.getTargetClass().getFullName())
                .filter(name -> name.startsWith(PLUGIN_PACKAGE_PREFIX))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        if (!pluginDependencies.isEmpty()) {
          String message =
              item.getFullName()
                  + " must not depend on plugin classes directly: "
                  + String.join(", ", pluginDependencies);
          events.add(SimpleConditionEvent.violated(item, message));
        }
      }
    };
  }
}
