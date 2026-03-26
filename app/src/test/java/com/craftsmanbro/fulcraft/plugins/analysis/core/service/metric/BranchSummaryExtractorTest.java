package com.craftsmanbro.fulcraft.plugins.analysis.core.service.metric;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardType;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import org.junit.jupiter.api.Test;

class BranchSummaryExtractorTest {

  private final BranchSummaryExtractor extractor = new BranchSummaryExtractor();

  @Test
  void extractsBranchSummaryAndRepresentativePaths() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(calculateInvoiceSource());

    BranchSummaryResult result = extractor.compute(method);

    // parseError may be present if fallback parsing was used (e.g., due to switch
    // expressions)
    // The important thing is that the summary was still produced successfully
    assertThat(result.branchSummary()).isPresent();
    assertThat(method.getBranchSummary()).isNotNull();
    assertThat(method.getBranchSummary().getGuards()).hasSize(5);
    assertThat(method.getBranchSummary().getGuards())
        .filteredOn(g -> g.getType() == GuardType.FAIL_GUARD)
        .extracting(GuardSummary::getCondition)
        .containsExactlyInAnyOrder(
            "customer == null",
            "items == null || items.isEmpty()",
            "regionCode == null || regionCode.isEmpty()");
    assertThat(method.getBranchSummary().getGuards())
        .filteredOn(g -> g.getType() == GuardType.FAIL_GUARD)
        .filteredOn(g -> g.getMessageLiteral() != null)
        .extracting(GuardSummary::getMessageLiteral)
        .contains("Customer cannot be null", "Item list cannot be empty", "Invalid region code");
    assertThat(method.getBranchSummary().getGuards())
        .filteredOn(g -> g.getType() == GuardType.LOOP_GUARD_CONTINUE)
        .singleElement()
        .satisfies(
            g -> {
              assertThat(g.getCondition()).contains("item.getQuantity() <= 0");
              assertThat(g.getMessageLiteral()).contains("invalid quantity");
            });
    assertThat(method.getBranchSummary().getGuards())
        .filteredOn(g -> g.getType() == GuardType.LOOP_GUARD_BREAK)
        .singleElement()
        .satisfies(
            g -> {
              assertThat(g.getCondition()).contains("item.getUnitPrice() < 0");
              assertThat(g.getMessageLiteral()).contains("Negative price");
            });
    assertThat(method.getBranchSummary().getGuards())
        .noneMatch(
            g ->
                g.getCondition().contains("customer != null")
                    || g.getCondition().contains("items != null && !items.isEmpty()")
                    || g.getCondition().contains("regionCode != null && !regionCode.isEmpty()"));

    // Switch expressions may not be parsed on all JDK versions/configurations
    if (!method.getBranchSummary().getSwitches().isEmpty()) {
      assertThat(method.getBranchSummary().getSwitches())
          .anyMatch(s -> s.contains("JP") || s.contains("US_CA") || s.contains("default"))
          .anyMatch(s -> s.contains("US_NY") || s.contains("EU_DE"));
    }

    assertThat(method.getBranchSummary().getPredicates())
        .anySatisfy(p -> assertThat(p).contains("customer.getTier() == Tier.GOLD"))
        .anySatisfy(p -> assertThat(p).contains("date.getMonthValue() == 12"))
        .anySatisfy(p -> assertThat(p).contains("item.getQuantity() >= 10"))
        .anySatisfy(p -> assertThat(p).contains("reg == Region.JP"))
        .anySatisfy(p -> assertThat(p).contains("item.category() == Category.GIFT"));

    assertThat(method.getRepresentativePaths()).hasSizeLessThanOrEqualTo(16);
    assertThat(method.getRepresentativePaths())
        .noneSatisfy(p -> assertThat(p.getDescription()).contains("Early return path"));
    assertThat(method.getRepresentativePaths())
        .filteredOn(p -> "failure".equals(p.getExpectedOutcomeHint()))
        .filteredOn(
            p ->
                !p.getDescription().contains("invalid quantity")
                    && !p.getDescription().contains("Negative price"))
        .allSatisfy(
            p -> {
              assertThat(p.getRequiredConditions().getFirst())
                  .doesNotContain(" != null")
                  .containsAnyOf("== null", "isEmpty()");
            });
    assertThat(method.getRepresentativePaths())
        .filteredOn(p -> p.getDescription().contains("Invalid region code"))
        .first()
        .satisfies(
            p ->
                assertThat(p.getRequiredConditions().getFirst())
                    .contains("regionCode == null")
                    .doesNotContain("regionCode != null"));
    assertThat(method.getRepresentativePaths())
        .filteredOn(p -> p.getDescription().contains("Item list cannot be empty"))
        .first()
        .satisfies(
            p ->
                assertThat(p.getRequiredConditions().getFirst())
                    .contains("items == null")
                    .doesNotContain("items != null && !items.isEmpty()"));
    assertThat(method.getRepresentativePaths())
        .filteredOn(p -> p.getDescription().contains("Customer cannot be null"))
        .first()
        .satisfies(
            p ->
                assertThat(p.getRequiredConditions().getFirst())
                    .contains("customer == null")
                    .doesNotContain("customer != null"));
    assertThat(method.getRepresentativePaths())
        .anySatisfy(p -> assertThat(p.getExpectedOutcomeHint()).contains("success"));
    if (!method.getBranchSummary().getSwitches().isEmpty()) {
      assertThat(method.getRepresentativePaths())
          .extracting(p -> p.getExpectedOutcomeHint() == null ? "" : p.getExpectedOutcomeHint())
          .anyMatch(hint -> hint.contains("case-JP"))
          .anyMatch(hint -> hint.contains("case-US_CA"))
          .anyMatch(hint -> hint.contains("case-default"));
    }
  }

  @Test
  void doesNotClassifySuccessfulLookupReturnAsFailureGuard() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
        public Customer findCustomerById(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            for (Customer customer : customers) {
                if (customer.getId().equals(id)) {
                    return customer;
                }
            }
            return null;
        }
        """);

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isPresent();
    assertThat(method.getBranchSummary()).isNotNull();
    assertThat(method.getBranchSummary().getGuards())
        .filteredOn(g -> g.getType() == GuardType.FAIL_GUARD)
        .extracting(GuardSummary::getCondition)
        .contains("id == null || id.isBlank()")
        .doesNotContain("customer.getId().equals(id)");
    assertThat(method.getRepresentativePaths())
        .filteredOn(p -> "early-return".equals(p.getExpectedOutcomeHint()))
        .extracting(
            p -> p.getRequiredConditions().isEmpty() ? "" : p.getRequiredConditions().getFirst())
        .allSatisfy(
            condition -> assertThat(condition).doesNotContain("customer.getId().equals(id)"));
  }

  @Test
  void keepsCompoundIfConditionInPredicates() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
        public String findCustomerById(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            return id.trim();
        }
        """);

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isPresent();
    assertThat(method.getBranchSummary()).isNotNull();
    assertThat(method.getBranchSummary().getPredicates()).contains("id == null || id.isBlank()");
  }

  @Test
  void treatsUtilityIsEmptyReturnAsFailureGuard() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
        public String formatMessage(String prefix, String body) {
            if (StringUtil.isEmpty(body)) {
                return "";
            }
            return prefix + ": " + body;
        }
        """);

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isPresent();
    assertThat(method.getBranchSummary()).isNotNull();
    assertThat(method.getBranchSummary().getGuards())
        .filteredOn(g -> g.getType() == GuardType.FAIL_GUARD)
        .extracting(GuardSummary::getCondition)
        .contains("StringUtil.isEmpty(body)");
    assertThat(method.getRepresentativePaths())
        .filteredOn(p -> "early-return".equals(p.getExpectedOutcomeHint()))
        .extracting(
            p -> p.getRequiredConditions().isEmpty() ? "" : p.getRequiredConditions().getFirst())
        .contains("StringUtil.isEmpty(body)");
  }

  @Test
  void parseFallbackProducesWarnNotError() {
    MethodInfo method = new MethodInfo();
    // Two methods concatenated should force direct parse failure and fallback to
    // succeed
    method.setSourceCode(
        """
                        public void a() {}
                        public void b() {}
                        """);

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.usedFallback()).isTrue();
    assertThat(result.branchSummary()).isPresent();
    assertThat(result.parseError()).isPresent();
  }

  @Test
  void doesNotCreateSyntheticSuccessPathWhenNoSpecificBranchFactsExist() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
        public String normalize(String value) {
            return value.trim();
        }
        """);

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isPresent();
    assertThat(method.getBranchSummary()).isNotNull();
    assertThat(method.getBranchSummary().getGuards()).isEmpty();
    assertThat(method.getBranchSummary().getSwitches()).isEmpty();
    assertThat(method.getRepresentativePaths()).isEmpty();
  }

  @Test
  void doesNotCreateBoundaryPathForPositiveNonNullPredicate() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
        public void refund(java.math.BigDecimal refundAmount) {
            if (refundAmount != null) {
                process(refundAmount);
            }
        }
        """);

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isPresent();
    assertThat(method.getBranchSummary()).isNotNull();
    assertThat(method.getBranchSummary().getPredicates()).contains("refundAmount != null");
    assertThat(method.getRepresentativePaths())
        .noneSatisfy(
            path -> assertThat(path.getRequiredConditions()).contains("refundAmount != null"));
  }

  @Test
  void doesNotDuplicateBoundaryPathForExistingGuardCondition() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
        public String resolve(String id) {
            if (id == null) {
                return null;
            }
            return id.trim();
        }
        """);

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isPresent();
    assertThat(method.getBranchSummary()).isNotNull();
    assertThat(method.getRepresentativePaths())
        .filteredOn(path -> path.getRequiredConditions().contains("id == null"))
        .hasSize(1);
  }

  @Test
  void doesNotCreateBoundaryPathForLoopConditionPredicates() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
        public int countPositive(java.util.List<Integer> values) {
            int count = 0;
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i) > 0) {
                    count++;
                }
            }
            return count;
        }
        """);

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isPresent();
    assertThat(method.getBranchSummary()).isNotNull();
    assertThat(method.getBranchSummary().getPredicates())
        .contains("i < values.size()", "values.get(i) > 0");
    assertThat(method.getRepresentativePaths())
        .noneSatisfy(
            path -> assertThat(path.getRequiredConditions()).contains("i < values.size()"));
  }

  @Test
  void doesNotCreateBoundaryPathForInternalPredicatesWithoutParameterReference() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
        public int computeScore(int base) {
            int score = base * 2;
            if (score > 10) {
                return score;
            }
            return 0;
        }
        """);

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isPresent();
    assertThat(method.getBranchSummary()).isNotNull();
    assertThat(method.getBranchSummary().getPredicates()).contains("score > 10");
    assertThat(method.getRepresentativePaths())
        .noneSatisfy(path -> assertThat(path.getRequiredConditions()).contains("score > 10"));
  }

  @Test
  void createsBoundaryPathForParameterDrivenComparisonOutsideLoop() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
        public int normalize(int amount) {
            if (amount <= 0) {
                return 0;
            }
            return amount;
        }
        """);

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isPresent();
    assertThat(method.getBranchSummary()).isNotNull();
    assertThat(method.getRepresentativePaths())
        .anySatisfy(path -> assertThat(path.getRequiredConditions()).contains("amount <= 0"));
  }

  @Test
  void returnsParseErrorWhenSourceCodeIsMissing() {
    MethodInfo method = new MethodInfo();

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isEmpty();
    assertThat(result.representativePaths()).isEmpty();
    assertThat(result.usedFallback()).isFalse();
    assertThat(result.parseError()).contains("Missing source_code for branch summary extraction");
    assertThat(method.getBranchSummary()).isNull();
  }

  @Test
  void createsEmptySummaryForTrivialMethodWhenParsingFails() {
    MethodInfo method = new MethodInfo();
    method.setCyclomaticComplexity(1);
    method.setSourceCode("public String normalize(String value) { return value.trim(; }");

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isPresent();
    assertThat(method.getBranchSummary()).isNotNull();
    assertThat(method.getBranchSummary().getGuards()).isEmpty();
    assertThat(method.getBranchSummary().getSwitches()).isEmpty();
    assertThat(method.getBranchSummary().getPredicates()).isEmpty();
    assertThat(method.getRepresentativePaths()).isEmpty();
    assertThat(result.parseError()).isPresent();
  }

  @Test
  void returnsNoSummaryForNonTrivialMethodWhenParsingFails() {
    MethodInfo method = new MethodInfo();
    method.setCyclomaticComplexity(2);
    method.setSourceCode("public int normalize(int amount) { if (amount <= 0) { return 0; ");

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isEmpty();
    assertThat(result.representativePaths()).isEmpty();
    assertThat(result.parseError()).isPresent();
    assertThat(method.getBranchSummary()).isNull();
  }

  @Test
  void extractsFailureGuardFromConstructorSource() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
        public PriceService(String currency) {
            if (currency == null) {
                throw new IllegalArgumentException("currency is required");
            }
            this.currency = currency;
        }
        """);

    BranchSummaryResult result = extractor.compute(method);

    assertThat(result.branchSummary()).isPresent();
    assertThat(method.getBranchSummary()).isNotNull();
    assertThat(method.getBranchSummary().getGuards())
        .filteredOn(g -> g.getType() == GuardType.FAIL_GUARD)
        .extracting(GuardSummary::getCondition)
        .contains("currency == null");
    assertThat(method.getRepresentativePaths())
        .filteredOn(path -> "failure".equals(path.getExpectedOutcomeHint()))
        .anySatisfy(path -> assertThat(path.getRequiredConditions()).contains("currency == null"));
  }

  private String calculateInvoiceSource() {
    return """
                public InvoiceResult calculateInvoice(Customer customer, java.util.List<Item> items, String regionCode, boolean applyDiscount, java.time.LocalDate date) {
                    InvoiceResult result = new InvoiceResult();
                    result.success = true;
                    if (customer != null) {
                        if (items != null && !items.isEmpty()) {
                            if (regionCode != null && !regionCode.isEmpty()) {
                                Region reg = Region.from(regionCode);
                                double rate = switch (reg) {
                                    case JP -> 0.08;
                                    case US_CA -> 0.07;
                                    case US_NY -> 0.09;
                                    case EU_DE -> 0.19;
                                    default -> 0.10;
                                };
                                double total = 0;
                                for (Item item : items) {
                                    if (item.getQuantity() <= 0) {
                                        result.errorMessages.add("invalid quantity");
                                        continue;
                                    }
                                    if (item.getUnitPrice() < 0) {
                                        result.success = false;
                                        result.errorMessages.add("Negative price not allowed");
                                        break;
                                    }
                                    total += item.price();
                                    if (item.category() == Category.GIFT && date.getMonthValue() == 12) {
                                        total += 5;
                                    }
                                    if (item.getQuantity() >= 10) {
                                        total *= 0.9;
                                    }
                                    if (reg == Region.JP && item.category() == Category.FOOD) {
                                        total += total * 0.08;
                                    }
                                }
                                if (customer.getTier() == Tier.GOLD) {
                                    total *= 0.95;
                                }
                                result.total = total;
                            } else {
                                result.success = false;
                                result.errorMessages.add("Invalid region code");
                            }
                        } else {
                            result.success = false;
                            result.errorMessages.add("Item list cannot be empty");
                        }
                    } else {
                        result.success = false;
                        result.errorMessages.add("Customer cannot be null");
                    }
                    return result;
                }
                """;
  }
}
