package com.craftsmanbro.fulcraft.kernel.pipeline.context;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Intermediate artifacts collected during pipeline execution. */
public final class RunArtifacts {

  private final List<ReportTaskResult> reportTaskResults = new ArrayList<>();

  private boolean brittlenessDetected;

  public List<ReportTaskResult> getReportTaskResults() {
    return List.copyOf(reportTaskResults);
  }

  public void setReportTaskResults(final List<ReportTaskResult> reportTaskResults) {
    final List<ReportTaskResult> validatedResults =
        requireNonNullListElements(reportTaskResults, "reportTaskResults");
    this.reportTaskResults.clear();
    this.reportTaskResults.addAll(validatedResults);
  }

  public boolean isBrittlenessDetected() {
    return brittlenessDetected;
  }

  public void setBrittlenessDetected(final boolean brittlenessDetected) {
    this.brittlenessDetected = brittlenessDetected;
  }

  private static <T> List<T> requireNonNullListElements(final List<T> list, final String name) {
    Objects.requireNonNull(
        list, MessageSource.getMessage("kernel.common.error.argument_null", name));
    for (int i = 0; i < list.size(); i++) {
      Objects.requireNonNull(
          list.get(i),
          MessageSource.getMessage("kernel.common.error.collection_element_null", name, i));
    }
    return list;
  }
}
