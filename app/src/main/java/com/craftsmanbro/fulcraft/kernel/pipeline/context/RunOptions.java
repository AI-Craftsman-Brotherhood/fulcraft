package com.craftsmanbro.fulcraft.kernel.pipeline.context;

/** Execution options for a pipeline run. */
public final class RunOptions {

  private boolean dryRun;

  private boolean failFast;

  private boolean showSummary;

  public boolean isDryRun() {
    return dryRun;
  }

  public void setDryRun(final boolean dryRun) {
    this.dryRun = dryRun;
  }

  public boolean isFailFast() {
    return failFast;
  }

  public void setFailFast(final boolean failFast) {
    this.failFast = failFast;
  }

  public boolean isShowSummary() {
    return showSummary;
  }

  public void setShowSummary(final boolean showSummary) {
    this.showSummary = showSummary;
  }
}
