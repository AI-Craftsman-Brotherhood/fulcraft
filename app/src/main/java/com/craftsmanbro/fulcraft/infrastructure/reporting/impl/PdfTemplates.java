package com.craftsmanbro.fulcraft.infrastructure.reporting.impl;

import com.craftsmanbro.fulcraft.infrastructure.reporting.contract.PdfTemplatePort;

/** Shared HTML templates for PDF-ready report output. */
public final class PdfTemplates implements PdfTemplatePort {

  // Stateless singleton exposed both as convenience methods and as PdfTemplatePort.
  private static final PdfTemplates INSTANCE = new PdfTemplates();

  private PdfTemplates() {}

  private static final String PDF_READY_HTML_TEMPLATE =
      """
      <!DOCTYPE html>
      <html lang="{{LANG}}">
      <head>
        <meta charset="UTF-8">
        <title>{{PAGE_TITLE}}</title>
        <style>
          @page {
            size: A4;
            margin: 2cm;
          }
          @media print {
            body { print-color-adjust: exact; -webkit-print-color-adjust: exact; }
          }
          * { box-sizing: border-box; margin: 0; padding: 0; }
          body {
            font-family: 'Noto Sans JP', 'Yu Gothic', sans-serif;
            font-size: 10pt;
            line-height: 1.6;
            color: #333;
            background: white;
            padding: 1cm;
          }
          h1 {
            font-size: 20pt;
            color: #1a1a2e;
            border-bottom: 3px solid #e94560;
            padding-bottom: 0.5rem;
            margin-bottom: 1rem;
          }
          h2 {
            font-size: 14pt;
            color: #0f3460;
            margin: 1.5rem 0 0.75rem;
            border-bottom: 1px solid #ddd;
            padding-bottom: 0.25rem;
          }
          h3 {
            font-size: 12pt;
            color: #16213e;
            margin: 1rem 0 0.5rem;
          }
          table {
            width: 100%;
            border-collapse: collapse;
            margin: 0.75rem 0;
            font-size: 9pt;
          }
          th, td {
            padding: 0.5rem;
            text-align: left;
            border: 1px solid #ddd;
          }
          th {
            background: #f5f5f5;
            font-weight: bold;
          }
          code {
            font-family: 'Consolas', monospace;
            background: #f5f5f5;
            padding: 0.1rem 0.3rem;
            border-radius: 3px;
            font-size: 9pt;
          }
          pre {
            background: #f8f8f8;
            border: 1px solid #ddd;
            padding: 0.75rem;
            overflow-x: auto;
            margin: 0.75rem 0;
            font-size: 9pt;
          }
          pre code {
            background: transparent;
            padding: 0;
          }
          .warning {
            background: #fff8e1;
            border-left: 3px solid #ffa000;
            padding: 0.5rem;
            margin: 0.5rem 0;
          }
          .critical {
            background: #ffebee;
            border-left: 3px solid #e91e63;
            padding: 0.5rem;
            margin: 0.5rem 0;
          }
          ul { padding-left: 1.5rem; margin: 0.5rem 0; }
          li { margin: 0.25rem 0; }
          p { margin: 0.5rem 0; }
          .page-break { page-break-after: always; }
          .header {
            text-align: right;
            color: #888;
            font-size: 8pt;
            margin-bottom: 1rem;
          }
          .footer {
            margin-top: 1.5rem;
            padding-top: 0.5rem;
            border-top: 1px solid #ddd;
            color: #888;
            font-size: 8pt;
            text-align: center;
          }
        </style>
      </head>
      <body>
        <div class="header">
          <p>{{HEADER_TEXT}}</p>
        </div>
        {{CONTENT}}
        <div class="footer">
          <p>{{FOOTER_TEXT}}</p>
        </div>
      </body>
      </html>
      """;

  public static String pdfReadyHtmlTemplate() {
    return INSTANCE.template();
  }

  public static PdfTemplatePort port() {
    return INSTANCE;
  }

  @Override
  public String template() {
    return PDF_READY_HTML_TEMPLATE;
  }
}
