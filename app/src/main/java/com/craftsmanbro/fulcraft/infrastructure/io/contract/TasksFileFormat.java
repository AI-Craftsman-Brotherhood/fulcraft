package com.craftsmanbro.fulcraft.infrastructure.io.contract;

import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.io.IOException;
import java.nio.file.Path;

/** Contract for reading and writing task and result entries in a concrete file format. */
public interface TasksFileFormat {

  /**
   * Opens a reader over task and result entries stored in the given tasks file.
   *
   * @param path path to the tasks file to read
   * @return reader over task and result entries stored in the given tasks file
   * @throws IOException when the file cannot be opened or parsed
   */
  TasksFileReader read(Path path) throws IOException;

  /**
   * Writes task and result entries to the given tasks file.
   *
   * @param tasks task records to persist
   * @param results generation results to persist
   * @param path path to the tasks file to write
   * @throws IOException when the file cannot be written
   */
  void write(Iterable<TaskRecord> tasks, Iterable<GenerationTaskResult> results, Path path)
      throws IOException;
}
