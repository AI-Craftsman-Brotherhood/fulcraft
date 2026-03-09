package com.craftsmanbro.fulcraft.infrastructure.io.contract;

import com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileEntry;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Contract for iterating task and result entries stored in a tasks file. */
public interface TasksFileReader extends Iterable<TasksFileEntry>, AutoCloseable {

  /**
   * Returns an iterator over task and result entries in source order.
   *
   * @return ordered iterator over task and result entries
   */
  @Override
  Iterator<TasksFileEntry> iterator();

  /** Closes any resources held by this reader. */
  @Override
  void close();

  /**
   * Returns a sequential stream backed by this reader's iterator.
   *
   * <p>Closing the returned stream also closes this reader.
   *
   * @return sequential stream of task and result entries
   */
  default Stream<TasksFileEntry> stream() {
    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED), false)
        .onClose(this::close);
  }
}
