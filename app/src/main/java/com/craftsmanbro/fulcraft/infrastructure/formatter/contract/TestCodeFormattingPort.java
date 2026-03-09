package com.craftsmanbro.fulcraft.infrastructure.formatter.contract;

import com.craftsmanbro.fulcraft.infrastructure.formatter.model.TestCodeFormattingProfile;

/** Contract for deterministic formatting of generated Java test source code. */
public interface TestCodeFormattingPort {

  String format(String code, TestCodeFormattingProfile profile);

  default String format(final String code) {
    return format(code, TestCodeFormattingProfile.deterministicDefaults());
  }
}
