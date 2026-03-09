package com.craftsmanbro.fulcraft.plugins.document.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HtmlReportingStyleTest {

  private static final String STYLE_CLASS =
      "com.craftsmanbro.fulcraft.plugins.document.adapter.HtmlReportingStyle";
  private static final String CSS_RESOURCE = "styles/html_reporting.css";

  @Test
  void css_shouldExposeThemeVariables() {
    String css = HtmlReportingStyle.css();

    assertThat(css).contains(":root");
    assertThat(css).contains("--bg-body");
    assertThat(css).contains("--primary-500");
    assertThat(css).contains("table");
  }

  @Test
  void css_shouldMatchClasspathResource() throws IOException {
    String css = HtmlReportingStyle.css();
    String expected = readResource(CSS_RESOURCE);

    assertThat(css).isEqualTo(expected);
  }

  @Test
  void css_shouldReturnCachedInstance() {
    String first = HtmlReportingStyle.css();
    String second = HtmlReportingStyle.css();

    assertThat(second).isSameAs(first);
  }

  @Test
  void css_shouldFailFast_whenCssResourceIsMissing() {
    Throwable thrown =
        catchThrowable(
            () -> Class.forName(STYLE_CLASS, true, new IsolatedStyleClassLoader(false, false)));

    assertThat(thrown).isInstanceOf(ExceptionInInitializerError.class);
    assertThat(thrown.getCause()).isInstanceOf(IllegalStateException.class);
    assertThat(thrown.getCause()).hasMessageContaining(CSS_RESOURCE);
  }

  @Test
  void css_shouldWrapIoError_whenCssResourceReadFails() {
    Throwable thrown =
        catchThrowable(
            () -> Class.forName(STYLE_CLASS, true, new IsolatedStyleClassLoader(true, true)));

    assertThat(thrown).isInstanceOf(ExceptionInInitializerError.class);
    assertThat(thrown.getCause()).isInstanceOf(UncheckedIOException.class);
    assertThat(thrown.getCause()).hasMessageContaining(CSS_RESOURCE);
    assertThat(thrown.getCause()).hasCauseInstanceOf(IOException.class);
  }

  private String readResource(String resourcePath) throws IOException {
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(input).isNotNull();
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static final class IsolatedStyleClassLoader extends ClassLoader {
    private final boolean resourceAvailable;
    private final boolean failOnRead;

    private IsolatedStyleClassLoader(boolean resourceAvailable, boolean failOnRead) {
      super(HtmlReportingStyleTest.class.getClassLoader());
      this.resourceAvailable = resourceAvailable;
      this.failOnRead = failOnRead;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        if (STYLE_CLASS.equals(name)) {
          Class<?> loadedClass = findLoadedClass(name);
          if (loadedClass == null) {
            loadedClass = findClass(name);
          }
          if (resolve) {
            resolveClass(loadedClass);
          }
          return loadedClass;
        }
        return super.loadClass(name, resolve);
      }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      String classResourcePath = name.replace('.', '/') + ".class";
      try (InputStream classBytes =
          HtmlReportingStyleTest.class.getClassLoader().getResourceAsStream(classResourcePath)) {
        if (classBytes == null) {
          throw new ClassNotFoundException(name);
        }
        byte[] bytecode = classBytes.readAllBytes();
        return defineClass(name, bytecode, 0, bytecode.length);
      } catch (IOException e) {
        throw new ClassNotFoundException(name, e);
      }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      if (CSS_RESOURCE.equals(name)) {
        if (!resourceAvailable) {
          return null;
        }
        if (failOnRead) {
          return new FailingInputStream();
        }
      }
      return super.getResourceAsStream(name);
    }
  }

  private static final class FailingInputStream extends InputStream {
    @Override
    public int read() throws IOException {
      throw new IOException("forced css read failure");
    }
  }
}
