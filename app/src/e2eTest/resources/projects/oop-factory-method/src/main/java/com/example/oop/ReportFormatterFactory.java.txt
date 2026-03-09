package com.example.oop;

public final class ReportFormatterFactory {

    private ReportFormatterFactory() {}

    public static ReportFormatter create(String format) {
        if ("json".equalsIgnoreCase(format)) {
            return new JsonReportFormatter();
        }
        return new TextReportFormatter();
    }
}
