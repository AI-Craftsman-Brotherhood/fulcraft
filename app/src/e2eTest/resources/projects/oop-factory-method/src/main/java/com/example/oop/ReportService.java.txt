package com.example.oop;

public class ReportService {

    public String createMonthlyReport(String format, int totalSales) {
        ReportFormatter formatter = ReportFormatterFactory.create(format);
        return formatter.format("monthly-sales", totalSales);
    }
}
