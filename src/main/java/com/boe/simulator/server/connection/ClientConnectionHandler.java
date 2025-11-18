package com.boe.simulator.server.connection;

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
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.heartbeat.HeartbeatMonitor;
import com.boe.simulator.server.order.OrderManager;
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
    private final OrderManager orderManager;  // NUEVO

    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean running;

    public ClientConnectionHandler(Socket socket, int connectionId, ServerConfiguration config, AuthenticationService authService, ClientSessionManager sessionManager, ErrorHandler errorHandler, RateLimiter rateLimiter, OrderManager orderManager) {
        this.socket = socket;
        this.session = new ClientSession(connectionId, socket.getRemoteSocketAddress().toString());
        this.serializer = new BoeMessageSerializer();
        this.authService = authService;
        this.heartbeatMonitor = new HeartbeatMonitor(this, config);
        this.sessionManager = sessionManager;
        this.running = false;
        this.errorHandler = errorHandler;
        this.rateLimiter = rateLimiter;
        this.orderManager = orderManager;

        LOGGER.log(Level.INFO, "[Session {0}] Handler created for {1}", new Object[]{
                session.getConnectionId(),
                socket.getRemoteSocketAddress().toString()
        });
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
                    LOGGER.log(Level.WARNING, "[Session {0}] Invalid message: {1}", new Object[]{
                            session.getConnectionId(),
                            validation.getMessage()
                    });
                    errorHandler.handleError(session.getConnectionId(), "Message validation", new IllegalArgumentException(validation.getMessage()));
                    continue;
                }

                if (!rateLimiter.allowMessage(session.getConnectionId())) {
                    LOGGER.log(Level.WARNING, "[Session {0}] Message rejected - rate limit", session.getConnectionId());
                    continue;
                }

                byte messageType = message.getMessageType();
                String messageTypeName = BoeMessageFactory.getMessageTypeName(messageType);

                LOGGER.log(Level.INFO, "[Session {0}] ← Received {1} (length: {2} bytes)", new Object[]{
                        session.getConnectionId(),
                        messageTypeName,
                        message.getLength()
                });

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
                    LOGGER.log(Level.WARNING, "[Session {0}] Unknown message type: 0x{1}", new Object[]{
                            session.getConnectionId(), String.format("%02X", messageType)
                    });
                }
                case LoginRequestMessage loginRequestMessage -> handleLoginRequest(loginRequestMessage);
                case LogoutRequestMessage logoutRequestMessage -> handleLogoutRequest(logoutRequestMessage);
                case ClientHeartbeatMessage clientHeartbeatMessage -> handleClientHeartbeat(clientHeartbeatMessage);

                case NewOrderMessage newOrderMessage -> handleNewOrder(newOrderMessage);
                case CancelOrderMessage cancelOrderMessage -> handleCancelOrder(cancelOrderMessage);

                default -> LOGGER.log(Level.WARNING, "[Session {0}] Unhandled message type: {1}", new Object[]{
                        session.getConnectionId(),
                        specificMessage.getClass().getSimpleName()
                });
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error processing message type 0x" + String.format("%02X", messageType), e);
        }
    }

    private void handleLoginRequest(LoginRequestMessage request) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing login request: user=''{1}'', sessionSubID=''{2}''", new Object[]{
                session.getConnectionId(),
                request.getUsername(),
                request.getSessionSubID()
        });

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

            LOGGER.log(Level.INFO, "[Session {0}] User authenticated successfully",
                    session.getConnectionId());
        } else {
            session.setState(SessionState.ERROR);
            sessionManager.getStatistics().incrementFailedLogins();
            LOGGER.log(Level.WARNING, "[Session {0}] Authentication failed: {1}", new Object[]{
                    session.getConnectionId(),
                    authResult.getMessage()
            });
            
            // Close connection gracefully after failed authentication
            try {
                Thread.sleep(100); // Give time for LoginResponse to be sent
                running = false;
                socket.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Error closing socket after failed authentication", e);
            }
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
        LOGGER.log(Level.FINE, "[Session {0}] Client heartbeat received: seq={1}",
                new Object[]{session.getConnectionId(), heartbeat.getSequenceNumber()});

        session.updateReceivedSequenceNumber(heartbeat.getSequenceNumber());
        session.updateHeartbeatReceived();

        LOGGER.log(Level.FINE, "[Session {0}] Heartbeat acknowledged", session.getConnectionId());
    }

    private void handleNewOrder(NewOrderMessage newOrder) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing NewOrder: ClOrdID={1}, Symbol={2}, Side={3}, Qty={4}", new Object[]{
                session.getConnectionId(),
                newOrder.getClOrdID(),
                newOrder.getSymbol(),
                newOrder.getSide() == 1 ? "Buy" : "Sell",
                newOrder.getOrderQty()
        });


        if (!session.isAuthenticated()) {
            LOGGER.log(Level.WARNING, "[Session {0}] NewOrder rejected - not authenticated", session.getConnectionId());
            sendOrderRejected(
                    newOrder.getClOrdID(),
                    OrderRejectedMessage.REASON_SESSION_NOT_AUTHENTICATED,
                    "Session not authenticated"
            );
            return;
        }

        session.updateReceivedSequenceNumber(newOrder.getSequenceNumber());

        OrderManager.OrderResponse response = orderManager.processNewOrder(newOrder, session);

        if (response.isAcknowledged()) sendOrderAcknowledgment(response.getOrder());
        else {
            sendOrderRejected(
                    response.getClOrdID(),
                    response.getRejectReason(),
                    response.getRejectText()
            );
        }
    }

    private void handleCancelOrder(CancelOrderMessage cancelOrder) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing CancelOrder: {1}", new Object[]{
                session.getConnectionId(),
        });

        if (!session.isAuthenticated()) {
            LOGGER.log(Level.WARNING, "[Session {0}] CancelOrder rejected - not authenticated", session.getConnectionId());
            return;
        }

        session.updateReceivedSequenceNumber(cancelOrder.getSequenceNumber());

        OrderManager.CancelResponse response = orderManager.processCancelOrder(cancelOrder, session);

        if (response.isCancelled()) sendOrderCancelled(response.getOrder(), response.getCancelReason());
        else if (response.isMassCancelled()) sendMassCancelAcknowledgment(response.getMassCancelCount(), response.getMassCancelId());
        else LOGGER.log(Level.WARNING, "[Session {0}] Cancel rejected: {1}", new Object[]{
                    session.getConnectionId(),
                    response.getRejectText()
            });

    }

    public void sendMessage(byte[] messageBytes) throws IOException {
        synchronized (this) {
            outputStream.write(messageBytes);
            outputStream.flush();
            session.incrementMessagesSent();

            LOGGER.log(Level.FINE, "[Session {0}] → Sent message ({1} bytes)", new Object[]{
                    session.getConnectionId(),
                    messageBytes.length
            });
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

            response.setMatchingUnit(session.getMatchingUnit());
            response.setSequenceNumber(session.getNextSentSequenceNumber());

            byte[] responseBytes = response.toBytes();
            sendMessage(responseBytes);

            LOGGER.log(Level.INFO, "[Session {0}] → Sent LoginResponse: status={1}, msg=''{2}''", new Object[]{
                    session.getConnectionId(),
                    (char)authResult.toLoginResponseStatusByte(),
                    authResult.getMessage()
            });

        } catch (IOException | IllegalStateException e) {
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

            response.setMatchingUnit(session.getMatchingUnit());
            response.setSequenceNumber(session.getNextSentSequenceNumber());

            byte[] responseBytes = response.toBytes();
            sendMessage(responseBytes);

            LOGGER.log(Level.INFO, "[Session {0}] → Sent LogoutResponse", session.getConnectionId());

        } catch (IOException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error sending LogoutResponse", e);
        }
    }

    private void sendOrderAcknowledgment(com.boe.simulator.server.order.Order order) {
        try {
            OrderAcknowledgmentMessage ack = OrderAcknowledgmentMessage.fromOrder(
                    order,
                    session.getMatchingUnit(),
                    session.getNextSentSequenceNumber()
            );

            byte[] ackBytes = ack.toBytes();
            sendMessage(ackBytes);

            LOGGER.log(Level.INFO, "[Session {0}] → Sent OrderAcknowledgment: ClOrdID={1}, OrderID={2}", new Object[]{
                    session.getConnectionId(),
                    order.getClOrdID(),
                    order.getOrderID()
            });

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error sending OrderAcknowledgment", e);
        }
    }

    private void sendOrderRejected(String clOrdID, byte reason, String text) {
        try {
            OrderRejectedMessage rejected = new OrderRejectedMessage(clOrdID, reason, text);
            rejected.setMatchingUnit(session.getMatchingUnit());
            rejected.setSequenceNumber(session.getNextSentSequenceNumber());

            byte[] rejectedBytes = rejected.toBytes();
            sendMessage(rejectedBytes);

            LOGGER.log(Level.INFO, "[Session {0}] → Sent OrderRejected: ClOrdID={1}, Reason={2}", new Object[]{
                    session.getConnectionId(),
                    clOrdID,
                    (char)reason
            });

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error sending OrderRejected", e);
        }
    }

    private void sendOrderCancelled(com.boe.simulator.server.order.Order order, byte reason) {
        try {
            OrderCancelledMessage cancelled = OrderCancelledMessage.fromOrder(order, reason);
            cancelled.setMatchingUnit(session.getMatchingUnit());
            cancelled.setSequenceNumber(session.getNextSentSequenceNumber());

            byte[] cancelledBytes = cancelled.toBytes();
            sendMessage(cancelledBytes);

            LOGGER.log(Level.INFO, "[Session {0}] → Sent OrderCancelled: ClOrdID={1}", new Object[]{
                    session.getConnectionId(),
                    order.getClOrdID()
            });

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error sending OrderCancelled", e);
        }
    }

    private void sendMassCancelAcknowledgment(int count, String massCancelId) {
        LOGGER.log(Level.INFO, "[Session {0}] → Mass Cancel completed: {1} orders, ID={2}", new Object[]{
                session.getConnectionId(),
                count,
                massCancelId
        });

    }

    private void cleanup() {
        running = false;

        if (heartbeatMonitor != null) heartbeatMonitor.shutdown();

        errorHandler.clearConnectionStats(session.getConnectionId());
        rateLimiter.clearConnection(session.getConnectionId());

        LOGGER.log(Level.INFO, "[Session {0}] Cleaning up connection...", session.getConnectionId());
        LOGGER.log(Level.INFO, "[Session {0}] Statistics: Received={1}, Sent={2}, Duration={3}s", new Object[]{
                session.getConnectionId(),
                session.getMessagesReceived(),
                session.getMessagesSent(),
                java.time.Duration.between(
                        session.getCreatedAt(),
                        java.time.Instant.now()).getSeconds()
        });

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
            } catch (Exception ignored) {
            }
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