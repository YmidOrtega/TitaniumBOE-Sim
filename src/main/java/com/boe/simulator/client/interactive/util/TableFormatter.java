package com.boe.simulator.client.interactive.util;

import java.util.ArrayList;
import java.util.List;

public class TableFormatter {

    private final List<String> headers;
    private final List<List<String>> rows;
    private final List<Integer> columnWidths;

    public TableFormatter(String... headers) {
        this.headers = List.of(headers);
        this.rows = new ArrayList<>();
        this.columnWidths = new ArrayList<>();

        // Initialize column widths with header lengths
        for (String header : headers) {
            columnWidths.add(header.length());
        }
    }

    public void addRow(String... values) {
        if (values.length != headers.size()) throw new IllegalArgumentException("Row must have " + headers.size() + " columns");

        List<String> row = List.of(values);
        rows.add(row);

        // Update column widths
        for (int i = 0; i < values.length; i++) {
            int currentWidth = columnWidths.get(i);
            int valueWidth = values[i].length();
            if (valueWidth > currentWidth) {
                columnWidths.set(i, valueWidth);
            }
        }
    }

    public void print() {
        printSeparator(true);
        printRow(headers, true);
        printSeparator(false);

        for (List<String> row : rows) {
            printRow(row, false);
        }

        printSeparator(true);
    }

    private void printSeparator(boolean isOuter) {
        System.out.print(isOuter ? "╔" : "╠");

        for (int i = 0; i < columnWidths.size(); i++) {
            System.out.print("═".repeat(columnWidths.get(i) + 2));

            if (i < columnWidths.size() - 1) System.out.print(isOuter ? "╦" : "╬");
        }

        System.out.println(isOuter ? "╗" : "╣");
    }

    private void printRow(List<String> values, boolean isHeader) {
        System.out.print("║");

        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            int width = columnWidths.get(i);

            System.out.printf(" %-" + width + "s ", value);
            System.out.print("║");
        }

        System.out.println();
    }
}