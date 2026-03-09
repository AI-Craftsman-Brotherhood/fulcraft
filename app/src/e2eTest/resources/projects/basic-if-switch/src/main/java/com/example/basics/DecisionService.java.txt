package com.example.basics;

public class DecisionService {

    public String gradeLabel(int score) {
        if (score < 0 || score > 100) {
            return "INVALID";
        }
        if (score >= 80) {
            return "A";
        }
        if (score >= 60) {
            return "B";
        }
        return "C";
    }

    public String shippingZone(String prefecture) {
        if (prefecture == null || prefecture.isBlank()) {
            return "UNKNOWN";
        }
        return switch (prefecture) {
            case "Tokyo", "Kanagawa", "Chiba", "Saitama" -> "KANTO";
            case "Osaka", "Kyoto", "Hyogo" -> "KANSAI";
            default -> "OTHER";
        };
    }
}
