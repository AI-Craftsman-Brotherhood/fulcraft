package com.craftsmanbro.fulcraft.infrastructure.json.contract;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/**
 * Contract for deterministic JSON serialization, deserialization, and structural conversion.
 *
 * <p>This contract hides the underlying JSON library (e.g., Jackson) from most consumers,
 * providing a stable API that does not leak third-party types. Common application-level JSON
 * operations should go through this interface.
 *
 * <p>Implementations must guarantee deterministic output, including sorted keys and consistent
 * formatting.
 */
public interface JsonServicePort {

  /**
   * Writes an object as JSON to a file with pretty printing.
   *
   * @param file the output file path
   * @param value the object to serialize
   * @throws IOException if writing fails
   */
  void writeToFile(Path file, Object value) throws IOException;

  /**
   * Writes an object as compact (non-pretty) JSON to a file.
   *
   * @param file the output file path
   * @param value the object to serialize
   * @throws IOException if writing fails
   */
  void writeToFileCompact(Path file, Object value) throws IOException;

  /**
   * Reads a JSON file and deserializes it into the specified type.
   *
   * @param file the input file path
   * @param type the target class
   * @param <T> the target type
   * @return the deserialized object
   * @throws IOException if reading or deserialization fails
   */
  <T> T readFromFile(Path file, Class<T> type) throws IOException;

  /**
   * Reads a JSON file into a {@code LinkedHashMap<String, Object>}, preserving key order.
   *
   * @param file the input file path
   * @return the deserialized map, or an empty map if the file does not exist
   * @throws IOException if reading fails
   */
  LinkedHashMap<String, Object> readMapFromFile(Path file) throws IOException;

  /**
   * Reads a JSON string into a {@code LinkedHashMap<String, Object>}, preserving key order.
   *
   * @param json the JSON string
   * @return the deserialized map, or an empty map if the JSON string is {@code null} or blank
   * @throws IOException if parsing fails
   */
  LinkedHashMap<String, Object> readMapFromString(String json) throws IOException;

  /**
   * Serializes an object to a JSON string (compact, single-line).
   *
   * @param value the object to serialize
   * @return the JSON string
   * @throws IOException if serialization fails
   */
  String toJson(Object value) throws IOException;

  /**
   * Serializes an object to a pretty-printed JSON string.
   *
   * @param value the object to serialize
   * @return the pretty-printed JSON string
   * @throws IOException if serialization fails
   */
  String toJsonPretty(Object value) throws IOException;

  /**
   * Deserializes a JSON string into the specified type.
   *
   * @param json the JSON string
   * @param type the target class
   * @param <T> the target type
   * @return the deserialized object
   * @throws IOException if deserialization fails
   */
  <T> T fromJson(String json, Class<T> type) throws IOException;

  /**
   * Converts an object (typically a Map or another POJO) into the specified type using JSON-based
   * structural mapping.
   *
   * <p>This is useful for converting untyped payloads (e.g., {@code Map<String, Object>}) into
   * strongly-typed domain objects. Unknown properties in the source are ignored.
   *
   * @param source the source object
   * @param type the target class
   * @param <T> the target type
   * @return the converted object, or {@code null} if conversion fails
   */
  <T> T convert(Object source, Class<T> type);
}
