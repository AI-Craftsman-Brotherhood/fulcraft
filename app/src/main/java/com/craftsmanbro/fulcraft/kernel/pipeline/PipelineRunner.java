package com.craftsmanbro.fulcraft.kernel.pipeline;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.logging.LoggerPort;
import com.craftsmanbro.fulcraft.logging.LoggerPortProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

/**
 * パイプライン実行用のファサードクラス。
 *
 * <p>設定や依存関係を元に {@link Pipeline} を構築し、実行を委譲します。 CLIコマンドやTUIから利用されることを想定しています。
 */
public record PipelineRunner(Pipeline pipeline) {

  private static final LoggerPort LOG = LoggerPortProvider.getLogger(PipelineRunner.class);

  public PipelineRunner {
    pipeline = Objects.requireNonNull(pipeline, msg("kernel.pipeline_runner.error.pipeline_null"));
  }

  /**
   * パイプラインイベントリスナーを追加します。
   *
   * @param listener 追加するリスナー
   * @return このランナーインスタンス（メソッドチェーン用）
   */
  public PipelineRunner addListener(final Pipeline.PipelineListener listener) {
    pipeline.addListener(listener);
    return this;
  }

  /**
   * パイプラインを実行します。
   *
   * @param context 実行コンテキスト
   * @return 終了コード (0: 成功, 非0: 失敗)
   */
  public int run(final RunContext context) {
    return run(context, null, null, null);
  }

  /**
   * 特定ノードを指定してパイプラインを実行します。
   *
   * @param context 実行コンテキスト
   * @param nodeIds 実行する特定ノードリスト (nullの場合は全ノード)
   * @param fromNodeId 開始ノード (これ以降を実行)
   * @param toNodeId 終了ノード (これ以前を実行)
   * @return 終了コード (0: 成功, 非0: 失敗)
   */
  public int run(
      final RunContext context,
      final List<String> nodeIds,
      final String fromNodeId,
      final String toNodeId) {
    prepareRun(context);
    return pipeline.run(context, nodeIds, fromNodeId, toNodeId);
  }

  /**
   * Run pipeline by node ids (DAG-native API).
   *
   * @param context run context
   * @param nodeIds specific node ids to run (null/empty = range or all)
   * @param fromNodeId start node id (inclusive)
   * @param toNodeId end node id (inclusive)
   * @return exit code (0 success / non-zero failure)
   */
  public int runNodes(
      final RunContext context,
      final List<String> nodeIds,
      final String fromNodeId,
      final String toNodeId) {
    prepareRun(context);
    return pipeline.runNodes(context, nodeIds, fromNodeId, toNodeId);
  }

  public Pipeline getPipeline() {
    return pipeline;
  }

  private void prepareRun(final RunContext context) {
    if (context == null) {
      return;
    }
    try {
      Files.createDirectories(context.getRunDirectory());
    } catch (IOException e) {
      LOG.warn(msg("kernel.pipeline_runner.warn.run_root_init_failed"), e.getMessage());
    }
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
