package com.craftsmanbro.fulcraft.ui.cli;

import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.ui.cli.wiring.ServiceFactory;
import java.nio.file.Path;

/** Shared root command context exposed to CLI command and bootstrap layers. */
public interface CliContext {

  ServiceFactory getServices();

  ConfigLoaderPort getConfigLoader();

  Path getConfigFile();

  String getLanguageTag();
}
