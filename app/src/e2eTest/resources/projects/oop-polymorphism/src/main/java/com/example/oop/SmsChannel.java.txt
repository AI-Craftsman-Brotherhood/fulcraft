package com.example.oop;

public class SmsChannel implements NotificationChannel {

    @Override
    public String deliver(String userId, String message) {
        return "SMS:" + userId + ":" + message;
    }
}
