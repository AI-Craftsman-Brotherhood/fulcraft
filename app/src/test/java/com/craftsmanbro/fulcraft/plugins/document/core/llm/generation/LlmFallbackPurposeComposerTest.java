package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmFallbackPurposeComposerTest {

  private final LlmFallbackPurposeComposer composer = new LlmFallbackPurposeComposer();

  @Test
  void composePurposeLines_shouldEmitServiceRoleAndPrincipalOperations() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.OrderService");

    MethodInfo process = new MethodInfo();
    process.setName("processOrder");
    MethodInfo cancel = new MethodInfo();
    cancel.setName("cancelOrder");
    MethodInfo getter = new MethodInfo();
    getter.setName("getStatus");

    List<String> lines =
        composer.composePurposeLines(classInfo, List.of(process, cancel, getter), true);

    assertThat(lines).anyMatch(line -> line.contains("サービス層"));
    assertThat(lines).anyMatch(line -> line.contains("`processOrder`"));
    assertThat(lines).anyMatch(line -> line.contains("`cancelOrder`"));
  }

  @Test
  void composePurposeLines_shouldClassifyMostlyAccessorClassAsDataHolder() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.CustomerRecord");

    FieldInfo id = new FieldInfo();
    id.setName("id");
    classInfo.setFields(List.of(id));

    MethodInfo constructor = new MethodInfo();
    constructor.setName("CustomerRecord");
    MethodInfo getId = new MethodInfo();
    getId.setName("getId");
    MethodInfo setId = new MethodInfo();
    setId.setName("setId");

    List<String> lines =
        composer.composePurposeLines(classInfo, List.of(constructor, getId, setId), true);

    assertThat(lines).anyMatch(line -> line.contains("データ保持"));
    assertThat(lines).noneMatch(line -> line.contains("`getId`"));
  }

  @Test
  void composePurposeLines_shouldReturnEmptyWhenClassIsNull() {
    List<String> lines = composer.composePurposeLines(null, List.of(), true);

    assertThat(lines).isEmpty();
  }

  @Test
  void composePurposeLines_shouldUseRepositoryRoleAndFieldFactsWhenOnlyAccessorsExist() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.OrderRepository");

    FieldInfo id = new FieldInfo();
    id.setName("id");
    FieldInfo status = new FieldInfo();
    status.setName("status");
    classInfo.setFields(List.of(id, status));

    MethodInfo getId = new MethodInfo();
    getId.setName("getId");
    MethodInfo setId = new MethodInfo();
    setId.setName("setId");

    List<String> lines = composer.composePurposeLines(classInfo, List.of(getId, setId), true);

    assertThat(lines).anyMatch(line -> line.contains("永続化アクセス層"));
    assertThat(lines).anyMatch(line -> line.contains("`id`"));
    assertThat(lines).anyMatch(line -> line.contains("`status`"));
  }

  @Test
  void composePurposeLines_shouldFallbackToMethodCountWhenNoOperationOrFieldFacts() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.OrderUtils");

    List<String> lines = composer.composePurposeLines(classInfo, List.of(), false);

    assertThat(lines).anyMatch(line -> line.contains("utility component"));
    assertThat(lines).anyMatch(line -> line.contains("Detected 0 specification methods."));
  }

  @Test
  void composePurposeLines_shouldUseDomainRoleAndLimitOperationListToThree() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.OrderManager");

    MethodInfo classNameLike = new MethodInfo();
    classNameLike.setName("OrderManager");
    MethodInfo op1 = new MethodInfo();
    op1.setName("createOrder");
    MethodInfo op2 = new MethodInfo();
    op2.setName("updateOrder");
    MethodInfo op3 = new MethodInfo();
    op3.setName("deleteOrder");
    MethodInfo op4 = new MethodInfo();
    op4.setName("archiveOrder");
    MethodInfo getter = new MethodInfo();
    getter.setName("getStatus");

    List<String> lines =
        composer.composePurposeLines(
            classInfo, List.of(classNameLike, op1, op2, op3, op4, getter), false);

    assertThat(lines).anyMatch(line -> line.contains("domain-logic component"));
    assertThat(lines).anyMatch(line -> line.contains("`createOrder`"));
    assertThat(lines).anyMatch(line -> line.contains("`updateOrder`"));
    assertThat(lines).anyMatch(line -> line.contains("`deleteOrder`"));
    assertThat(lines).noneMatch(line -> line.contains("`archiveOrder`"));
  }

  @Test
  void composePurposeLines_shouldClassifyAsDataHolderWhenNoEvaluatedMethodsRemain() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Profile");
    FieldInfo field = new FieldInfo();
    field.setName("id");
    classInfo.setFields(List.of(field));

    MethodInfo constructor = new MethodInfo();
    constructor.setName("Profile");
    MethodInfo blank = new MethodInfo();
    blank.setName(" ");

    List<String> lines = composer.composePurposeLines(classInfo, List.of(constructor, blank), true);

    assertThat(lines).anyMatch(line -> line.contains("データ保持"));
  }

  @Test
  void composePurposeLines_shouldUseFieldFallbackAndSkipNullOrBlankFieldNames() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.ConfigRepository");

    FieldInfo f1 = new FieldInfo();
    f1.setName("host");
    FieldInfo f2 = new FieldInfo();
    f2.setName(" ");
    FieldInfo f3 = new FieldInfo();
    f3.setName("port");
    FieldInfo f4 = new FieldInfo();
    f4.setName(null);
    FieldInfo f5 = new FieldInfo();
    f5.setName("scheme");
    FieldInfo f6 = new FieldInfo();
    f6.setName("timeout");
    classInfo.setFields(List.of(f1, f2, f3, f4, f5, f6));

    MethodInfo accessor = new MethodInfo();
    accessor.setName("getHost");

    List<String> lines = composer.composePurposeLines(classInfo, List.of(accessor), false);

    assertThat(lines).anyMatch(line -> line.contains("repository/access component"));
    assertThat(lines).anyMatch(line -> line.contains("`host`"));
    assertThat(lines).anyMatch(line -> line.contains("`port`"));
    assertThat(lines).anyMatch(line -> line.contains("`scheme`"));
    assertThat(lines).noneMatch(line -> line.contains("`timeout`"));
  }

  @Test
  void composePurposeLines_shouldTreatBooleanAndCanonicalNamesAsAccessorLike() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Account");

    MethodInfo isActive = new MethodInfo();
    isActive.setName("isActive");
    MethodInfo hasRole = new MethodInfo();
    hasRole.setName("hasRole");
    MethodInfo equals = new MethodInfo();
    equals.setName("equals");
    MethodInfo hashCode = new MethodInfo();
    hashCode.setName("hashCode");
    MethodInfo toString = new MethodInfo();
    toString.setName("toString");
    MethodInfo nonAccessor = new MethodInfo();
    nonAccessor.setName("activate");

    List<String> lines =
        composer.composePurposeLines(
            classInfo, List.of(isActive, hasRole, equals, hashCode, toString, nonAccessor), false);

    assertThat(lines).anyMatch(line -> line.contains("domain-logic component"));
    assertThat(lines).anyMatch(line -> line.contains("`activate`"));
    assertThat(lines).noneMatch(line -> line.contains("`isActive`"));
    assertThat(lines).noneMatch(line -> line.contains("`hasRole`"));
    assertThat(lines).noneMatch(line -> line.contains("`equals`"));
  }

  @Test
  void composePurposeLines_shouldFallbackToMethodCountWhenFieldFactsAreUnavailable() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.RecordRepository");

    FieldInfo blank1 = new FieldInfo();
    blank1.setName(" ");
    FieldInfo blank2 = new FieldInfo();
    blank2.setName(null);
    classInfo.setFields(List.of(blank1, blank2));

    MethodInfo get = new MethodInfo();
    get.setName("getId");

    List<String> lines = composer.composePurposeLines(classInfo, List.of(get), false);

    assertThat(lines).anyMatch(line -> line.contains("repository/access component"));
    assertThat(lines).anyMatch(line -> line.contains("Detected 1 specification methods."));
  }
}
