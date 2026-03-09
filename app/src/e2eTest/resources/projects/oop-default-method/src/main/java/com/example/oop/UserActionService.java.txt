package com.example.oop;

public class UserActionService {

    private final EventPublisher publisher;

    public UserActionService(EventPublisher publisher) {
        this.publisher = publisher;
    }

    public void login(String userId) {
        publisher.publishUserEvent(userId, "login");
    }
}
