package com.boe.simulator.server.persistence.service;

import com.boe.simulator.protocol.message.BoeMessage;
import com.boe.simulator.protocol.message.BoeMessageFactory;
import com.boe.simulator.server.persistence.model.PersistedMessage;
import com.boe.simulator.server.persistence.repository.MessageRepository;
import com.boe.simulator.server.session.ClientSession;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageLogger {
    private static final Logger LOGGER = Logger.getLogger(MessageLogger.class.getName());

    private final MessageRepository messageRepository;
    private final boolean enabled;

    public MessageLogger(MessageRepository messageRepository, boolean enabled) {
        this.messageRepository = messageRepository;
        this.enabled = enabled;
    }

    public MessageLogger(MessageRepository messageRepository) {
        this(messageRepository, true);
    }

    public void logInbound(BoeMessage message, ClientSession session) {
        if (!enabled) return;

        try {
            byte messageType = message.getMessageType();
            String messageTypeName = BoeMessageFactory.getMessageTypeName(messageType);

            PersistedMessage persistedMessage = PersistedMessage.create(
                    PersistedMessage.MessageDirection.INBOUND,
                    messageType,
                    messageTypeName,
                    session.getConnectionId(),
                    session.getUsername(),
                    session.getSessionSubID(),
                    session.getLastReceivedSequenceNumber(),
                    message.getData()
            );

            // Add metadata
            persistedMessage = persistedMessage
                    .withMetadata("remote_address", session.getRemoteAddress())
                    .withMetadata("state", session.getState().name());

            messageRepository.save(persistedMessage);

            LOGGER.fine("Logged inbound message: " + messageTypeName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to log inbound message", e);
        }
    }

    public void logOutbound(byte[] messageData, ClientSession session) {
        if (!enabled) return;

        try {
            // Parse message type from raw data
            if (messageData.length < 5) {
                LOGGER.warning("Message too short to log");
                return;
            }

            byte messageType = messageData[4]; // MessageType is at offset 4
            String messageTypeName = BoeMessageFactory.getMessageTypeName(messageType);

            PersistedMessage persistedMessage = PersistedMessage.create(
                    PersistedMessage.MessageDirection.OUTBOUND,
                    messageType,
                    messageTypeName,
                    session.getConnectionId(),
                    session.getUsername(),
                    session.getSessionSubID(),
                    session.getCurrentSentSequenceNumber(),
                    messageData
            );

            // Add metadata
            persistedMessage = persistedMessage
                    .withMetadata("remote_address", session.getRemoteAddress())
                    .withMetadata("state", session.getState().name());

            messageRepository.save(persistedMessage);

            LOGGER.fine("Logged outbound message: " + messageTypeName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to log outbound message", e);
        }
    }

    public MessageStatistics getStatistics() {
        long totalMessages = messageRepository.count();
        long inboundCount = messageRepository.findByDirection(PersistedMessage.MessageDirection.INBOUND).size();
        long outboundCount = messageRepository.findByDirection(PersistedMessage.MessageDirection.OUTBOUND).size();

        return new MessageStatistics(totalMessages, inboundCount, outboundCount);
    }

    public record MessageStatistics(
            long totalMessages,
            long inboundMessages,
            long outboundMessages
    ) {}
}