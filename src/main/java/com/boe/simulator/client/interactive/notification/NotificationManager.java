package com.boe.simulator.client.interactive.notification;

import com.boe.simulator.client.interactive.util.ColorOutput;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class NotificationManager {

    private final BlockingQueue<Notification> notifications;
    private final AtomicBoolean running;
    private Thread displayThread;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public NotificationManager() {
        this.notifications = new LinkedBlockingQueue<>();
        this.running = new AtomicBoolean(false);
    }

    public void start() {
        if (running.get()) return;

        running.set(true);
        displayThread = new Thread(this::processNotifications);
        displayThread.setName("NotificationDisplay");
        displayThread.setDaemon(true);
        displayThread.start();
    }

    public void stop() {
        running.set(false);
        if (displayThread != null) displayThread.interrupt();
    }

    public void notify(NotificationType type, String message) {
        notifications.offer(new Notification(type, message, LocalTime.now()));
    }

    private void processNotifications() {
        while (running.get()) {
            try {
                Notification notification = notifications.take();
                displayNotification(notification);
            } catch (InterruptedException e) {
                if (!running.get()) {
                    break;
                }
            }
        }
    }

    private void displayNotification(Notification notification) {
        // Save cursor position, move to new line, print notification, restore cursor
        System.out.print("\r\033[K"); // Clear current line
        System.out.print("\033[s"); // Save cursor position

        String time = notification.timestamp().format(TIME_FORMATTER);
        String icon = getIcon(notification.type());
        String color = getColor(notification.type());

        System.out.println(ColorOutput.colorize(color,
                String.format("[%s] %s %s", time, icon, notification.message())));

        System.out.print("\033[u"); // Restore cursor position
        System.out.flush();
    }

    private String getIcon(NotificationType type) {
        return switch (type) {
            case ORDER_ACCEPTED -> "✓";
            case ORDER_EXECUTED -> "⚡";
            case ORDER_CANCELLED -> "✗";
            case ORDER_REJECTED -> "⚠";
            case INFO -> "ℹ";
            case WARNING -> "⚠";
            case ERROR -> "✗";
        };
    }

    private String getColor(NotificationType type) {
        return switch (type) {
            case ORDER_ACCEPTED -> ColorOutput.GREEN;
            case ORDER_EXECUTED -> ColorOutput.BRIGHT_GREEN;
            case ORDER_CANCELLED -> ColorOutput.YELLOW;
            case ORDER_REJECTED -> ColorOutput.RED;
            case INFO -> ColorOutput.CYAN;
            case WARNING -> ColorOutput.YELLOW;
            case ERROR -> ColorOutput.RED;
        };
    }

    public enum NotificationType {
        ORDER_ACCEPTED,
        ORDER_EXECUTED,
        ORDER_CANCELLED,
        ORDER_REJECTED,
        INFO,
        WARNING,
        ERROR
    }

    private record Notification(
            NotificationType type,
            String message,
            LocalTime timestamp
    ) {}
}