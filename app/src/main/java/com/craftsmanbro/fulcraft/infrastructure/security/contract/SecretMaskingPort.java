package com.craftsmanbro.fulcraft.infrastructure.security.contract;

import com.craftsmanbro.fulcraft.infrastructure.security.model.MaskedText;

/** Contract for masking sensitive data in textual output. */
public interface SecretMaskingPort {

  String maskText(String input);

  String maskThrowable(Throwable throwable);

  default MaskedText maskValue(final String input) {
    return new MaskedText(input, maskText(input));
  }
}
