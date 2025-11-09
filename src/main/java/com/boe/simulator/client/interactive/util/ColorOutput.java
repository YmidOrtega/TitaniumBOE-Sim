package com.boe.simulator.client.interactive.util;

public class ColorOutput {

    // ANSI Color codes
    public static final String RESET = "\u001B[0m";
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    // Bright colors
    public static final String BRIGHT_BLACK = "\u001B[90m";
    public static final String BRIGHT_RED = "\u001B[91m";
    public static final String BRIGHT_GREEN = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_BLUE = "\u001B[94m";
    public static final String BRIGHT_MAGENTA = "\u001B[95m";
    public static final String BRIGHT_CYAN = "\u001B[96m";
    public static final String BRIGHT_WHITE = "\u001B[97m";

    // Background colors
    public static final String BG_BLACK = "\u001B[40m";
    public static final String BG_RED = "\u001B[41m";
    public static final String BG_GREEN = "\u001B[42m";
    public static final String BG_YELLOW = "\u001B[43m";
    public static final String BG_BLUE = "\u001B[44m";
    public static final String BG_MAGENTA = "\u001B[45m";
    public static final String BG_CYAN = "\u001B[46m";
    public static final String BG_WHITE = "\u001B[47m";

    // Text styles
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String ITALIC = "\u001B[3m";
    public static final String UNDERLINE = "\u001B[4m";
    public static final String BLINK = "\u001B[5m";
    public static final String REVERSE = "\u001B[7m";
    public static final String HIDDEN = "\u001B[8m";

    // Helper methods
    public static void println(String color, String message) {
        System.out.println(color + message + RESET);
    }

    public static void print(String color, String message) {
        System.out.print(color + message + RESET);
    }

    public static String colorize(String color, String message) {
        return color + message + RESET;
    }

    // Semantic helpers
    public static void success(String message) {
        println(GREEN, "✓ " + message);
    }

    public static void error(String message) {
        println(RED, "✗ " + message);
    }

    public static void warning(String message) {
        println(YELLOW, "⚠ " + message);
    }

    public static void info(String message) {
        println(CYAN, "ℹ " + message);
    }

    // Disable colors (for environments that don't support ANSI)
    private static boolean colorsEnabled = true;

    public static void enableColors(boolean enable) {
        colorsEnabled = enable;
    }

    public static boolean areColorsEnabled() {
        return colorsEnabled;
    }
}