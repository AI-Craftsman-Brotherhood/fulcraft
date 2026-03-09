package com.example.basics;

public class LoopService {

    public int sum(int[] values) {
        if (values == null) {
            return 0;
        }
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    public int countPositiveUntilZero(int[] values) {
        if (values == null) {
            return 0;
        }
        int index = 0;
        int count = 0;
        while (index < values.length) {
            int value = values[index];
            index++;
            if (value == 0) {
                break;
            }
            if (value > 0) {
                count++;
            }
        }
        return count;
    }

    public int countdownSteps(int start) {
        int current = start;
        int steps = 0;
        do {
            current--;
            steps++;
        } while (current > 0);
        return steps;
    }
}
