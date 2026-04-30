package br.com.finalcraft.evernifecore.math.vecmath;

public final class VecMath {

    public static int floor_double(double value){
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }

    public static int parseIntFast(String s, int start, int end) {
        if (start >= end) {
            throw new NumberFormatException("Empty number in: " + s);
        }

        int sign = 1;
        int i = start;

        char first = s.charAt(i);
        if (first == '-') {
            sign = -1;
            i++;
        }

        int result = 0;

        for (; i < end; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException("Invalid character '" + c + "' in: " + s);
            }
            result = result * 10 + (c - '0');
        }

        return result * sign;
    }
}
