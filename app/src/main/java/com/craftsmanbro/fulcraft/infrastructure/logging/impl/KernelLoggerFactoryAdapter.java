package com.craftsmanbro.fulcraft.infrastructure.logging.impl;

import com.craftsmanbro.fulcraft.logging.LoggerPort;
import com.craftsmanbro.fulcraft.logging.LoggerPortFactory;
import java.util.Objects;

/** Adapter that routes logging port calls to the infrastructure Logger. */
public final class KernelLoggerFactoryAdapter implements LoggerPortFactory {

  @Override
  public LoggerPort getLogger(final Class<?> type) {
    return new InfrastructureKernelLogger(type);
  }

  private static final class InfrastructureKernelLogger implements LoggerPort {

    private final String name;

    private InfrastructureKernelLogger(final Class<?> type) {
      this.name = type != null ? type.getName() : "unknown";
    }

    @Override
    public void debug(final String message, final Object... args) {
      Logger.debug(prefix(format(message, args)));
    }

    @Override
    public void info(final String message, final Object... args) {
      Logger.info(prefix(format(message, args)));
    }

    @Override
    public void warn(final String message, final Object... args) {
      Logger.warn(prefix(format(message, args)));
    }

    @Override
    public void warn(final String message, final Throwable t) {
      Logger.warn(prefix(message), t);
    }

    @Override
    public void error(final String message, final Object... args) {
      Logger.error(prefix(format(message, args)));
    }

    @Override
    public void error(final String message, final Throwable t) {
      Logger.error(prefix(message), t);
    }

    private String prefix(final String message) {
      if (message == null) {
        return "[" + name + "] null";
      }
      return "[" + name + "] " + message;
    }

    private static String format(final String template, final Object... args) {
      if (template == null) {
        return "null";
      }
      if (args == null || args.length == 0) {
        return template;
      }
      final StringBuilder out = new StringBuilder();
      int index = 0;
      int argIndex = 0;
      while (index < template.length()) {
        final int placeholder = template.indexOf("{}", index);
        if (placeholder == -1 || argIndex >= args.length) {
          out.append(template, index, template.length());
          break;
        }
        out.append(template, index, placeholder);
        out.append(Objects.toString(args[argIndex++]));
        index = placeholder + 2;
      }
      return out.toString();
    }
  }
}
