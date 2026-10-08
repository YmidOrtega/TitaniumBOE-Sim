package com.boe.simulator.server.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;
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
    private volatile boolean readerDone;
    private final ReentrantLock sendLock = new ReentrantLock();
    private final LinkedBlockingQueue<BoeMessage> inbound = new LinkedBlockingQueue<>();
    private final UnacknowledgedMessageGate gate;

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
        this.gate = new UnacknowledgedMessageGate(config.getMaxUnacknowledgedMessages(), config.getResumeReadingBelow());

        LOGGER.log(Level.INFO, "[Session {0}] Handler created for {1}", new Object[]{
                session.getConnectionId(),
                socket.getRemoteSocketAddress().toString()
        });
    }

    @Override
    public void run() {
        Thread processor = null;
        try {
            initialize();
            processor = Thread.ofVirtual()
                    .name("boe-processor-" + session.getConnectionId())
                    .start(this::processLoop);
            readLoop();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Handler error", e);
        } finally {
            readerDone = true;
            joinQuietly(processor);
            running = false;
            gate.close();
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

    private void readLoop() {
        while (running) {
            try {
                gate.awaitReadable();
                if (!running) break;

                BoeMessage message = serializer.deserialize(inputStream);
                session.incrementMessagesReceived();

                if (gate.onRead()) {
                    LOGGER.log(Level.WARNING, "[Session {0}] {1} unacknowledged messages - pausing socket reads",
                            new Object[]{session.getConnectionId(), gate.unacknowledged()});
                }
                inbound.add(message);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (SocketException e) {
                if (running) {
                    errorHandler.handleError(session.getConnectionId(), "Socket error", e);
                    LOGGER.log(Level.INFO, "[Session {0}] Client disconnected", session.getConnectionId());
                }
                break;
            } catch (IOException e) {
                if (running) errorHandler.handleError(session.getConnectionId(), "IO error reading message", e);

                break;
            }
        }
    }

    private void processLoop() {
        while (running) {
            BoeMessage message;
            try {
                message = inbound.poll(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (message == null) {
                if (readerDone) break;
                continue;
            }

            try {
                handleInbound(message);
            } catch (Exception e) {
                errorHandler.handleError(session.getConnectionId(), "Error processing message", e);
                LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Unexpected error", e);
            } finally {
                if (gate.onAcknowledged()) {
                    LOGGER.log(Level.INFO, "[Session {0}] {1} unacknowledged messages - resuming socket reads",
                            new Object[]{session.getConnectionId(), gate.unacknowledged()});
                }
            }

            if (errorHandler.shouldTerminateConnection(session.getConnectionId())) {
                LOGGER.log(Level.SEVERE, "[Session {0}] Too many errors - terminating", session.getConnectionId());
                break;
            }
        }

        running = false;
        gate.close();
        shutdownInputQuietly();
    }

    private void handleInbound(BoeMessage message) {
        MessageValidator.ValidationResult validation = MessageValidator.validate(message);
        if (!validation.isValid()) {
            LOGGER.log(Level.WARNING, "[Session {0}] Invalid message: {1}", new Object[]{
                    session.getConnectionId(),
                    validation.getMessage()
            });
            errorHandler.handleError(session.getConnectionId(), "Message validation", new IllegalArgumentException(validation.getMessage()));
            return;
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "[Session {0}] ← Received {1} (length: {2} bytes)", new Object[]{
                    session.getConnectionId(),
                    BoeMessageFactory.getMessageTypeName(message.getMessageType()),
                    message.getLength()
            });
        }

        processMessage(message);
    }

    private void processMessage(BoeMessage message) {
        byte messageType = message.getMessageType();

        try {
            // Create specific message object
            BoeProtocolMessage specificMessage = BoeMessageFactory.createMessage(message);

            switch (specificMessage) {
                case null -> {
                    LOGGER.log(Level.WARNING, "[Session {0}] Unknown message type: 0x{1}", new Object[]{
                            session.getConnectionId(), String.format("%02X", messageType)
                    });
                }
                case SessionMessage sessionMessage -> handleSessionMessage(sessionMessage);
                case ApplicationMessage applicationMessage -> {
                    rateLimiter.acquire(session.getConnectionId());
                    handleApplicationMessage(applicationMessage);
                }
                default -> LOGGER.log(Level.WARNING, "[Session {0}] Unhandled message type: {1}", new Object[]{
                        session.getConnectionId(),
                        specificMessage.getClass().getSimpleName()
                });
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error processing message type 0x" + String.format("%02X", messageType), e);
        }
    }

    private void handleSessionMessage(SessionMessage message) {
        switch (message) {
            case LoginRequestMessage loginRequestMessage -> handleLoginRequest(loginRequestMessage);
            case LogoutRequestMessage logoutRequestMessage -> handleLogoutRequest(logoutRequestMessage);
            case ClientHeartbeatMessage clientHeartbeatMessage -> handleClientHeartbeat(clientHeartbeatMessage);
            default -> LOGGER.log(Level.WARNING, "[Session {0}] Unsupported inbound session message: {1}", new Object[]{
                    session.getConnectionId(),
                    message.getClass().getSimpleName()
            });
        }
    }

    private void handleApplicationMessage(ApplicationMessage message) {
        switch (message) {
            case NewOrderMessage newOrderMessage       -> handleNewOrder(newOrderMessage);
            case CancelOrderMessage cancelOrderMessage -> handleCancelOrder(cancelOrderMessage);
            case ModifyOrderMessage modifyOrderMessage -> handleModifyOrder(modifyOrderMessage);
            default -> LOGGER.log(Level.WARNING, "[Session {0}] Unsupported inbound application message: {1}", new Object[]{
                    session.getConnectionId(),
                    message.getClass().getSimpleName()
            });
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
        session.setReturnBitfields(request.getReturnBitfields());
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
                    authResult.message()
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
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "[Session {0}] Processing NewOrder: ClOrdID={1}, Symbol={2}, Side={3}, Qty={4}", new Object[]{
                    session.getConnectionId(),
                    newOrder.getClOrdID(),
                    newOrder.getSymbol(),
                    newOrder.getSide() == 1 ? "Buy" : "Sell",
                    newOrder.getOrderQty()
            });
        }


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

    private void handleModifyOrder(ModifyOrderMessage modifyOrder) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing ModifyOrder: origClOrdID={1}, newClOrdID={2}",
                new Object[]{session.getConnectionId(),
                        modifyOrder.getOrigClOrdID(), modifyOrder.getClOrdID()});

        if (!session.isAuthenticated()) {
            LOGGER.log(Level.WARNING, "[Session {0}] ModifyOrder rejected - not authenticated",
                    session.getConnectionId());
            sendUserModifyRejected(modifyOrder.getClOrdID(),
                    UserModifyRejectedMessage.REASON_UNKNOWN, "Session not authenticated");
            return;
        }

        session.updateReceivedSequenceNumber(modifyOrder.getSequenceNumber());

        OrderManager.ModifyResponse response = orderManager.processModifyOrder(modifyOrder, session);

        if (response.isModified()) {
            sendOrderModified(response.getOrder());
        } else if (response.isAutoCancelled()) {
            sendOrderCancelled(response.getOrder(), OrderCancelledMessage.REASON_USER_REQUESTED);
        } else {
            sendUserModifyRejected(response.getClOrdID(),
                    response.getRejectReason(), response.getRejectText());
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

    public void sendSequenced(IntFunction<byte[]> encoder) throws IOException {
        sendLock.lock();
        try {
            sendMessage(encoder.apply(session.getNextSentSequenceNumber()));
        } finally {
            sendLock.unlock();
        }
    }

    public void sendMessage(byte[] messageBytes) throws IOException {
        sendLock.lock();
        try {
            outputStream.write(messageBytes);
            outputStream.flush();
            session.incrementMessagesSent();

            LOGGER.log(Level.FINE, "[Session {0}] → Sent message ({1} bytes)", new Object[]{
                    session.getConnectionId(),
                    messageBytes.length
            });
        } finally {
            sendLock.unlock();
        }
    }

    private void sendLoginResponse(AuthenticationResult authResult, int lastReceivedSeq) {
        try {
            LoginResponseMessage response = new LoginResponseMessage(
                    authResult.toLoginResponseStatusByte(),
                    authResult.message(),
                    lastReceivedSeq,
                    1
            );

            response.setMatchingUnit(session.getMatchingUnit());
            sendSequenced(seq -> {
                response.setSequenceNumber(seq);
                return response.toBytes();
            });

            LOGGER.log(Level.INFO, "[Session {0}] → Sent LoginResponse: status={1}, msg=''{2}''", new Object[]{
                    session.getConnectionId(),
                    (char)authResult.toLoginResponseStatusByte(),
                    authResult.message()
            });

            if (authResult.isAccepted()) {
                ReplayCompleteMessage replayComplete = new ReplayCompleteMessage(session.getMatchingUnit(), 0);
                sendMessage(replayComplete.toBytes());
                LOGGER.log(Level.INFO, "[Session {0}] → Sent ReplayComplete", session.getConnectionId());
            }

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
            sendSequenced(seq -> {
                response.setSequenceNumber(seq);
                return response.toBytes();
            });

            LOGGER.log(Level.INFO, "[Session {0}] → Sent LogoutResponse", session.getConnectionId());

        } catch (IOException | IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error sending LogoutResponse", e);
        }
    }

    private void sendOrderAcknowledgment(com.boe.simulator.server.order.Order order) {
        try {
            sendSequenced(seq -> OrderAcknowledgmentMessage.fromOrder(
                    order,
                    session.getMatchingUnit(),
                    seq,
                    session.getReturnBitfields()
            ).toBytes());

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "[Session {0}] → Sent OrderAcknowledgment: ClOrdID={1}, OrderID={2}", new Object[]{
                        session.getConnectionId(),
                        order.getClOrdID(),
                        order.getOrderID()
                });
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error sending OrderAcknowledgment", e);
        }
    }

    private void sendOrderRejected(String clOrdID, byte reason, String text) {
        try {
            OrderRejectedMessage rejected = new OrderRejectedMessage(clOrdID, reason, text);
            rejected.setMatchingUnit(session.getMatchingUnit());
            sendSequenced(seq -> {
                rejected.setSequenceNumber(seq);
                return rejected.toBytes();
            });

            LOGGER.log(Level.INFO, "[Session {0}] → Sent OrderRejected: ClOrdID={1}, Reason={2}", new Object[]{
                    session.getConnectionId(),
                    clOrdID,
                    (char)reason
            });

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error sending OrderRejected", e);
        }
    }

    private void sendOrderModified(com.boe.simulator.server.order.Order order) {
        try {
            sendSequenced(seq -> OrderModifiedMessage.fromOrder(
                    order,
                    session.getMatchingUnit(),
                    seq
            ).toBytes());

            LOGGER.log(Level.INFO, "[Session {0}] → Sent OrderModified: ClOrdID={1}, OrderID={2}",
                    new Object[]{session.getConnectionId(),
                            order.getClOrdID(), order.getOrderID()});

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,
                    "[Session " + session.getConnectionId() + "] Error sending OrderModified", e);
        }
    }

    private void sendUserModifyRejected(String clOrdID, byte reason, String text) {
        try {
            UserModifyRejectedMessage rejected = new UserModifyRejectedMessage(clOrdID, reason, text);

            sendMessage(rejected.toBytes());

            LOGGER.log(Level.INFO, "[Session {0}] → Sent UserModifyRejected: ClOrdID={1}, Reason={2}",
                    new Object[]{session.getConnectionId(), clOrdID, (char) reason});

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,
                    "[Session " + session.getConnectionId() + "] Error sending UserModifyRejected", e);
        }
    }

    private void sendOrderCancelled(com.boe.simulator.server.order.Order order, byte reason) {
        try {
            OrderCancelledMessage cancelled = OrderCancelledMessage.fromOrder(order, reason);
            cancelled.setMatchingUnit(session.getMatchingUnit());
            sendSequenced(seq -> {
                cancelled.setSequenceNumber(seq);
                return cancelled.toBytes();
            });

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
        if (session.isAuthenticated()) authService.endSession(session.getUsername());

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

    private void shutdownInputQuietly() {
        try {
            if (!socket.isClosed() && !socket.isInputShutdown()) socket.shutdownInput();
        } catch (IOException ignored) {
        }
    }

    private void joinQuietly(Thread thread) {
        if (thread == null) return;
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
