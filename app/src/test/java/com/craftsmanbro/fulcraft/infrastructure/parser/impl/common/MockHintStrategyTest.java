package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.FieldInfo;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockHintStrategyTest {

  @Test
  void determineMockHint_returnsRequiredForInterfaceType() {
    FieldInfo field = new FieldInfo();
    field.setType("com.example.PaymentGateway");
    field.setVisibility("private");

    String hint =
        MockHintStrategy.determineMockHint(field, Set.of("PaymentGateway"), Set.of(), Set.of());

    assertEquals(MockHintStrategy.HINT_REQUIRED, hint);
    assertFalse(field.isInjectable());
  }

  @Test
  void determineMockHint_returnsRequiredForAbstractTypeWithNormalizedName() {
    FieldInfo field = new FieldInfo();
    field.setType("com.example.AbstractPaymentService<Order>");
    field.setVisibility("private");

    String hint =
        MockHintStrategy.determineMockHint(
            field, Set.of(), Set.of("AbstractPaymentService"), Set.of());

    assertEquals(MockHintStrategy.HINT_REQUIRED, hint);
  }

  @Test
  void determineMockHint_marksInjectableAndRecommendedForConstructorInjectedPattern() {
    FieldInfo field = new FieldInfo();
    field.setType("OrderService");
    field.setVisibility("private");
    field.setFinal(true);
    field.setStatic(false);

    String hint =
        MockHintStrategy.determineMockHint(field, Set.of(), Set.of(), Set.of("OrderService"));

    assertEquals(MockHintStrategy.HINT_RECOMMENDED, hint);
    assertTrue(field.isInjectable());
  }

  @Test
  void determineMockHint_recommendsForPrivateNonStaticMockableField() {
    FieldInfo field = new FieldInfo();
    field.setType("com.example.UserRepository");
    field.setVisibility("private");
    field.setStatic(false);

    String hint = MockHintStrategy.determineMockHint(field, Set.of(), Set.of(), Set.of());

    assertEquals(MockHintStrategy.HINT_RECOMMENDED, hint);
    assertFalse(field.isInjectable());
  }

  @Test
  void determineMockHint_handlesNullHintSets() {
    FieldInfo field = new FieldInfo();
    field.setType("BillingClient");
    field.setVisibility("private");
    field.setStatic(false);

    String hint = MockHintStrategy.determineMockHint(field, null, null, null);

    assertEquals(MockHintStrategy.HINT_RECOMMENDED, hint);
  }

  @Test
  void determineMockHint_marksInjectableEvenWhenHintIsNull() {
    FieldInfo field = new FieldInfo();
    field.setType("OrderConfig");
    field.setVisibility("private");
    field.setFinal(true);
    field.setStatic(false);

    String hint =
        MockHintStrategy.determineMockHint(
            field, Set.of(), Set.of(), Set.of("com.example.OrderConfig"));

    assertNull(hint);
    assertTrue(field.isInjectable());
  }

  @Test
  void determineMockHint_returnsNullForStaticMockableField() {
    FieldInfo field = new FieldInfo();
    field.setType("UserRepository");
    field.setVisibility("private");
    field.setStatic(true);

    String hint = MockHintStrategy.determineMockHint(field, Set.of(), Set.of(), Set.of());

    assertNull(hint);
  }

  @Test
  void determineMockHint_returnsNullWhenTypeMissing() {
    FieldInfo field = new FieldInfo();

    String hint = MockHintStrategy.determineMockHint(field, Set.of(), Set.of(), Set.of());

    assertNull(hint);
  }

  @Test
  void matchesMockablePattern_normalizesTypeNames() {
    assertTrue(MockHintStrategy.matchesMockablePattern("com.example.AccountManager[]"));
    assertTrue(MockHintStrategy.matchesMockablePattern("PaymentService<Gateway>"));
    assertFalse(MockHintStrategy.matchesMockablePattern("java.lang.String"));
    assertFalse(MockHintStrategy.matchesMockablePattern(null));
  }
}
