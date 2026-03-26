package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import spoon.Launcher;
import spoon.reflect.declaration.CtType;

final class SpoonTestUtils {

  private SpoonTestUtils() {}

  static Launcher buildLauncher(Path projectRoot, String relativePath, String source)
      throws IOException {
    Path file = projectRoot.resolve(relativePath);
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(file, source);
    Launcher launcher = new Launcher();
    launcher.addInputResource(file.toString());
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setComplianceLevel(17);
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.buildModel();
    return launcher;
  }

  static CtType<?> getType(Launcher launcher, String fqn) {
    CtType<?> type = launcher.getFactory().Type().get(fqn);
    if (type == null) {
      throw new AssertionError("Type not found: " + fqn);
    }
    return type;
  }
}
