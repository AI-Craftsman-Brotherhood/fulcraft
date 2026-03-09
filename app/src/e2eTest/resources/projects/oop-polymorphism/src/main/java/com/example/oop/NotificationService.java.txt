package com.example.oop;

public class NotificationService {

    private NotificationChannel channel;

    public NotificationService(NotificationChannel channel) {
        this.channel = channel;
    }

    public void switchChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public String sendWelcome(String userId) {
        if (userId == null || userId.isBlank()) {
            return "INVALID_USER";
        }
        return channel.deliver(userId, "welcome");
    }
}
