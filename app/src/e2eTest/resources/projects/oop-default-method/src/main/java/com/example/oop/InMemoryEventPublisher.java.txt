package com.example.oop;

import java.util.ArrayList;
import java.util.List;

public class InMemoryEventPublisher implements EventPublisher {

    private final List<String> events = new ArrayList<>();

    @Override
    public void publish(String eventText) {
        events.add(eventText);
    }

    public List<String> events() {
        return List.copyOf(events);
    }
}
