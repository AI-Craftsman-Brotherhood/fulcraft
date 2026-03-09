package com.craftsmanbro.fulcraft.plugins.document;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.stage.StageException;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPluginException;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginContext;
import com.craftsmanbro.fulcraft.kernel.plugin.api.PluginResult;
import com.craftsmanbro.fulcraft.plugins.document.flow.DocumentFlow;
import com.craftsmanbro.fulcraft.plugins.document.stage.DocumentStage;
import java.util.Objects;

/** Built-in workflow plugin that executes {@link DocumentStage}. */
public final class DocumentPlugin implements ActionPlugin {

  public static final String PLUGIN_ID = "document-builtin";

  @Override
  public String id() {
    return PLUGIN_ID;
  }

  @Override
  public String kind() {
    return "workflow";
  }

  @Override
  public PluginResult execute(final PluginContext context) throws ActionPluginException {
    Objects.requireNonNull(
        context, MessageSource.getMessage("document.common.error.argument_null", "context"));
    final RunContext runContext = context.getRunContext();
    final DocumentFlow documentFlow;
    try {
      documentFlow = context.getServiceRegistry().require(DocumentFlow.class);
    } catch (IllegalStateException e) {
      throw new ActionPluginException(
          MessageSource.getMessage("document.plugin.error.document_flow_required"), e);
    }
    try {
      new DocumentStage(documentFlow).execute(runContext);
      return PluginResult.success(PLUGIN_ID, MessageSource.getMessage("document.plugin.completed"));
    } catch (StageException e) {
      throw new ActionPluginException(MessageSource.getMessage("document.plugin.error.failed"), e);
    }
  }
}
