package com.boe.simulator.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.protocol.message.*;
import com.boe.simulator.protocol.serialization.BoeMessageSerializer;
import com.boe.simulator.server.auth.AuthenticationResult;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.heartbeat.HeartbeatMonitor;
import com.boe.simulator.server.session.ClientSession;


public class ClientConnectionHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientConnectionHandler.class.getName());

    private final Socket socket;
    private final ClientSession session;
    private final BoeMessageSerializer serializer;
    private final AuthenticationService authService;
    private final HeartbeatMonitor heartbeatMonitor;

    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean running;

    public ClientConnectionHandler(Socket socket, int connectionId, ServerConfiguration config, AuthenticationService authService) {
        this.socket = socket;
        this.session = new ClientSession(connectionId, socket.getRemoteSocketAddress().toString());
        this.serializer = new BoeMessageSerializer();
        this.authService = authService;
        this.running = false;
        this.heartbeatMonitor = new HeartbeatMonitor(this, config);

        LOGGER.log(Level.INFO, "[Session {0}] Handler created for {1}", new Object[]{connectionId, session.getRemoteAddress()});
    }

    @Override
    public void run() {
        try {
            initialize();
            messageLoop();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Handler error", e);
        } finally {
            cleanup();
        }
    }

    private void initialize() throws IOException {
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        running = true;

        session.setState(SessionState.CONNECTED);

        LOGGER.log(Level.INFO, "[Session {0}] Initialized - Ready to receive messages", session.getConnectionId());
        LOGGER.log(Level.INFO, "[Session {0}] Waiting for login request...", session.getConnectionId());
    }

    private void messageLoop() {
        while (running) {
            try {
                // Read message from client
                BoeMessage message = serializer.deserialize(inputStream);
                session.incrementMessagesReceived();

                byte messageType = message.getMessageType();
                String messageTypeName = BoeMessageFactory.getMessageTypeName(messageType);

                LOGGER.log(Level.INFO, "[Session {0}] \u2190 Received {1} (length: {2} bytes)", new Object[]{session.getConnectionId(), messageTypeName, message.getLength()});

                // Process message based on type
                processMessage(message);

            } catch (SocketException e) {
                // Client disconnected
                LOGGER.log(Level.INFO, "[Session {0}] Client disconnected", session.getConnectionId());
                break;
            } catch (IOException e) {
                if (running) LOGGER.log(Level.WARNING, "[Session " + session.getConnectionId() + "] Error reading message", e);
                break;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error processing message", e);
            }
        }
    }

    private void processMessage(BoeMessage message) {
        byte messageType = message.getMessageType();

        try {
            // Create specific message object
            Object specificMessage = BoeMessageFactory.createMessage(message);

            // Dispatch to appropriate handler
            switch (specificMessage) {
                case null -> {
                    LOGGER.log(Level.WARNING, "[Session {0}] Unknown message type: 0x{1}", new Object[]{session.getConnectionId(), String.format("%02X", messageType)});
                }
                case LoginRequestMessage loginRequestMessage -> handleLoginRequest(loginRequestMessage);
                case LogoutRequestMessage logoutRequestMessage -> handleLogoutRequest(logoutRequestMessage);
                case ClientHeartbeatMessage clientHeartbeatMessage -> handleClientHeartbeat(clientHeartbeatMessage);
                default -> LOGGER.log(Level.WARNING, "[Session {0}] Unhandled message type: {1}", new Object[]{session.getConnectionId(), specificMessage.getClass().getSimpleName()});
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error processing message type 0x" +  String.format("%02X", messageType), e);
        }
    }

    private void handleLoginRequest(LoginRequestMessage request) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing login request: user=''{1}'', sessionSubID=''{2}''", new Object[]{session.getConnectionId(), request.getUsername(), request.getSessionSubID()});

        // Update session info
        session.setUsername(request.getUsername());
        session.setSessionSubID(request.getSessionSubID());
        session.setMatchingUnit(request.getMatchingUnit());
        session.updateReceivedSequenceNumber(request.getSequenceNumber());

        AuthenticationResult authResult = authService.authenticate(
                request.getUsername(),
                request.getPassword(),
                request.getSessionSubID()
        );

        // Create and send LoginResponse
        sendLoginResponse(authResult, request.getSequenceNumber());


        if (authResult.isAccepted()) {
            session.setState(SessionState.AUTHENTICATED);
            heartbeatMonitor.start();
            LOGGER.info("[Session " + session.getConnectionId() + "] User authenticated successfully");
        } else {
            session.setState(SessionState.ERROR);
            LOGGER.warning("[Session " + session.getConnectionId() + "] Authentication failed: " + authResult.getMessage());
            // Close connection after rejected login
            running = false;
        }
    }

    private void handleLogoutRequest(LogoutRequestMessage request) {
        LOGGER.info("[Session " + session.getConnectionId() + "] Processing logout request");

        session.updateReceivedSequenceNumber(request.getSequenceNumber());
        session.setState(SessionState.DISCONNECTING);

        heartbeatMonitor.stop();
        if (session.getUsername() != null) authService.endSession(session.getUsername());

        // Send LogoutResponse
        sendLogoutResponse(request.getSequenceNumber());

        // Close connection after logout
        running = false;
    }

    private void handleClientHeartbeat(ClientHeartbeatMessage heartbeat) {
        LOGGER.fine("[Session " + session.getConnectionId() + "] Client heartbeat received: seq=" + heartbeat.getSequenceNumber());

        session.updateReceivedSequenceNumber(heartbeat.getSequenceNumber());
        session.updateHeartbeatReceived();

        LOGGER.fine("[Session " + session.getConnectionId() + "] Heartbeat acknowledged");
    }

    public void sendMessage(byte[] messageBytes) throws IOException {
        synchronized (this) {
            outputStream.write(messageBytes);
            outputStream.flush();
            session.incrementMessagesSent();

            LOGGER.fine("[Session " + session.getConnectionId() + "] → Sent message (" + messageBytes.length + " bytes)");
        }
    }

    private void cleanup() {
        running = false;

        if (heartbeatMonitor != null) heartbeatMonitor.shutdown();

        LOGGER.info("[Session " + session.getConnectionId() + "] Cleaning up connection...");
        LOGGER.info("[Session " + session.getConnectionId() + "] Statistics: " +
                "Received=" + session.getMessagesReceived() + ", " +
                "Sent=" + session.getMessagesSent() + ", " +
                "Duration=" + java.time.Duration.between(session.getCreatedAt(), java.time.Instant.now()).getSeconds() + "s");

        closeQuietly(inputStream);
        closeQuietly(outputStream);
        closeQuietly(socket);

        session.setState(SessionState.DISCONNECTED);
        LOGGER.info("[Session " + session.getConnectionId() + "] Connection closed");
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void sendLoginResponse(AuthenticationResult authResult, int lastReceivedSeq) {
        try {
            LoginResponseMessage response = new LoginResponseMessage(
                    authResult.toLoginResponseStatusByte(),
                    authResult.getMessage(),
                    lastReceivedSeq,
                    1
            );

            // Set matching unit and sequence number
            response.setMatchingUnit(session.getMatchingUnit());
            response.setSequenceNumber(session.getNextSentSequenceNumber());

            byte[] responseBytes = response.toBytes();
            sendMessage(responseBytes);

            LOGGER.info("[Session " + session.getConnectionId() + "] → Sent LoginResponse: status=" + (char)authResult.toLoginResponseStatusByte() + ", msg='" + authResult.getMessage() + "'");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error sending LoginResponse", e);
        }
    }

    private void sendLogoutResponse(int lastReceivedSeq) {
        try {
            LogoutResponseMessage response = new LogoutResponseMessage(
                    LogoutResponseMessage.REASON_USER_REQUESTED,
                    "Logout successful",
                    lastReceivedSeq,
                    1
            );

            // Set matching unit and sequence number
            response.setMatchingUnit(session.getMatchingUnit());
            response.setSequenceNumber(session.getNextSentSequenceNumber());

            byte[] responseBytes = response.toBytes();
            sendMessage(responseBytes);

            LOGGER.info("[Session " + session.getConnectionId() + "] → Sent LogoutResponse");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error sending LogoutResponse", e);
        }
    }

    // Getters
    public ClientSession getSession() {
        return session;
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        running = false;
    }
}

