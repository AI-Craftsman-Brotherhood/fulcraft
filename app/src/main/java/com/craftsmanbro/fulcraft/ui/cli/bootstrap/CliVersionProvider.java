package com.craftsmanbro.fulcraft.ui.cli.bootstrap;

import com.craftsmanbro.fulcraft.ui.banner.StartupBannerSupport;
import picocli.CommandLine.IVersionProvider;

/** Supplies CLI version text from packaged application metadata. */
public final class CliVersionProvider implements IVersionProvider {

  @Override
  public String[] getVersion() {
    return new String[] {"ful " + StartupBannerSupport.resolveApplicationVersion()};
  }
}
