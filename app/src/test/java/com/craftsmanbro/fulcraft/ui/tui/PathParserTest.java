package com.craftsmanbro.fulcraft.ui.tui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.ui.tui.config.ConfigEditor;
import java.util.List;
import org.junit.jupiter.api.Test;

class PathParserTest {

  @Test
  void parseShouldHandleDotPaths() {
    PathParser.ParsedPath parsed = PathParser.parse("llm.max_retries");

    assertThat(parsed.append()).isFalse();
    assertThat(parsed.hasIndex()).isFalse();
    assertThat(PathParser.toMetadataPath(parsed.segments())).isEqualTo("llm.max_retries");
  }

  @Test
  void parseShouldHandleListIndex() {
    PathParser.ParsedPath parsed = PathParser.parse("selection_rules.exclude_annotations[0]");

    List<ConfigEditor.PathSegment> segments = parsed.segments();
    assertThat(parsed.hasIndex()).isTrue();
    assertThat(segments).hasSize(3);
    assertThat(segments.get(0).isKey()).isTrue();
    assertThat(segments.get(0).key()).isEqualTo("selection_rules");
    assertThat(segments.get(1).isKey()).isTrue();
    assertThat(segments.get(1).key()).isEqualTo("exclude_annotations");
    assertThat(segments.get(2).isIndex()).isTrue();
    assertThat(segments.get(2).index()).isEqualTo(0);
    assertThat(PathParser.toMetadataPath(segments))
        .isEqualTo("selection_rules.exclude_annotations");
    assertThat(PathParser.toPathString(segments))
        .isEqualTo("selection_rules.exclude_annotations[0]");
  }

  @Test
  void parseShouldHandleListAppend() {
    PathParser.ParsedPath parsed = PathParser.parse("selection_rules.exclude_annotations[]");

    assertThat(parsed.append()).isTrue();
    assertThat(parsed.hasIndex()).isFalse();
    assertThat(PathParser.toMetadataPath(parsed.segments()))
        .isEqualTo("selection_rules.exclude_annotations");
  }

  @Test
  void parseShouldRejectInvalidIndex() {
    assertThatThrownBy(() -> PathParser.parse("selection_rules.exclude_annotations[abc]"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseShouldRejectMissingSeparatorAfterIndex() {
    assertThatThrownBy(() -> PathParser.parse("foo[0]bar"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseShouldRejectTrailingDot() {
    assertThatThrownBy(() -> PathParser.parse("llm.")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PathParser.parse("foo[0]."))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseShouldRejectDotBeforeIndex() {
    assertThatThrownBy(() -> PathParser.parse("foo.[0]"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseShouldTrimInputAndAllowDotAfterIndex() {
    PathParser.ParsedPath parsed = PathParser.parse("  foo[0].bar  ");

    assertThat(parsed.append()).isFalse();
    assertThat(parsed.hasIndex()).isTrue();
    assertThat(PathParser.toPathString(parsed.segments())).isEqualTo("foo[0].bar");
  }

  @Test
  void parseShouldRejectNullOrBlankPath() {
    assertThatThrownBy(() -> PathParser.parse(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PathParser.parse("  ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseShouldRejectUnexpectedClosingBracket() {
    assertThatThrownBy(() -> PathParser.parse("foo]")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseShouldRejectMissingClosingBracket() {
    assertThatThrownBy(() -> PathParser.parse("foo[0"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseShouldRejectNegativeIndex() {
    assertThatThrownBy(() -> PathParser.parse("foo[-1]"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseShouldRejectIndexWithoutKey() {
    assertThatThrownBy(() -> PathParser.parse("[0]")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PathParser.parse("foo[0][1]"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseShouldRejectEmptyDotSegments() {
    assertThatThrownBy(() -> PathParser.parse(".foo")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PathParser.parse("foo..bar"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseShouldRejectAppendInMiddle() {
    assertThatThrownBy(() -> PathParser.parse("foo[].bar"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toPathAndMetadataPathShouldReturnEmptyForNullOrEmptySegments() {
    assertThat(PathParser.toPathString(null)).isEmpty();
    assertThat(PathParser.toPathString(List.of())).isEmpty();
    assertThat(PathParser.toMetadataPath(null)).isEmpty();
    assertThat(PathParser.toMetadataPath(List.of())).isEmpty();
  }

  @Test
  void parsedPathShouldNormalizeNullSegmentsToEmptyImmutableList() {
    PathParser.ParsedPath parsed = new PathParser.ParsedPath(null, false);

    assertThat(parsed.segments()).isEmpty();
    assertThatThrownBy(() -> parsed.segments().add(ConfigEditor.PathSegment.key("x")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
