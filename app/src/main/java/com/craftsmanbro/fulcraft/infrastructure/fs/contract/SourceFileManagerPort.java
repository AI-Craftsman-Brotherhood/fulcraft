package com.craftsmanbro.fulcraft.infrastructure.fs.contract;

import com.craftsmanbro.fulcraft.infrastructure.fs.model.TestFilePlan;
import java.io.IOException;
import java.nio.file.Path;

/** Contract for resolving source and test file paths and reading or writing file content. */
public interface SourceFileManagerPort {

  /**
   * Resolves an existing source file from a project-relative path or supported source root.
   *
   * @param projectRoot root directory of the target project
   * @param filePath source file path recorded for the task
   * @param taskId task identifier used in error reporting
   * @return absolute path to the existing source file
   * @throws IOException when the source file cannot be resolved
   */
  Path resolveSourcePathOrThrow(Path projectRoot, String filePath, String taskId)
      throws IOException;

  /**
   * Plans the destination path and class name for a generated test file without creating it.
   *
   * @param projectRoot root directory of the target project
   * @param packageName package name for the generated test
   * @param baseTestClassName preferred test class name
   * @return planned test file metadata
   */
  TestFilePlan planTestFile(Path projectRoot, String packageName, String baseTestClassName);

  /**
   * Persists generated test code for later inspection after a failed generation attempt.
   *
   * @param projectRoot root directory of the target project
   * @param packageName package name of the generated test
   * @param testClassName generated test class name
   * @param code generated test source to persist
   * @throws IOException when the file cannot be written
   */
  void saveFailedTest(Path projectRoot, String packageName, String testClassName, String code)
      throws IOException;

  /**
   * Reads UTF-8 text content from the given path.
   *
   * @param path file to read
   * @return file content as a string
   * @throws IOException when an I/O error occurs
   */
  String readString(Path path) throws IOException;

  /**
   * Writes UTF-8 text content to the given path, creating parent directories when needed.
   *
   * @param path destination file path
   * @param content text content to write
   * @throws IOException when an I/O error occurs
   */
  void writeString(Path path, String content) throws IOException;
}
