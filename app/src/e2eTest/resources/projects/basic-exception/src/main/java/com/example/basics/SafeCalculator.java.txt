package com.example.basics;

public class SafeCalculator {

    public int divide(int dividend, int divisor) {
        if (divisor == 0) {
            throw new IllegalArgumentException("divisor must not be zero");
        }
        return dividend / divisor;
    }

    public int parseAndDivide(String left, String right) {
        try {
            int dividend = Integer.parseInt(left);
            int divisor = Integer.parseInt(right);
            return divide(dividend, divisor);
        } catch (NumberFormatException exception) {
            return 0;
        } catch (IllegalArgumentException exception) {
            return -1;
        } finally {
            // no-op: this branch exists to exercise finally in fixture analysis.
        }
    }
}
