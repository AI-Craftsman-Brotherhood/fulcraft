package com.craftsmanbro.fulcraft.infrastructure.json.contract;

import com.craftsmanbro.fulcraft.infrastructure.json.model.JsonMapperProfile;
import tools.jackson.databind.ObjectMapper;

/** Contract for creating Jackson ObjectMapper instances for JSON infrastructure concerns. */
public interface JsonMapperPort {

  ObjectMapper create(JsonMapperProfile profile);

  default ObjectMapper createCompact() {
    return create(JsonMapperProfile.deterministicCompact());
  }

  default ObjectMapper createPretty() {
    return create(JsonMapperProfile.deterministicPretty());
  }
}
