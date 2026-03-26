package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ResolutionStatus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PostProcessingUtilsTest {

  @Test
  void buildCalledMethodRefs_honorsResolutionStatusAndUnknownCalls() {
    List<String> calls = List.of("com.example.A#foo()", "unknown#bar()", "com.example.B#baz()");
    Map<String, ResolutionStatus> statuses =
        Map.of("com.example.B#baz()", ResolutionStatus.AMBIGUOUS);
    Map<String, List<String>> argumentLiterals =
        Map.of(
            "com.example.A#foo()",
            List.of("\"literal-a\"", "\"literal-a\""),
            "unknown#bar()",
            List.of("\"mystery\""));

    List<CalledMethodRef> refs =
        PostProcessingUtils.buildCalledMethodRefs(
            calls, "analysis", statuses, true, argumentLiterals);

    assertThat(refs).hasSize(3);
    assertThat(refs.get(0).getResolved()).isEqualTo("com.example.A#foo()");
    assertThat(refs.get(0).getStatus()).isEqualTo(ResolutionStatus.RESOLVED);
    assertThat(refs.get(0).getConfidence()).isEqualTo(1.0);
    assertThat(refs.get(0).getSource()).isEqualTo("analysis");
    assertThat(refs.get(0).getArgumentLiterals()).containsExactly("\"literal-a\"");

    assertThat(refs.get(1).getResolved()).isNull();
    assertThat(refs.get(1).getStatus()).isEqualTo(ResolutionStatus.UNRESOLVED);
    assertThat(refs.get(1).getConfidence()).isEqualTo(0.3);
    assertThat(refs.get(1).getCandidates()).containsExactly("unknown#bar()");
    assertThat(refs.get(1).getArgumentLiterals()).containsExactly("\"mystery\"");

    assertThat(refs.get(2).getResolved()).isNull();
    assertThat(refs.get(2).getStatus()).isEqualTo(ResolutionStatus.AMBIGUOUS);
    assertThat(refs.get(2).getConfidence()).isEqualTo(0.3);
    assertThat(refs.get(2).getCandidates()).containsExactly("com.example.B#baz()");
    assertThat(refs.get(2).getArgumentLiterals()).isEmpty();
  }

  @Test
  void logUnresolvedCalls_emitsWarningAndSuppressionNotice() {
    CalledMethodRef ref1 = new CalledMethodRef();
    ref1.setRaw("com.example.A#foo()");
    ref1.setStatus(ResolutionStatus.UNRESOLVED);
    ref1.setCandidates(List.of("com.example.A#foo()"));

    CalledMethodRef ref2 = new CalledMethodRef();
    ref2.setRaw("com.example.B#bar()");
    ref2.setStatus(ResolutionStatus.UNRESOLVED);

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    ch.qos.logback.classic.Logger targetLogger = attachAppender(appender);
    Level previousLevel = targetLogger.getLevel();
    targetLogger.setLevel(Level.WARN);

    try {
      PostProcessingUtils.logUnresolvedCalls(
          "com.example.C#caller()", List.of(ref1, ref2), new AtomicInteger(0), 1);

      assertThat(appender.list).hasSize(2);
      assertThat(appender.list.get(0).getFormattedMessage()).contains("com.example.A#foo()");
      assertThat(appender.list.get(0).getFormattedMessage()).contains("TypeResolution");
      assertThat(appender.list.get(1).getFormattedMessage()).contains("TypeResolution");
    } finally {
      detachAppender(targetLogger, appender, previousLevel);
    }
  }

  @Test
  void markDuplicates_marksMethodsWithSameHash() {
    AnalysisContext context = new AnalysisContext();
    MethodInfo methodA = new MethodInfo();
    MethodInfo methodB = new MethodInfo();
    MethodInfo methodC = new MethodInfo();

    context.getMethodInfos().put("A#foo()", methodA);
    context.getMethodInfos().put("B#bar()", methodB);
    context.getMethodInfos().put("C#baz()", methodC);

    context.getMethodCodeHash().put("A#foo()", "hash-1");
    context.getMethodCodeHash().put("B#bar()", "hash-1");
    context.getMethodCodeHash().put("C#baz()", "hash-2");
    context.getMethodCodeHash().put("D#skip()", "");

    PostProcessingUtils.markDuplicates(context, 2);

    assertThat(methodA.isDuplicate()).isTrue();
    assertThat(methodB.isDuplicate()).isTrue();
    assertThat(methodA.getDuplicateGroup()).isEqualTo("hash-1");
    assertThat(methodB.getDuplicateGroup()).isEqualTo("hash-1");
    assertThat(methodC.isDuplicate()).isFalse();
  }

  private ch.qos.logback.classic.Logger attachAppender(ListAppender<ILoggingEvent> appender) {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    ch.qos.logback.classic.Logger targetLogger = context.getLogger("utgenerator");
    appender.setContext(context);
    appender.start();
    targetLogger.addAppender(appender);
    return targetLogger;
  }

  private void detachAppender(
      ch.qos.logback.classic.Logger targetLogger,
      ListAppender<ILoggingEvent> appender,
      Level previousLevel) {
    targetLogger.detachAppender(appender);
    targetLogger.setLevel(previousLevel);
    appender.stop();
  }
}
