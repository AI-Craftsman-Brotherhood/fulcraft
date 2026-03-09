package com.example.oop;

public class Product implements Identifiable {

    private final String id;
    private final String name;

    public Product(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String id() {
        return id;
    }

    public String name() {
        return name;
    }
}
