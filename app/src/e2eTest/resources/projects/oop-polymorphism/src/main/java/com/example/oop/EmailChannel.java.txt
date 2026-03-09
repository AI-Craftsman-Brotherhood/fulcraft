package com.example.oop;

public class EmailChannel implements NotificationChannel {

    @Override
    public String deliver(String userId, String message) {
        return "EMAIL:" + userId + ":" + message;
    }
}
