package com.craftsmanbro.fulcraft.ui.cli.spi;

import java.util.concurrent.Callable;

/**
 * SPI contract for Picocli commands discovered via {@link java.util.ServiceLoader}.
 *
 * <p>Implementations should be concrete {@code @Command} types and be listed in {@code
 * META-INF/services/com.craftsmanbro.fulcraft.ui.cli.spi.CliCommand}.
 */
@FunctionalInterface
public interface CliCommand extends Callable<Integer> {}
