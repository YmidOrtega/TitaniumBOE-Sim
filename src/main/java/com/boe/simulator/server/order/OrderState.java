package com.boe.simulator.server.order;

public enum OrderState {
    PENDING_NEW("Pending New", "Order received, pending acknowledgment", false),
    LIVE("Live", "Order acknowledged and active in the book", true),
    PARTIALLY_FILLED("Partially Filled", "Order partially executed", true),
    FILLED("Filled", "Order completely executed", false),
    CANCELLED("Cancelled", "Order cancelled by user or system", false),
    REJECTED("Rejected", "Order rejected by system", false),
    EXPIRED("Expired", "Order expired (time-based)", false),
    PENDING_CANCEL("Pending Cancel", "Cancel request received, pending confirmation", true),
    PENDING_REPLACE("Pending Replace", "Replace request received, pending confirmation", true);

    private final String name;
    private final String description;
    private final boolean cancellable;

    OrderState(String name, String description, boolean cancellable) {
        this.name = name;
        this.description = description;
        this.cancellable = cancellable;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCancellable() {
        return cancellable;
    }

    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED || this == EXPIRED;
    }

    public boolean isActive() {
        return this == LIVE || this == PARTIALLY_FILLED;
    }

    @Override
    public String toString() {
        return name;
    }
}