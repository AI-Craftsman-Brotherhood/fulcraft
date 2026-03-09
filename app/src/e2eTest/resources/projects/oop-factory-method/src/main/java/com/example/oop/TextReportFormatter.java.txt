package com.example.oop;

public class TextReportFormatter implements ReportFormatter {

    @Override
    public String format(String title, int value) {
        return title + ": " + value;
    }
}
