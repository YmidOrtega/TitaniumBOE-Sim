package com.boe.simulator.server.auth;

public record AuthenticationResult(Status status, String message) {

    public enum Status {
        ACCEPTED,
        REJECTED,
        SESSION_IN_USE
    }

    public static AuthenticationResult accepted(String message) {
        return new AuthenticationResult(Status.ACCEPTED, message);
    }

    public static AuthenticationResult rejected(String message) {
        return new AuthenticationResult(Status.REJECTED, message);
    }

    public static AuthenticationResult sessionInUse(String message) {
        return new AuthenticationResult(Status.SESSION_IN_USE, message);
    }

    public boolean isAccepted() {
        return status == Status.ACCEPTED;
    }

    public boolean isRejected() {
        return status == Status.REJECTED;
    }

    public boolean isSessionInUse() {
        return status == Status.SESSION_IN_USE;
    }

    public byte toLoginResponseStatusByte() {
        return switch (status) {
            case ACCEPTED -> 'A';
            case REJECTED -> 'R';
            case SESSION_IN_USE -> 'S';
        };
    }
}
