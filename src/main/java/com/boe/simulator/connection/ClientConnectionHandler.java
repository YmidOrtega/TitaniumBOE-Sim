package com.boe.simulator.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.protocol.message.BoeMessage;
import com.boe.simulator.protocol.message.BoeMessageFactory;
import com.boe.simulator.protocol.message.ClientHeartbeatMessage;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutRequestMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;
import com.boe.simulator.protocol.message.SessionState;
import com.boe.simulator.protocol.serialization.BoeMessageSerializer;
import com.boe.simulator.server.auth.AuthenticationResult;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.heartbeat.HeartbeatMonitor;
import com.boe.simulator.server.ratelimit.RateLimiter;
import com.boe.simulator.server.session.ClientSession;
import com.boe.simulator.server.session.ClientSessionManager;
import com.boe.simulator.server.validation.MessageValidator;

public class ClientConnectionHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientConnectionHandler.class.getName());

    private final Socket socket;
    private final ClientSession session;
    private final BoeMessageSerializer serializer;
    private final AuthenticationService authService;
    private final HeartbeatMonitor heartbeatMonitor;
    private final ClientSessionManager sessionManager;
    private final ErrorHandler errorHandler;
    private final RateLimiter rateLimiter;

    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean running;

    public ClientConnectionHandler(Socket socket, int connectionId, ServerConfiguration config,
                                   AuthenticationService authService, ClientSessionManager sessionManager, ErrorHandler errorHandler, RateLimiter rateLimiter) {
        this.socket = socket;
        this.session = new ClientSession(connectionId, socket.getRemoteSocketAddress().toString());
        this.serializer = new BoeMessageSerializer();
        this.authService = authService;
        this.heartbeatMonitor = new HeartbeatMonitor(this, config);
        this.sessionManager = sessionManager;
        this.running = false;
        this.errorHandler = errorHandler;
        this.rateLimiter = rateLimiter; 
        
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
                // Read message
                BoeMessage message = serializer.deserialize(inputStream);
                session.incrementMessagesReceived();

                MessageValidator.ValidationResult validation = MessageValidator.validate(message);
                if (!validation.isValid()) {
                    LOGGER.log(Level.WARNING, "[Session {0}] Invalid message: {1}", new Object[]{session.getConnectionId(), validation.getMessage()});
                    errorHandler.handleError(session.getConnectionId(), "Message validation", new IllegalArgumentException(validation.getMessage()));
                    continue;
                }

                if (!rateLimiter.allowMessage(session.getConnectionId())) {
                    LOGGER.log(Level.WARNING, "[Session {0}] Message rejected - rate limit", session.getConnectionId());
                    continue;
                }

                byte messageType = message.getMessageType();
                String messageTypeName = BoeMessageFactory.getMessageTypeName(messageType);

                LOGGER.log(Level.INFO, "[Session {0}] ← Received {1} (length: {2} bytes)", new Object[]{session.getConnectionId(), messageTypeName, message.getLength()});

                processMessage(message);

                if (errorHandler.shouldTerminateConnection(session.getConnectionId())) {
                    LOGGER.log(Level.SEVERE, "[Session {0}] Too many errors - terminating", session.getConnectionId());
                    break;
                }

            } catch (SocketException e) {
                errorHandler.handleError(session.getConnectionId(), "Socket error", e);
                LOGGER.log(Level.INFO, "[Session {0}] Client disconnected", session.getConnectionId());
                break;
            } catch (IOException e) {
                if (running) errorHandler.handleError(session.getConnectionId(), "IO error reading message", e);
                break;
            } catch (Exception e) {
                errorHandler.handleError(session.getConnectionId(), "Error processing message", e);
                LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Unexpected error", e);
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
            sessionManager.registerUsername(this, request.getUsername());
            sessionManager.getStatistics().incrementSuccessfulLogins();
            
            LOGGER.log(Level.INFO, "[Session {0}] User authenticated successfully", session.getConnectionId());
        } else {
            session.setState(SessionState.ERROR);
            sessionManager.getStatistics().incrementFailedLogins();
            LOGGER.log(Level.WARNING, "[Session {0}] Authentication failed: {1}", new Object[]{session.getConnectionId(), authResult.getMessage()});
            running = false;
        }
    }

    private void handleLogoutRequest(LogoutRequestMessage request) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing logout request", session.getConnectionId());

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
        LOGGER.log(Level.FINE, "[Session {0}] Client heartbeat received: seq={1}", new Object[]{session.getConnectionId(), heartbeat.getSequenceNumber()});

        session.updateReceivedSequenceNumber(heartbeat.getSequenceNumber());
        session.updateHeartbeatReceived();

        LOGGER.log(Level.FINE, "[Session {0}] Heartbeat acknowledged", session.getConnectionId());
    }

    public void sendMessage(byte[] messageBytes) throws IOException {
        synchronized (this) {
            outputStream.write(messageBytes);
            outputStream.flush();
            session.incrementMessagesSent();

            LOGGER.log(Level.FINE, "[Session {0}] \u2192 Sent message ({1} bytes)", new Object[]{session.getConnectionId(), messageBytes.length});
        }
    }

    private void cleanup() {
        running = false;

        if (heartbeatMonitor != null) heartbeatMonitor.shutdown();

        errorHandler.clearConnectionStats(session.getConnectionId());
        rateLimiter.clearConnection(session.getConnectionId());

        LOGGER.log(Level.INFO, "[Session {0}] Cleaning up connection...", session.getConnectionId());
        LOGGER.log(Level.INFO, "[Session {0}] Statistics: Received={1}, Sent={2}, Duration={3}s", new Object[]{session.getConnectionId(), session.getMessagesReceived(), session.getMessagesSent(), java.time.Duration.between(session.getCreatedAt(), java.time.Instant.now()).getSeconds()});

        closeQuietly(inputStream);
        closeQuietly(outputStream);
        closeQuietly(socket);

        session.setState(SessionState.DISCONNECTED);
        LOGGER.log(Level.INFO, "[Session {0}] Connection closed", session.getConnectionId());
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

            LOGGER.log(Level.INFO, "[Session {0}] \u2192 Sent LoginResponse: status={1}, msg=''{2}''", new Object[]{session.getConnectionId(), (char)authResult.toLoginResponseStatusByte(), authResult.getMessage()});

        }catch (IOException | IllegalStateException e) {
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

            LOGGER.log(Level.INFO, "[Session {0}] \u2192 Sent LogoutResponse", session.getConnectionId());

        } catch (IOException | IllegalStateException e) {
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

