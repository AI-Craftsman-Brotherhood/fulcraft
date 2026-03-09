package com.example.oop;

import java.util.ArrayList;
import java.util.List;

public class Schedule {

    private final List<Slot> slots = new ArrayList<>();

    public void addSlot(String title, int minutes) {
        slots.add(new Slot(title, minutes));
    }

    public int totalMinutes() {
        int total = 0;
        for (Slot slot : slots) {
            total += slot.minutes();
        }
        return total;
    }

    public List<String> titles() {
        List<String> titles = new ArrayList<>();
        for (Slot slot : slots) {
            titles.add(slot.title());
        }
        return titles;
    }

    public static final class Slot {

        private final String title;
        private final int minutes;

        public Slot(String title, int minutes) {
            this.title = title == null ? "" : title;
            this.minutes = Math.max(0, minutes);
        }

        public String title() {
            return title;
        }

        public int minutes() {
            return minutes;
        }
    }
}
