package com.craftsmanbro.fulcraft.config.junit;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LocalFixConfig {

  @JsonProperty("enable_generics")
  private Boolean enableGenerics = false;

  @JsonProperty("enable_builder_fix")
  private Boolean enableBuilderFix = false;

  @JsonProperty("enable_record_accessor")
  private Boolean enableRecordAccessor = false;

  @JsonProperty("enable_redundant_cast_removal")
  private Boolean enableRedundantCastRemoval = false;

  @JsonProperty("enable_extended_public_removal")
  private Boolean enableExtendedPublicRemoval = false;

  public boolean isEnableGenerics() {
    return enableGenerics != null && enableGenerics;
  }

  public void setEnableGenerics(final Boolean enableGenerics) {
    this.enableGenerics = enableGenerics;
  }

  public boolean isEnableBuilderFix() {
    return enableBuilderFix != null && enableBuilderFix;
  }

  public void setEnableBuilderFix(final Boolean enableBuilderFix) {
    this.enableBuilderFix = enableBuilderFix;
  }

  public boolean isEnableRecordAccessor() {
    return enableRecordAccessor != null && enableRecordAccessor;
  }

  public void setEnableRecordAccessor(final Boolean enableRecordAccessor) {
    this.enableRecordAccessor = enableRecordAccessor;
  }

  public boolean isEnableRedundantCastRemoval() {
    return enableRedundantCastRemoval != null && enableRedundantCastRemoval;
  }

  public void setEnableRedundantCastRemoval(final Boolean enableRedundantCastRemoval) {
    this.enableRedundantCastRemoval = enableRedundantCastRemoval;
  }

  public boolean isEnableExtendedPublicRemoval() {
    return enableExtendedPublicRemoval != null && enableExtendedPublicRemoval;
  }

  public void setEnableExtendedPublicRemoval(final Boolean enableExtendedPublicRemoval) {
    this.enableExtendedPublicRemoval = enableExtendedPublicRemoval;
  }

  /** Returns true if any extended fix pattern is enabled. */
  public boolean hasAnyExtendedFixEnabled() {
    return isEnableGenerics()
        || isEnableBuilderFix()
        || isEnableRecordAccessor()
        || isEnableRedundantCastRemoval()
        || isEnableExtendedPublicRemoval();
  }
}
