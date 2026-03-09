package com.craftsmanbro.fulcraft.infrastructure.vcs.contract;

import com.craftsmanbro.fulcraft.infrastructure.vcs.model.CommitCount;
import java.util.List;

/** Contract for querying VCS commit count metadata. */
public interface VcsCommitCountPort {

  void setSourceRootPaths(List<String> sourceRootPaths);

  CommitCount resolveCommitCount(String filePath);

  default int getCommitCount(final String filePath) {
    return resolveCommitCount(filePath).value();
  }
}
