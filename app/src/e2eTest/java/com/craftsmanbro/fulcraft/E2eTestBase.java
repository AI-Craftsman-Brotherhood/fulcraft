package com.craftsmanbro.fulcraft;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Base class for end-to-end CLI tests that drive the real {@link Main#run(String[])} entry point.
 *
 * <p>Provides isolation that a shared, forked test JVM needs:
 *
 * <ul>
 *   <li>each test gets a fresh {@link #workspace} temp directory used as the project root;
 *   <li>run artifacts are redirected into {@code workspace/.ful/runs} via the {@code ful.runsRoot}
 *       system property (honored by {@code BaseCliCommand}), so nothing is written to the repo;
 *   <li>global {@code Logger} de-duplication state is reset after each test.
 * </ul>
 *
 * <p>Lives in the {@code com.craftsmanbro.fulcraft} package so subclasses can call the
 * package-private {@link Main#run(String[])} (which returns an exit code without {@code
 * System.exit}).
 */
abstract class E2eTestBase {

  @TempDir protected Path workspace;

  private String previousRunsRoot;

  @BeforeEach
  void redirectRunsRoot() {
    previousRunsRoot = System.getProperty("ful.runsRoot");
    System.setProperty("ful.runsRoot", runsRoot().toString());
  }

  @AfterEach
  void resetGlobalState() {
    if (previousRunsRoot == null) {
      System.clearProperty("ful.runsRoot");
    } else {
      System.setProperty("ful.runsRoot", previousRunsRoot);
    }
    Logger.resetWarnOnceKeys();
    Logger.resetInfoOnceKeys();
    // Main.run reconfigures global logger state; reset it so tests can't leak settings/context.
    Logger.setJsonMode(false);
    Logger.clearContext();
  }

  /** Run the CLI in-process and return its exit code. */
  protected int runCli(final String... args) {
    return Main.run(args);
  }

  /** Root under which the CLI writes {@code <runId>/...} for this test. */
  protected Path runsRoot() {
    return workspace.resolve(".ful").resolve("runs");
  }
}
