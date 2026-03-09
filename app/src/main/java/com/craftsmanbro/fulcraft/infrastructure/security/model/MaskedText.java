package com.craftsmanbro.fulcraft.infrastructure.security.model;

import java.util.Objects;

/** Immutable pair of original and masked text. */
public record MaskedText(String original, String masked) {

  public boolean changed() {
    return !Objects.equals(original, masked);
  }
}
