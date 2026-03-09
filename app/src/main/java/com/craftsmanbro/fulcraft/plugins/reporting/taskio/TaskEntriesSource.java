package com.craftsmanbro.fulcraft.plugins.reporting.taskio;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;

/** Source abstraction for loading task entries independent from concrete file format models. */
public interface TaskEntriesSource {

  TaskEntriesReader read(Path path) throws IOException;

  TaskEntriesReader readJsonl(BufferedReader reader);

  Path resolveExistingTasksFile(Path directory);
}
