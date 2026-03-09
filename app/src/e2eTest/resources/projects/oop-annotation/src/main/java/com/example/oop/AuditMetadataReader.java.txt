package com.example.oop;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AuditMetadataReader {

    public List<String> auditedActions(Class<?> type) {
        List<String> actions = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            AuditedAction annotation = method.getAnnotation(AuditedAction.class);
            if (annotation != null) {
                actions.add(annotation.value());
            }
        }
        return actions;
    }
}
