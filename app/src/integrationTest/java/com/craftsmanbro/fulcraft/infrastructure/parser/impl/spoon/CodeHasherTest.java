package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.CodeHashing;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;

class CodeHasherTest {

  @TempDir Path tempDir;

  @Test
  void returnsNullWhenExecutableMissingOrHasNoBody() throws Exception {
    assertNull(CodeHasher.computeCodeHash(null));

    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Sample.java",
            """
            package com.example;

            public abstract class Sample {
                public abstract void noBody();
            }
            """);
    CtMethod<?> noBody =
        SpoonTestUtils.getType(launcher, "com.example.Sample").getMethodsByName("noBody").get(0);

    assertNull(CodeHasher.computeCodeHash(noBody));
  }

  @Test
  void hashesExecutableBodyUsingNormalizedHash() throws Exception {
    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Sample.java",
            """
            package com.example;

            public class Sample {
                public void foo() {
                    int value = 1;
                    if (value > 0) {
                        value++;
                    }
                }
            }
            """);
    CtMethod<?> foo =
        SpoonTestUtils.getType(launcher, "com.example.Sample").getMethodsByName("foo").get(0);

    String expected = CodeHashing.hashNormalized(foo.getBody().toString());

    assertEquals(expected, CodeHasher.computeCodeHash(foo));
  }

  @Test
  void returnsNullWhenBodyStringIsBlank() {
    CtExecutable<?> executable = mock(CtExecutable.class);
    CtBlock<?> body = mock(CtBlock.class);
    doReturn(body).when(executable).getBody();
    when(body.toString()).thenReturn("   ");

    assertNull(CodeHasher.computeCodeHash(executable));
  }
}
