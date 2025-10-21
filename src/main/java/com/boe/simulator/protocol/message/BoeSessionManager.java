package com.boe.simulator.protocol.message;

public class BoeSessionManager extends Message {
    private String sessionSubID;
    private String username;
    private String password;

    public BoeSessionManager(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }


}
