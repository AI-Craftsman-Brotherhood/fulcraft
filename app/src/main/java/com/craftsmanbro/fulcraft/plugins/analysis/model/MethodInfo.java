package com.craftsmanbro.fulcraft.plugins.analysis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MethodInfo {

  public static final String UNKNOWN = "unknown";

  private static final String METHOD_KEY_SEPARATOR = "#";

  @JsonProperty("name")
  private String name;

  @JsonProperty("signature")
  private String signature;

  @JsonProperty("method_id")
  private String methodId;

  @JsonProperty("raw_signatures")
  private List<String> rawSignatures = new ArrayList<>();

  @JsonProperty("branch_summary")
  private BranchSummary branchSummary;

  @JsonProperty("representative_paths")
  private List<RepresentativePath> representativePaths = new ArrayList<>();

  @JsonProperty("loc")
  private int loc;

  @JsonProperty("visibility")
  private String visibility;

  @JsonProperty("cyclomatic_complexity")
  private int cyclomaticComplexity;

  @JsonProperty("uses_removed_apis")
  private boolean usesRemovedApis;

  @JsonProperty("annotations")
  private List<String> annotations = new ArrayList<>();

  @JsonProperty("called_method_refs")
  private List<CalledMethodRef> calledMethodRefs = new ArrayList<>();

  @JsonProperty("called_methods")
  private List<String> calledMethods = new ArrayList<>();

  @JsonProperty("part_of_cycle")
  private boolean partOfCycle;

  @JsonProperty("dead_code")
  private boolean deadCode;

  @JsonProperty("duplicate")
  private boolean duplicate;

  @JsonProperty("duplicate_group")
  private String duplicateGroup;

  @JsonProperty("code_hash")
  private String codeHash;

  @JsonProperty("max_nesting_depth")
  private int maxNestingDepth;

  @JsonProperty("parameter_count")
  private int parameterCount;

  @JsonProperty("thrown_exceptions")
  private List<String> thrownExceptions = new ArrayList<>();

  @JsonProperty("usage_count")
  private int usageCount;

  @JsonProperty("source_code")
  private String sourceCode;

  @JsonProperty("is_static")
  private boolean isStatic;

  @JsonProperty("dynamic_resolutions")
  private List<DynamicResolution> dynamicResolutions = new ArrayList<>();

  @JsonProperty("dynamic_feature_high")
  private int dynamicFeatureHigh;

  @JsonProperty("dynamic_feature_medium")
  private int dynamicFeatureMedium;

  @JsonProperty("dynamic_feature_low")
  private int dynamicFeatureLow;

  @JsonProperty("dynamic_feature_has_service_loader")
  private boolean dynamicFeatureHasServiceLoader;

  @JsonProperty("brittleness_signals")
  private List<BrittlenessSignal> brittlenessSignals = new ArrayList<>();

  @JsonProperty("brittle")
  private boolean brittle;

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getSignature() {
    return signature;
  }

  public void setSignature(final String signature) {
    this.signature = signature;
  }

  public String getMethodId() {
    return methodId;
  }

  public void setMethodId(final String methodId) {
    this.methodId = methodId;
  }

  public List<String> getRawSignatures() {
    return Collections.unmodifiableList(rawSignatures);
  }

  public void setRawSignatures(final List<String> rawSignatures) {
    this.rawSignatures = Objects.requireNonNullElseGet(rawSignatures, ArrayList::new);
  }

  public BranchSummary getBranchSummary() {
    return branchSummary;
  }

  public void setBranchSummary(final BranchSummary branchSummary) {
    this.branchSummary = branchSummary;
  }

  public List<RepresentativePath> getRepresentativePaths() {
    return Collections.unmodifiableList(representativePaths);
  }

  public void setRepresentativePaths(final List<RepresentativePath> representativePaths) {
    final List<RepresentativePath> safeRepresentativePaths =
        Objects.requireNonNullElseGet(representativePaths, ArrayList::new);
    this.representativePaths = new ArrayList<>(safeRepresentativePaths);
  }

  public int getLoc() {
    return loc;
  }

  public void setLoc(final int loc) {
    this.loc = loc;
  }

  public String getVisibility() {
    return visibility;
  }

  public void setVisibility(final String visibility) {
    this.visibility = visibility;
  }

  public int getCyclomaticComplexity() {
    return cyclomaticComplexity;
  }

  public void setCyclomaticComplexity(final int cyclomaticComplexity) {
    this.cyclomaticComplexity = cyclomaticComplexity;
  }

  public boolean isUsesRemovedApis() {
    return usesRemovedApis;
  }

  public void setUsesRemovedApis(final boolean usesRemovedApis) {
    this.usesRemovedApis = usesRemovedApis;
  }

  public List<String> getAnnotations() {
    return Collections.unmodifiableList(annotations);
  }

  public void setAnnotations(final List<String> annotations) {
    this.annotations = Objects.requireNonNullElseGet(annotations, ArrayList::new);
  }

  public List<CalledMethodRef> getCalledMethodRefs() {
    if (calledMethodRefs == null) {
      calledMethodRefs = new ArrayList<>();
    }
    return Collections.unmodifiableList(calledMethodRefs);
  }

  public void setCalledMethodRefs(final List<CalledMethodRef> calledMethodRefs) {
    this.calledMethodRefs = Objects.requireNonNullElseGet(calledMethodRefs, ArrayList::new);
    this.calledMethods = deriveCalledMethods(this.calledMethodRefs);
  }

  public List<String> getCalledMethods() {
    if (calledMethodRefs != null && !calledMethodRefs.isEmpty()) {
      return Collections.unmodifiableList(deriveCalledMethods(calledMethodRefs));
    }
    if (calledMethods == null) {
      return List.of();
    }
    return Collections.unmodifiableList(calledMethods);
  }

  public void setCalledMethods(final List<String> calledMethods) {
    if (this.calledMethodRefs != null && !this.calledMethodRefs.isEmpty()) {
      this.calledMethods = deriveCalledMethods(this.calledMethodRefs);
      return;
    }
    this.calledMethods = Objects.requireNonNullElseGet(calledMethods, ArrayList::new);
    if (calledMethods == null) {
      return;
    }
    this.calledMethodRefs = new ArrayList<>();
    for (final String call : calledMethods) {
      final CalledMethodRef ref = new CalledMethodRef();
      ref.setRaw(call);
      if (isUnknownCall(call)) {
        ref.setResolved(null);
        ref.setStatus(ResolutionStatus.UNRESOLVED);
        ref.setConfidence(0.3);
      } else {
        ref.setResolved(call);
        ref.setStatus(ResolutionStatus.RESOLVED);
        ref.setConfidence(1.0);
      }
      ref.setSource("legacy");
      this.calledMethodRefs.add(ref);
    }
  }

  private static boolean isUnknownCall(final String call) {
    if (call == null) {
      return true;
    }
    final int separatorIndex = call.indexOf(METHOD_KEY_SEPARATOR);
    final String classPart = separatorIndex >= 0 ? call.substring(0, separatorIndex) : call;
    return UNKNOWN.equals(classPart);
  }

  public boolean isPartOfCycle() {
    return partOfCycle;
  }

  public void setPartOfCycle(final boolean partOfCycle) {
    this.partOfCycle = partOfCycle;
  }

  public boolean isDeadCode() {
    return deadCode;
  }

  public void setDeadCode(final boolean deadCode) {
    this.deadCode = deadCode;
  }

  public boolean isDuplicate() {
    return duplicate;
  }

  public void setDuplicate(final boolean duplicate) {
    this.duplicate = duplicate;
  }

  public String getDuplicateGroup() {
    return duplicateGroup;
  }

  public void setDuplicateGroup(final String duplicateGroup) {
    this.duplicateGroup = duplicateGroup;
  }

  public String getCodeHash() {
    return codeHash;
  }

  public void setCodeHash(final String codeHash) {
    this.codeHash = codeHash;
  }

  public int getMaxNestingDepth() {
    return maxNestingDepth;
  }

  public void setMaxNestingDepth(final int maxNestingDepth) {
    this.maxNestingDepth = maxNestingDepth;
  }

  public int getParameterCount() {
    return parameterCount;
  }

  public void setParameterCount(final int parameterCount) {
    this.parameterCount = parameterCount;
  }

  public List<String> getThrownExceptions() {
    return Collections.unmodifiableList(thrownExceptions);
  }

  public void setThrownExceptions(final List<String> thrownExceptions) {
    this.thrownExceptions = Objects.requireNonNullElseGet(thrownExceptions, ArrayList::new);
  }

  public int getUsageCount() {
    return usageCount;
  }

  public void setUsageCount(final int usageCount) {
    this.usageCount = usageCount;
  }

  @JsonProperty("has_loops")
  private boolean hasLoops;

  @JsonProperty("has_conditionals")
  private boolean hasConditionals;

  public boolean hasLoops() {
    return hasLoops;
  }

  public void setHasLoops(final boolean hasLoops) {
    this.hasLoops = hasLoops;
  }

  public boolean hasConditionals() {
    return hasConditionals;
  }

  public void setHasConditionals(final boolean hasConditionals) {
    this.hasConditionals = hasConditionals;
  }

  public String getSourceCode() {
    return sourceCode;
  }

  public void setSourceCode(final String sourceCode) {
    this.sourceCode = sourceCode;
  }

  public boolean isStatic() {
    return isStatic;
  }

  public void setStatic(final boolean isStatic) {
    this.isStatic = isStatic;
  }

  public List<DynamicResolution> getDynamicResolutions() {
    return Collections.unmodifiableList(dynamicResolutions);
  }

  public void setDynamicResolutions(final List<DynamicResolution> dynamicResolutions) {
    this.dynamicResolutions = Objects.requireNonNullElseGet(dynamicResolutions, ArrayList::new);
  }

  public int getDynamicFeatureHigh() {
    return dynamicFeatureHigh;
  }

  public void setDynamicFeatureHigh(final int dynamicFeatureHigh) {
    this.dynamicFeatureHigh = dynamicFeatureHigh;
  }

  public int getDynamicFeatureMedium() {
    return dynamicFeatureMedium;
  }

  public void setDynamicFeatureMedium(final int dynamicFeatureMedium) {
    this.dynamicFeatureMedium = dynamicFeatureMedium;
  }

  public int getDynamicFeatureLow() {
    return dynamicFeatureLow;
  }

  public void setDynamicFeatureLow(final int dynamicFeatureLow) {
    this.dynamicFeatureLow = dynamicFeatureLow;
  }

  public boolean hasDynamicFeatureServiceLoader() {
    return dynamicFeatureHasServiceLoader;
  }

  public void setDynamicFeatureHasServiceLoader(final boolean dynamicFeatureHasServiceLoader) {
    this.dynamicFeatureHasServiceLoader = dynamicFeatureHasServiceLoader;
  }

  public int getDynamicFeatureTotal() {
    return dynamicFeatureHigh + dynamicFeatureMedium + dynamicFeatureLow;
  }

  public List<BrittlenessSignal> getBrittlenessSignals() {
    return Collections.unmodifiableList(brittlenessSignals);
  }

  public void setBrittlenessSignals(final List<BrittlenessSignal> brittlenessSignals) {
    this.brittlenessSignals = Objects.requireNonNullElseGet(brittlenessSignals, ArrayList::new);
    this.brittle = !this.brittlenessSignals.isEmpty();
  }

  public boolean isBrittle() {
    return brittle;
  }

  public void setBrittle(final boolean brittle) {
    this.brittle = brittle;
  }

  private List<String> deriveCalledMethods(final List<CalledMethodRef> refs) {
    final List<String> derived = new ArrayList<>();
    final LinkedHashSet<String> normalizedKeys = new LinkedHashSet<>();
    for (final CalledMethodRef ref : refs) {
      if (ref == null) {
        continue;
      }
      final String value = ref.getResolved() != null ? ref.getResolved() : ref.getRaw();
      if (value == null || value.isBlank()) {
        continue;
      }
      final String normalized = normalizeCallSignature(value);
      if (normalizedKeys.add(normalized)) {
        derived.add(value.trim());
      }
    }
    return derived;
  }

  private String normalizeCallSignature(final String value) {
    return value.trim().replaceAll("\\$([A-Za-z])", ".$1").replaceAll("\\s+", "");
  }
}
