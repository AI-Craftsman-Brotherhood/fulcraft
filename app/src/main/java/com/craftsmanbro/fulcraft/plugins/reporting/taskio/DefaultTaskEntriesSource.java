package com.craftsmanbro.fulcraft.plugins.reporting.taskio;

import com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileReader;
import com.craftsmanbro.fulcraft.infrastructure.io.impl.JsonlTasksFileFormat;
import com.craftsmanbro.fulcraft.infrastructure.io.impl.TasksFileFormatFactory;
import com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileEntry;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.plugins.reporting.model.FeatureModelMapper;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Default task entry source backed by tasks file formats (plan only, no result). */
public class DefaultTaskEntriesSource implements TaskEntriesSource {

  @Override
  public TaskEntriesReader read(final Path path) throws IOException {
    Objects.requireNonNull(
        path,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "path"));
    final TasksFileFormatFactory factory = new TasksFileFormatFactory(JsonMapperFactory.create());
    return new TaskEntriesReaderAdapter(factory.formatForPath(path).read(path));
  }

  @Override
  public TaskEntriesReader readJsonl(final BufferedReader reader) {
    Objects.requireNonNull(
        reader,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "reader"));
    return new TaskEntriesReaderAdapter(
        new JsonlTasksFileFormat(JsonMapperFactory.create()).read(reader));
  }

  @Override
  public Path resolveExistingTasksFile(final Path directory) {
    Objects.requireNonNull(
        directory,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "report.common.error.argument_null", "directory"));
    final TasksFileFormatFactory factory = new TasksFileFormatFactory(JsonMapperFactory.create());
    return factory.resolveExistingTasksFile(directory);
  }

  private static final class TaskEntriesReaderAdapter implements TaskEntriesReader {

    private final TasksFileReader delegate;

    private TaskEntriesReaderAdapter(final TasksFileReader delegate) {
      this.delegate =
          Objects.requireNonNull(
              delegate,
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "report.common.error.argument_null", "delegate"));
    }

    @Override
    public Iterator<TaskEntry> iterator() {
      final Iterator<TasksFileEntry> iterator = delegate.iterator();
      return new Iterator<>() {

        private TaskEntry next;

        private boolean fetched;

        @Override
        public boolean hasNext() {
          if (!fetched) {
            fetchNext();
          }
          return next != null;
        }

        @Override
        public TaskEntry next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          final TaskEntry value = next;
          next = null;
          fetched = false;
          return value;
        }

        private void fetchNext() {
          fetched = true;
          next = null;
          while (iterator.hasNext()) {
            final TaskEntry converted = toTaskEntry(iterator.next());
            if (converted != null) {
              next = converted;
              return;
            }
          }
        }

        private TaskEntry toTaskEntry(final TasksFileEntry raw) {
          if (!raw.hasTask()) {
            return null;
          }
          final TaskRecord task = FeatureModelMapper.toTaskRecord(raw.getTask());
          return task != null ? new TaskEntry(task) : null;
        }
      };
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
