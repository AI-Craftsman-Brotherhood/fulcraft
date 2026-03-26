package com.craftsmanbro.fulcraft.infrastructure.auth.aws.contract;

import com.craftsmanbro.fulcraft.infrastructure.auth.aws.model.AwsCredentials;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

/** Contract for AWS request signing implementations (SigV4, future variants). */
@FunctionalInterface
public interface AwsRequestSigningPort {

  /**
   * Produces the signed headers for the given request input.
   *
   * <p>The caller provides {@code now} so signing remains deterministic and testable.
   */
  Map<String, String> sign(
      String method,
      URI uri,
      String region,
      String service,
      AwsCredentials credentials,
      String payload,
      Instant now);
}
