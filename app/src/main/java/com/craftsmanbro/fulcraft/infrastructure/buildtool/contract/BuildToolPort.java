package com.craftsmanbro.fulcraft.infrastructure.buildtool.contract;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.model.IsolationCheckResult;
import java.io.IOException;
import java.nio.file.Path;

/** Contract for executing project tests through the detected build tool. */
public interface BuildToolPort {

  String runTests(Config config, Path projectRoot, String runId) throws IOException;

  IsolationCheckResult runTestIsolated(
      Config config, Path projectRoot, String testClassName, String packageName, String testCode);

  IsolationCheckResult runSingleTest(
      Config config, Path projectRoot, String testClassName, String testMethodName);

  boolean isAvailable(Path projectRoot);
}
