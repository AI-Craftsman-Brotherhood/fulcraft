package com.example.oop;

import java.util.HashMap;
import java.util.Map;

public class MemoryRepository<T extends Identifiable> {

    private final Map<String, T> entities = new HashMap<>();

    public void save(T entity) {
        if (entity == null || entity.id() == null || entity.id().isBlank()) {
            return;
        }
        entities.put(entity.id(), entity);
    }

    public T findById(String id) {
        return entities.get(id);
    }

    public int size() {
        return entities.size();
    }
}
