package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dictionary-based sensitive data detector.
 *
 * <p>Detects sensitive terms using deny/allow lists. The denylist contains terms that should be
 * flagged as sensitive, while the allowlist contains terms that should be excluded from detection.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Case-insensitive matching
 *   <li>Full-width/half-width character normalization
 *   <li>Configurable partial vs exact word matching
 *   <li>Support for loading dictionaries from files
 * </ul>
 */
public final class DictionaryDetector implements SensitiveDetector {

  private static final Logger LOG = LoggerFactory.getLogger(DictionaryDetector.class);

  public static final String NAME = "dictionary";

  public static final String TYPE_DICTIONARY = "DICTIONARY";

  private static final double DEFAULT_CONFIDENCE = 0.80;

  private final Set<String> denylist = new HashSet<>();

  private final Set<String> allowlist = new HashSet<>();

  private final boolean exactMatchOnly;

  private String denylistSource = "inline";

  /** Creates a detector with empty lists and partial matching. */
  public DictionaryDetector() {
    this(false);
  }

  /**
   * Creates a detector with specified matching mode.
   *
   * @param exactMatchOnly If true, only match whole words; if false, match as substrings
   */
  public DictionaryDetector(final boolean exactMatchOnly) {
    this.exactMatchOnly = exactMatchOnly;
  }

  /**
   * Creates a detector loaded from file paths.
   *
   * @param denylistPath Path to denylist file (one term per line)
   * @param allowlistPath Path to allowlist file (one term per line), may be null
   * @param exactMatchOnly Matching mode
   * @return Configured detector
   */
  public static DictionaryDetector fromFiles(
      final Path denylistPath, final Path allowlistPath, final boolean exactMatchOnly) {
    final DictionaryDetector detector = new DictionaryDetector(exactMatchOnly);
    if (denylistPath != null) {
      try {
        detector.loadDenylist(denylistPath);
        final Path fileName = denylistPath.getFileName();
        detector.denylistSource = fileName != null ? fileName.toString() : denylistPath.toString();
      } catch (final IOException e) {
        LOG.warn(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.redaction.dictionary.warn.load_denylist_failed",
                denylistPath,
                e.getMessage()));
      }
    }
    if (allowlistPath != null) {
      try {
        detector.loadAllowlist(allowlistPath);
      } catch (final IOException e) {
        LOG.warn(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.redaction.dictionary.warn.load_allowlist_failed",
                allowlistPath,
                e.getMessage()));
      }
    }
    return detector;
  }

  /**
   * Loads denylist terms from a file.
   *
   * @param path Path to denylist file
   * @throws IOException If file cannot be read
   */
  public void loadDenylist(final Path path) throws IOException {
    loadTerms(path, denylist);
    LOG.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.redaction.dictionary.debug.denylist_loaded", denylist.size(), path));
  }

  /**
   * Loads allowlist terms from a file.
   *
   * @param path Path to allowlist file
   * @throws IOException If file cannot be read
   */
  public void loadAllowlist(final Path path) throws IOException {
    loadTerms(path, allowlist);
    LOG.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.redaction.dictionary.debug.allowlist_loaded", allowlist.size(), path));
  }

  private void loadTerms(final Path path, final Set<String> target) throws IOException {
    if (!Files.exists(path)) {
      LOG.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.redaction.dictionary.warn.file_not_exists", path));
      return;
    }
    final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    for (final String line : lines) {
      final String trimmed = line.trim();
      // Skip empty lines and comments
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      target.add(normalize(trimmed));
    }
  }

  /**
   * Adds a term to the denylist programmatically.
   *
   * @param term Term to add
   */
  public void addDenyTerm(final String term) {
    addTerm(term, denylist);
  }

  /**
   * Adds a term to the allowlist programmatically.
   *
   * @param term Term to add
   */
  public void addAllowTerm(final String term) {
    addTerm(term, allowlist);
  }

  private void addTerm(final String term, final Set<String> target) {
    if (term == null || term.isBlank()) {
      return;
    }

    target.add(normalize(term));
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isEnabled(final DetectionContext ctx) {
    return ctx.isDetectorEnabled(NAME) && !denylist.isEmpty();
  }

  @Override
  public DetectionResult detect(final String text, final DetectionContext ctx) {
    if (text == null || text.isEmpty() || denylist.isEmpty()) {
      return DetectionResult.EMPTY;
    }
    final List<Finding> findings = new ArrayList<>();
    final String normalizedText = normalize(text);
    final String ruleId = "dictionary:" + denylistSource;
    for (final String term : denylist) {
      // Skip if term is in allowlist
      if (allowlist.contains(term)) {
        continue;
      }
      final List<int[]> matches = findMatches(normalizedText, term);
      for (final int[] match : matches) {
        final int start = match[0];
        final int end = match[1];
        final String snippet = text.substring(start, end);
        // Check if snippet is allowlisted
        if (ctx.isAllowlisted(snippet) || allowlist.contains(normalize(snippet))) {
          continue;
        }
        findings.add(new Finding(TYPE_DICTIONARY, start, end, DEFAULT_CONFIDENCE, snippet, ruleId));
      }
    }
    return DetectionResult.of(findings);
  }

  private List<int[]> findMatches(final String normalizedText, final String term) {
    final List<int[]> matches = new ArrayList<>();
    if (term == null || term.isEmpty()) {
      return matches;
    }
    if (exactMatchOnly) {
      // Word boundary matching
      final Pattern pattern =
          Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE);
      final Matcher matcher = pattern.matcher(normalizedText);
      while (matcher.find()) {
        matches.add(new int[] {matcher.start(), matcher.end()});
      }
    } else {
      // Substring matching
      int index = 0;
      while ((index = normalizedText.indexOf(term, index)) >= 0) {
        matches.add(new int[] {index, index + term.length()});
        index += 1;
      }
    }
    return matches;
  }

  /**
   * Normalizes text for comparison.
   *
   * <p>Handles:
   *
   * <ul>
   *   <li>Case normalization (lowercase)
   *   <li>Full-width to half-width conversion
   * </ul>
   */
  private String normalize(final String text) {
    if (text == null) {
      return "";
    }
    final String loweredText = text.toLowerCase(Locale.ROOT);
    final StringBuilder normalized = new StringBuilder(loweredText.length());
    for (final char c : loweredText.toCharArray()) {
      // Full-width lowercase to half-width lowercase
      if (c >= 'ａ' && c <= 'ｚ') {
        normalized.append((char) (c - 'ａ' + 'a'));
      } else if (c >= '０' && c <= '９') { // Full-width digits to half-width
        normalized.append((char) (c - '０' + '0'));
      } else { // Regular characters (already lowercased)
        normalized.append(c);
      }
    }
    return normalized.toString();
  }

  /**
   * Returns the count of terms in the denylist.
   *
   * @return Denylist size
   */
  public int getDenylistSize() {
    return denylist.size();
  }

  /**
   * Returns the count of terms in the allowlist.
   *
   * @return Allowlist size
   */
  public int getAllowlistSize() {
    return allowlist.size();
  }
}
