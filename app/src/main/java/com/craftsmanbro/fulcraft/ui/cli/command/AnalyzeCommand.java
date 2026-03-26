package com.craftsmanbro.fulcraft.ui.cli.command;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import java.util.List;
import picocli.CommandLine.Command;

/**
 * CLI command for running only the analysis stage.
 *
 * <p>Analyzes the project source code and generates analysis files.
 *
 * <p>Usage:
 *
 * <pre>
 *   ful analyze /path/to/project
 *   ful analyze .
 * </pre>
 */
@Command(
    name = "analyze",
    description = "${command.analyze.description}",
    footer = "${command.analyze.footer}",
    resourceBundle = "messages",
    mixinStandardHelpOptions = true)
@Category("analysis")
public class AnalyzeCommand extends AbstractCliCommand {

  @Override
  protected List<String> getNodeIds() {
    return List.of(PipelineNodeIds.ANALYZE);
  }

  @Override
  protected String getCommandDescription() {
    return MessageSource.getMessage("analyze.command.description");
  }
}
