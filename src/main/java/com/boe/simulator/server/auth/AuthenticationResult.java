package com.boe.simulator.server.auth;

public class AuthenticationResult {

    public enum Status {
        ACCEPTED,
        REJECTED,
        SESSION_IN_USE
    }

    private final Status status;
    private final String message;

    private AuthenticationResult(Status status, String message) {
        this.status = status;
        this.message = message;
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

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
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
            default -> 'R';
        };
    }

    @Override
    public String toString() {
        return "AuthenticationResult{" +
                "status=" + status +
                ", message='" + message + '\'' +
                '}';
    }
}
