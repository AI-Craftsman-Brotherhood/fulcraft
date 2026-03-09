package com.craftsmanbro.fulcraft.plugins.reporting.taskio;

/** Reader abstraction for task entries. */
public interface TaskEntriesReader extends Iterable<TaskEntry>, AutoCloseable {

  @Override
  void close();
}
