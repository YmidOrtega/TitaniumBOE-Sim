package com.boe.simulator.protocol.message;

public abstract sealed class BoeProtocolMessage
        permits SessionMessage, ApplicationMessage {

    public abstract byte[] toBytes();

    public abstract byte getMessageType();
}
