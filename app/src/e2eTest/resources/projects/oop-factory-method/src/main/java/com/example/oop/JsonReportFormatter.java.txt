package com.example.oop;

public class JsonReportFormatter implements ReportFormatter {

    @Override
    public String format(String title, int value) {
        return "{\"title\":\"" + title + "\",\"value\":" + value + "}";
    }
}
