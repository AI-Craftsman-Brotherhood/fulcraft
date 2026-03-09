package com.example.oop;

public interface EventPublisher {

    void publish(String eventText);

    default void publishUserEvent(String userId, String action) {
        if (userId == null || userId.isBlank()) {
            publish("INVALID_USER");
            return;
        }
        publish("[user=" + userId + "] action=" + action);
    }
}
