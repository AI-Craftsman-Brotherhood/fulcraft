package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ResolutionStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisContextTest {

  private AnalysisContext context;

  @BeforeEach
  void setUp() {
    context = new AnalysisContext();
  }

  @Test
  @DisplayName("Initial state should have empty but non-null maps")
  void initialState() {
    assertThat(context.getMethodInfos()).isNotNull().isEmpty();
    assertThat(context.getCallGraph()).isNotNull().isEmpty();
    assertThat(context.getCallArgumentLiterals("missing")).isNull();
    assertThat(context.getIncomingCounts()).isNotNull().isEmpty();
    assertThat(context.getMethodHasBody()).isNotNull().isEmpty();
    assertThat(context.getMethodVisibility()).isNotNull().isEmpty();
    assertThat(context.getMethodClass()).isNotNull().isEmpty();
    assertThat(context.getMethodCodeHash()).isNotNull().isEmpty();

    // Check default values
    assertThat(context.getCallGraphSource()).isEqualTo("analysis");
    assertThat(context.isCallGraphResolved()).isFalse();
  }

  @Test
  @DisplayName("getOrCreateCallGraphEntry should create new set if missing")
  void getOrCreateCallGraphEntry_New() {
    String methodKey = "com.example.Foo#bar()";
    Set<String> callees = context.getOrCreateCallGraphEntry(methodKey);

    assertThat(callees).isNotNull().isEmpty();
    assertThat(context.getCallGraph()).containsKey(methodKey);
  }

  @Test
  @DisplayName("getOrCreateCallGraphEntry should return existing set")
  void getOrCreateCallGraphEntry_Existing() {
    String methodKey = "com.example.Foo#bar()";
    Set<String> initialSet = context.getOrCreateCallGraphEntry(methodKey);
    initialSet.add("callee");

    Set<String> retrievedSet = context.getOrCreateCallGraphEntry(methodKey);

    assertThat(retrievedSet).isSameAs(initialSet);
    assertThat(retrievedSet).containsExactly("callee");
  }

  @Test
  @DisplayName("recordCallStatus should ignore null inputs")
  void recordCallStatus_Nulls() {
    context.recordCallStatus(null, "callee", ResolutionStatus.RESOLVED);
    context.recordCallStatus("caller", null, ResolutionStatus.RESOLVED);
    context.recordCallStatus("caller", "callee", null);

    assertThat(context.getCallStatuses("caller")).isNull();
  }

  @Test
  @DisplayName("recordCallStatus should record status")
  void recordCallStatus_Basic() {
    context.recordCallStatus("caller", "callee", ResolutionStatus.RESOLVED);

    Map<String, ResolutionStatus> statuses = context.getCallStatuses("caller");
    assertThat(statuses).containsEntry("callee", ResolutionStatus.RESOLVED);
  }

  @Test
  @DisplayName("recordCallArgumentLiterals should record and merge distinct literals")
  void recordCallArgumentLiterals_BasicAndMerge() {
    context.recordCallArgumentLiterals(
        "caller", "callee", java.util.List.of("\"Order is being processed\"", "\"Order ready\""));
    context.recordCallArgumentLiterals(
        "caller", "callee", java.util.List.of("\"Order ready\"", "\"Order dispatched\""));
    context.recordCallArgumentLiterals("caller", "callee", java.util.List.of("   "));
    context.recordCallArgumentLiterals("caller", null, java.util.List.of("\"ignored\""));

    Map<String, java.util.List<String>> literals = context.getCallArgumentLiterals("caller");
    assertThat(literals).isNotNull();
    assertThat(literals.get("callee"))
        .containsExactly("\"Order is being processed\"", "\"Order ready\"", "\"Order dispatched\"");
  }

  @Test
  @DisplayName("recordCallArgumentLiterals should normalize, trim, and deduplicate in order")
  void recordCallArgumentLiterals_NormalizeAndDeduplicate() {
    context.recordCallArgumentLiterals(
        "caller", "callee", List.of("  \"A\"  ", "\"A\"", "\"B\" ", "   ", "\"B\""));

    Map<String, List<String>> literals = context.getCallArgumentLiterals("caller");
    assertThat(literals).isNotNull();
    assertThat(literals.get("callee")).containsExactly("\"A\"", "\"B\"");
  }

  @Test
  @DisplayName("recordCallArgumentLiterals should keep caller and callee scopes independent")
  void recordCallArgumentLiterals_CallerAndCalleeScoped() {
    context.recordCallArgumentLiterals("caller1", "callee1", List.of("\"x\""));
    context.recordCallArgumentLiterals("caller1", "callee2", List.of("\"y\""));
    context.recordCallArgumentLiterals("caller2", "callee1", List.of("\"z\""));

    assertThat(context.getCallArgumentLiterals("caller1"))
        .containsOnlyKeys("callee1", "callee2")
        .containsEntry("callee1", List.of("\"x\""))
        .containsEntry("callee2", List.of("\"y\""));
    assertThat(context.getCallArgumentLiterals("caller2"))
        .containsOnlyKeys("callee1")
        .containsEntry("callee1", List.of("\"z\""));
  }

  @Test
  @DisplayName("recordCallArgumentLiterals should ignore invalid inputs")
  void recordCallArgumentLiterals_InvalidInputs() {
    context.recordCallArgumentLiterals(null, "callee", List.of("\"x\""));
    context.recordCallArgumentLiterals("caller", null, List.of("\"x\""));
    context.recordCallArgumentLiterals("caller", "callee", null);
    context.recordCallArgumentLiterals("caller", "callee", List.of());
    context.recordCallArgumentLiterals("caller", "callee", List.of("   "));

    assertThat(context.getCallArgumentLiterals("caller")).isNull();
  }

  @Test
  @DisplayName("recordCallStatus should merge statuses correctly (RESOLVED is sticky)")
  void recordCallStatus_Merge() {
    String caller = "caller";
    String callee = "callee";

    // First UNRESOLVED
    context.recordCallStatus(caller, callee, ResolutionStatus.UNRESOLVED);
    assertThat(context.getCallStatuses(caller)).containsEntry(callee, ResolutionStatus.UNRESOLVED);

    // Update to RESOLVED
    context.recordCallStatus(caller, callee, ResolutionStatus.RESOLVED);
    assertThat(context.getCallStatuses(caller)).containsEntry(callee, ResolutionStatus.RESOLVED);

    // Try to revert to UNRESOLVED (should stay RESOLVED)
    context.recordCallStatus(caller, callee, ResolutionStatus.UNRESOLVED);
    assertThat(context.getCallStatuses(caller)).containsEntry(callee, ResolutionStatus.RESOLVED);
  }

  @Test
  @DisplayName("recordCallStatus should keep ambiguous status over unresolved updates")
  void recordCallStatus_PrefersAmbiguousOverUnresolved() {
    String caller = "caller";
    String callee = "callee";

    context.recordCallStatus(caller, callee, ResolutionStatus.AMBIGUOUS);
    context.recordCallStatus(caller, callee, ResolutionStatus.UNRESOLVED);

    assertThat(context.getCallStatuses(caller)).containsEntry(callee, ResolutionStatus.AMBIGUOUS);
  }

  @Test
  @DisplayName("Setters and getters for simple properties")
  void simpleProperties() {
    context.setCallGraphSource("spoon");
    assertThat(context.getCallGraphSource()).isEqualTo("spoon");

    context.setCallGraphResolved(true);
    assertThat(context.isCallGraphResolved()).isTrue();
  }

  @Test
  @DisplayName("Accessing various maps directly")
  void mapAccess() {
    // Just verifying we can put and get from the exposed maps
    context.getMethodInfos().put("key", new MethodInfo());
    assertThat(context.getMethodInfos()).containsKey("key");

    context.getIncomingCounts().put("key", 5);
    assertThat(context.getIncomingCounts()).containsEntry("key", 5);

    context.getMethodHasBody().put("key", true);
    assertThat(context.getMethodHasBody()).containsEntry("key", true);

    context.getMethodVisibility().put("key", "public");
    assertThat(context.getMethodVisibility()).containsEntry("key", "public");

    context.getMethodClass().put("key", "MyClass");
    assertThat(context.getMethodClass()).containsEntry("key", "MyClass");

    context.getMethodCodeHash().put("key", "hash123");
    assertThat(context.getMethodCodeHash()).containsEntry("key", "hash123");
  }
}
