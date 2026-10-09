package com.boe.simulator.server.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.protocol.message.*;
import com.boe.simulator.protocol.serialization.BoeMessageSerializer;
import com.boe.simulator.protocol.types.MessageType;
import com.boe.simulator.server.auth.AuthenticationResult;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.heartbeat.HeartbeatMonitor;
import com.boe.simulator.server.metrics.HealthMetrics;
import com.boe.simulator.server.order.OrderManager;
import com.boe.simulator.server.order.OrderState;
import com.boe.simulator.server.ratelimit.IdenticalRequestLimiter;
import com.boe.simulator.server.ratelimit.OrderRateThreshold;
import com.boe.simulator.server.config.PortAttributes;
import com.boe.simulator.server.ratelimit.RateLimiter;
import com.boe.simulator.server.session.BoeSessionState;
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
    private final OrderManager orderManager;
    private final HealthMetrics healthMetrics;

    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean running;
    private volatile boolean readerDone;
    private final ReentrantLock sendLock = new ReentrantLock();
    private final LinkedBlockingQueue<Inbound> inbound = new LinkedBlockingQueue<>();
    private final UnacknowledgedMessageGate gate;
    private final IdenticalRequestLimiter identicalMassCancels = new IdenticalRequestLimiter(10, Duration.ofSeconds(1));
    private final IdenticalRequestLimiter identicalPurges = new IdenticalRequestLimiter(10, Duration.ofSeconds(1));
    private final IdenticalRequestLimiter riskResets = new IdenticalRequestLimiter(1, Duration.ofMillis(100));
    private final OrderRateThreshold orderRateThreshold;
    private final PortAttributes portAttributes;
    private volatile boolean replayInProgress;
    private volatile BoeSessionState sessionState;
    private volatile boolean ownsAuthSession;

    private record Inbound(BoeMessage message, boolean receivedDuringReplay) {}

    public ClientConnectionHandler(Socket socket, int connectionId, ServerConfiguration config, AuthenticationService authService, ClientSessionManager sessionManager, ErrorHandler errorHandler, RateLimiter rateLimiter, OrderManager orderManager) {
        this(socket, connectionId, config, authService, sessionManager, errorHandler, rateLimiter, orderManager, new HealthMetrics());
    }

    public ClientConnectionHandler(Socket socket, int connectionId, ServerConfiguration config, AuthenticationService authService, ClientSessionManager sessionManager, ErrorHandler errorHandler, RateLimiter rateLimiter, OrderManager orderManager, HealthMetrics healthMetrics) {
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
        this.healthMetrics = healthMetrics;
        this.gate = new UnacknowledgedMessageGate(config.getMaxUnacknowledgedMessages(), config.getResumeReadingBelow());
        this.portAttributes = config.getPortAttributes();
        this.orderRateThreshold = new OrderRateThreshold(config.getPortAttributes().portOrderRateThreshold(),
                config.getPortAttributes().symbolOrderRateThreshold());

        LOGGER.log(Level.INFO, "[Session {0}] Handler created for {1}", new Object[]{
                session.getConnectionId(),
                socket.getRemoteSocketAddress()
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
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Handler error");
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
                session.markInbound();
                healthMetrics.recordBytesReceived(message.getLength());

                if (gate.onRead()) {
                    LOGGER.log(Level.WARNING, "[Session {0}] {1} unacknowledged messages - pausing socket reads",
                            new Object[]{session.getConnectionId(), gate.unacknowledged()});
                }
                if (message.getMessageType() == MessageType.LOGIN_REQUEST.wireValue()) replayInProgress = true;
                inbound.add(new Inbound(message, replayInProgress));

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
            Inbound message;
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
                handleInbound(message.message(), message.receivedDuringReplay());
            } catch (Exception e) {
                errorHandler.handleError(session.getConnectionId(), "Error processing message", e);
                LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Unexpected error");
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

    private void handleInbound(BoeMessage message, boolean receivedDuringReplay) {
        MessageValidator.ValidationResult validation = MessageValidator.validate(message);
        if (message == null || !validation.isValid()) {
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

        processMessage(message, receivedDuringReplay);
    }

    private void processMessage(BoeMessage message, boolean receivedDuringReplay) {
        byte messageType = message.getMessageType();

        if (messageType == MessageType.LOGIN_REQUEST.wireValue()) {
            if (session.isAuthenticated()) {
                logoutForProtocolViolation("Login Request already accepted on this connection");
                return;
            }
            LoginRequestMessage request;
            try {
                request = LoginRequestMessage.parseFromBytes(message.getData());
            } catch (IllegalArgumentException e) {
                rejectLogin(null, LoginResponseMessage.STATUS_INVALID_STRUCTURE, e.getMessage());
                return;
            }
            handleLoginRequest(request);
            return;
        }

        if (!session.isAuthenticated()) {
            logoutForProtocolViolation(String.format("Login Request must be the first message (got 0x%02X)", messageType));
            return;
        }

        String unsupported = unsupportedMessageType(messageType);
        if (unsupported != null) {
            logoutForProtocolViolation(unsupported);
            return;
        }

        try {
            // Create specific message object
            BoeProtocolMessage specificMessage = BoeMessageFactory.createMessage(message);

            switch (specificMessage) {
                case null -> logoutForProtocolViolation(String.format("Malformed message type 0x%02X (%s)",
                        messageType, MessageType.fromByte(messageType)));
                case SessionMessage sessionMessage -> handleSessionMessage(sessionMessage);
                case ApplicationMessage applicationMessage -> {
                    rateLimiter.acquire(session.getConnectionId());
                    handleApplicationMessage(applicationMessage, receivedDuringReplay);
                }
                default -> LOGGER.log(Level.WARNING, "[Session {0}] Unhandled message type: {1}", new Object[]{
                        session.getConnectionId(),
                        specificMessage.getClass().getSimpleName()
                });
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error processing message type 0x" + String.format("%02X", messageType));
        }
    }

    private void handleSessionMessage(SessionMessage message) {
        switch (message) {
            case LogoutRequestMessage logoutRequestMessage -> handleLogoutRequest(logoutRequestMessage);
            case ClientHeartbeatMessage clientHeartbeatMessage -> handleClientHeartbeat(clientHeartbeatMessage);
            default -> LOGGER.log(Level.WARNING, "[Session {0}] Unsupported inbound session message: {1}", new Object[]{
                    session.getConnectionId(),
                    message.getClass().getSimpleName()
            });
        }
    }

    private void handleApplicationMessage(ApplicationMessage message, boolean receivedDuringReplay) {
        BoeSessionState state = sessionState;
        if (state != null) {
            int sequenceNumber = inboundSequenceOf(message);
            if (state.checkInbound(sequenceNumber) == BoeSessionState.InboundCheck.BACKWARD) {
                logoutForProtocolViolation("Sequence number " + Integer.toUnsignedString(sequenceNumber)
                        + " is not above last processed " + Integer.toUnsignedString(state.lastProcessedInbound()));
                return;
            }
            if (message instanceof QuoteUpdateMessage quoteUpdate) {
                sendQuoteUpdateRejected(quoteUpdate.getQuoteUpdateID());
                return;
            }
            if (inboundMatchingUnitOf(message) != 0) {
                rejectNonZeroMatchingUnit(message);
                return;
            }
            if (receivedDuringReplay && !(message instanceof CancelOrderMessage) && !(message instanceof PurgeOrdersMessage)) {
                rejectReceivedDuringReplay(message);
                return;
            }
        }

        if (overOrderRateThreshold(message)) return;

        switch (message) {
            case NewOrderMessage newOrderMessage       -> handleNewOrder(newOrderMessage);
            case CancelOrderMessage cancelOrderMessage -> handleCancelOrder(cancelOrderMessage);
            case ModifyOrderMessage modifyOrderMessage -> handleModifyOrder(modifyOrderMessage);
            case PurgeOrdersMessage purgeOrdersMessage -> handlePurgeOrders(purgeOrdersMessage);
            case ResetRiskMessage resetRiskMessage     -> handleResetRisk(resetRiskMessage);
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

        if (request.getMatchingUnit() != 0 || request.getSequenceNumber() != 0) {
            rejectLogin(request, LoginResponseMessage.STATUS_INVALID_STRUCTURE,
                    "MatchingUnit and SequenceNumber must be 0 in a Login Request");
            return;
        }

        String bitfieldError = ReturnBitfieldRules.validate(request.getReturnBitfields());
        if (bitfieldError != null) {
            rejectLogin(request, LoginResponseMessage.STATUS_INVALID_BITFIELD, bitfieldError);
            return;
        }
        if ((portAttributes.doneForDayRestatements() || portAttributes.carriedOrderRestatements())
                && !requestsLiquidityIndicators(request.getReturnBitfields())) {
            rejectLogin(request, LoginResponseMessage.STATUS_INVALID_BITFIELD, "Order Ack must return Base and Sub LiquidityIndicator");
            return;
        }

        session.setUsername(request.getUsername());
        session.setSessionSubID(request.getSessionSubID());
        session.setReturnBitfields(request.getReturnBitfields());

        AuthenticationResult authResult = authService.authenticate(
                request.getUsername(),
                request.getPassword(),
                request.getSessionSubID()
        );
        if (!authResult.isAccepted()) {
            rejectLogin(request, authResult.toLoginResponseStatusByte(), authResult.message());
            return;
        }
        ownsAuthSession = true;

        BoeSessionState state = sessionManager.getSessionStates().bind(request.getUsername(), request.getSessionSubID());
        UnitSequences units = request.getUnitSequences();

        String unitError = validateUnitSequences(units, state);
        if (unitError != null) {
            authService.endSession(request.getUsername());
            ownsAuthSession = false;
            byte status = unitError.startsWith("Sequence ahead")
                    ? LoginResponseMessage.STATUS_SEQUENCE_AHEAD
                    : LoginResponseMessage.STATUS_INVALID_UNIT;
            rejectLogin(request, status, unitError);
            return;
        }

        state.setReturnBitfields(request.getReturnBitfields());
        int replayAfter = replayStartFor(units);

        state.lock();
        try {
            sessionState = state;
            session.setState(SessionState.AUTHENTICATED);
            sessionManager.registerUsername(this, request.getUsername());

            sendMessage(new LoginResponseMessage(
                    LoginResponseMessage.STATUS_ACCEPTED,
                    authResult.message(),
                    state.lastProcessedInbound(),
                    Map.of((int) BoeSessionState.MATCHING_UNIT, state.lastSentSequence()),
                    units.isNoUnspecifiedUnitReplay(),
                    request.getNumberOfParamGroups(),
                    request.getParamGroupBytes()
            ).toBytes());

            List<byte[]> missed = replayAfter >= 0 ? state.messagesAfter(replayAfter) : List.of();
            if (portAttributes.carriedOrderRestatements() && state.lastSentSequence() == 0) {
                sendRestatements(orderManager.carriedOrdersOf(request.getUsername()), SUB_LIQUIDITY_CARRIED);
            }

            for (byte[] replayed : missed) sendMessage(replayed);

            replayInProgress = false;
            sendMessage(new ReplayCompleteMessage().toBytes());

            LOGGER.log(Level.INFO, "[Session {0}] Login accepted: replayed {1} messages (last sent seq {2}, last received seq {3})",
                    new Object[]{session.getConnectionId(), missed.size(), state.lastSentSequence(), state.lastProcessedInbound()});
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending login response or replay");
        } finally {
            state.unlock();
            replayInProgress = false;
        }

        disableReadTimeout();
        heartbeatMonitor.start();
        sessionManager.getStatistics().incrementSuccessfulLogins();
    }

    private String validateUnitSequences(UnitSequences units, BoeSessionState state) {
        for (Map.Entry<Integer, Integer> unit : units.lastReceivedByUnit().entrySet()) {
            int unitNumber = unit.getKey();
            int lastReceived = unit.getValue();
            if (unitNumber != BoeSessionState.MATCHING_UNIT) {
                if (lastReceived != 0) return "Invalid unit " + unitNumber + " (only unit " + BoeSessionState.MATCHING_UNIT + " exists)";
            } else if (Integer.compareUnsigned(lastReceived, state.lastSentSequence()) > 0) {
                return "Sequence ahead: unit " + unitNumber + " last received " + Integer.toUnsignedString(lastReceived)
                        + " but highest sent is " + state.lastSentSequence();
            }
        }
        return null;
    }

    // -1 = no replay
    private int replayStartFor(UnitSequences units) {
        if (!units.isPresent()) return 0;
        Integer lastReceived = units.lastReceivedByUnit().get((int) BoeSessionState.MATCHING_UNIT);
        if (lastReceived != null) return lastReceived;
        return units.isNoUnspecifiedUnitReplay() ? -1 : 0;
    }

    private void rejectLogin(LoginRequestMessage request, byte status, String text) {
        try {
            sendMessage(new LoginResponseMessage(
                    status,
                    text,
                    0,
                    Map.of(),
                    request != null && request.getUnitSequences().isNoUnspecifiedUnitReplay(),
                    request != null ? request.getNumberOfParamGroups() : 0,
                    request != null ? request.getParamGroupBytes() : new byte[0]
            ).toBytes());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending LoginResponse");
        }
        replayInProgress = false;
        session.setState(SessionState.ERROR);
        sessionManager.getStatistics().incrementFailedLogins();
        LOGGER.log(Level.WARNING, "[Session {0}] Login rejected ({1}): {2}", new Object[]{
                session.getConnectionId(), (char) status, text
        });

        try {
            Thread.sleep(100); // Give time for LoginResponse to be sent
            running = false;
            socket.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Error closing socket after failed login", e);
        }
    }

    private void handleLogoutRequest(LogoutRequestMessage request) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing logout request", session.getConnectionId());
        warnIfSessionHeaderNotZero("LogoutRequest", request.getMatchingUnit(), request.getSequenceNumber());

        sendLogout(LogoutResponseMessage.REASON_USER_REQUESTED, "Logout successful");
    }

    public void logoutForHeartbeatTimeout() {
        sendLogout(LogoutResponseMessage.REASON_PROTOCOL_VIOLATION, "Heartbeat timeout");
    }

    public void logoutAndClose(byte reason, String text) {
        sendLogout(reason, text);
        shutdownInputQuietly();
    }

    private void logoutForProtocolViolation(String text) {
        LOGGER.log(Level.WARNING, "[Session {0}] Protocol violation - logging out: {1}",
                new Object[]{session.getConnectionId(), text});
        sendLogout(LogoutResponseMessage.REASON_PROTOCOL_VIOLATION, text);
    }

    private void sendLogout(byte reason, String text) {
        session.setState(SessionState.DISCONNECTING);
        heartbeatMonitor.stop();

        BoeSessionState state = sessionState;
        if (state != null) state.lock();
        try {
            sendMessage(new LogoutResponseMessage(
                    reason,
                    text,
                    state != null ? state.lastProcessedInbound() : 0,
                    state != null ? Map.of((int) BoeSessionState.MATCHING_UNIT, state.lastSentSequence()) : Map.of()
            ).toBytes());
            LOGGER.log(Level.INFO, "[Session {0}] → Sent Logout ({1})", new Object[]{session.getConnectionId(), (char) reason});
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending Logout");
        } finally {
            if (state != null) state.unlock();
        }

        if (ownsAuthSession) {
            authService.endSession(session.getUsername());
            ownsAuthSession = false;
        }
        running = false;
    }

    private void handleClientHeartbeat(ClientHeartbeatMessage heartbeat) {
        LOGGER.log(Level.FINE, "[Session {0}] Client heartbeat received", session.getConnectionId());
        warnIfSessionHeaderNotZero("ClientHeartbeat", heartbeat.getMatchingUnit(), heartbeat.getSequenceNumber());
        session.updateHeartbeatReceived();
    }

    private void warnIfSessionHeaderNotZero(String messageName, byte matchingUnit, int sequenceNumber) {
        if (matchingUnit != 0 || sequenceNumber != 0) {
            LOGGER.log(Level.WARNING, "[Session {0}] {1} should have MatchingUnit 0 and SequenceNumber 0, got {2} / {3}",
                    new Object[]{session.getConnectionId(), messageName, matchingUnit, Integer.toUnsignedString(sequenceNumber)});
        }
    }

    private static int inboundMatchingUnitOf(ApplicationMessage message) {
        return switch (message) {
            case NewOrderMessage m -> m.getMatchingUnit();
            case CancelOrderMessage m -> m.getMatchingUnit();
            case ModifyOrderMessage m -> m.getMatchingUnit();
            case PurgeOrdersMessage m -> m.getMatchingUnit();
            case ResetRiskMessage m -> m.getMatchingUnit();
            default -> 0;
        };
    }

    private void rejectNonZeroMatchingUnit(ApplicationMessage message) {
        String text = "MatchingUnit must be 0 for inbound messages";
        LOGGER.log(Level.WARNING, "[Session {0}] {1} with MatchingUnit {2} rejected",
                new Object[]{session.getConnectionId(), message.getClass().getSimpleName(), inboundMatchingUnitOf(message)});
        switch (message) {
            case NewOrderMessage m -> sendOrderRejected(m.getClOrdID(), OrderRejectedMessage.REASON_UNFORESEEN, text, OrderReturnFields.forNewOrder(m));
            case ModifyOrderMessage m -> sendUserModifyRejected(m.getClOrdID(), UserModifyRejectedMessage.REASON_UNFORESEEN, text, modifyReturnFields(m));
            case CancelOrderMessage m -> sendCancelRejected(m.getOrigClOrdID(), CancelRejectedMessage.REASON_UNFORESEEN, text, cancelReturnFields(m));
            case PurgeOrdersMessage m -> sendPurgeRejected(CancelRejectedMessage.REASON_UNFORESEEN, text, m.getMassCancelId());
            case ResetRiskMessage m -> sendRiskResetAcknowledgment(m.getRiskStatusID(), RiskResetAcknowledgmentMessage.RESULT_INVALID_MATCHING_UNIT);
            default -> { }
        }
    }

    private static int inboundSequenceOf(ApplicationMessage message) {
        return switch (message) {
            case NewOrderMessage m -> m.getSequenceNumber();
            case CancelOrderMessage m -> m.getSequenceNumber();
            case ModifyOrderMessage m -> m.getSequenceNumber();
            case QuoteUpdateMessage m -> m.getSequenceNumber();
            case PurgeOrdersMessage m -> m.getSequenceNumber();
            case ResetRiskMessage m -> m.getSequenceNumber();
            default -> 0;
        };
    }

    // Above the Port / Symbol Order Rate Threshold new orders are rejected, modifies become cancels and cancels go through (p.221)
    private boolean overOrderRateThreshold(ApplicationMessage message) {
        String symbol = switch (message) {
            case NewOrderMessage m -> m.getSymbol();
            case ModifyOrderMessage m -> orderManager.findByClOrdID(m.getOrigClOrdID()).map(com.boe.simulator.server.order.Order::getSymbol).orElse(null);
            default -> null;
        };
        if (!orderRateThreshold.exceeded(symbol)) return false;
        switch (message) {
            case NewOrderMessage m -> {
                sendOrderRejected(m.getClOrdID(), OrderRejectedMessage.REASON_RATE_THRESHOLD, "Order rate threshold exceeded",
                        OrderReturnFields.forNewOrder(m));
                return true;
            }
            case ModifyOrderMessage m -> {
                OrderManager.CancelResponse cancel = orderManager.processCancelOrder(new CancelOrderMessage(m.getOrigClOrdID()), session);
                if (cancel.isCancelled()) sendOrderCancelled(cancel.getOrder(), cancel.getCancelReason());
                else sendUserModifyRejected(m.getClOrdID(), UserModifyRejectedMessage.REASON_RATE_THRESHOLD,
                        "Order rate threshold exceeded", modifyReturnFields(m));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // Done For Day (p.14) and Carried Order (p.15) restatements need these two Order Acknowledgment return fields
    private static boolean requestsLiquidityIndicators(ReturnBitfields returnBitfields) {
        byte[] mask = returnBitfields.maskFor(OrderAcknowledgmentMessage.MESSAGE_TYPE);
        return mask != null && mask.length >= 7 && (mask[4] & 0x40) != 0 && (mask[6] & 0x01) != 0;
    }

    /** Unsolicited Order Acknowledgments with BaseLiquidityIndicator A and SubLiquidityIndicator D or C. */
    public void sendRestatements(List<com.boe.simulator.server.order.Order> orders, byte subLiquidityIndicator) {
        try {
            for (com.boe.simulator.server.order.Order order : orders) {
                sendSequenced(seq -> OrderAcknowledgmentMessage.fromOrder(order, BoeSessionState.MATCHING_UNIT, seq,
                        OrderReturnFields.forOrder(order)
                                .put(ReturnField.BASE_LIQUIDITY_INDICATOR, (byte) 'A')
                                .put(ReturnField.SUB_LIQUIDITY_INDICATOR, subLiquidityIndicator)
                                .select(session.getReturnBitfields(), OrderAcknowledgmentMessage.MESSAGE_TYPE)).toBytes());
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending restatements");
        }
    }

    public static final byte SUB_LIQUIDITY_DONE_FOR_DAY = (byte) 'D';
    public static final byte SUB_LIQUIDITY_CARRIED = (byte) 'C';

    private static String unsupportedMessageType(byte messageType) {
        MessageType type;
        try {
            type = MessageType.fromByte(messageType);
        } catch (IllegalArgumentException e) {
            return String.format("Unknown message type 0x%02X", messageType);
        }
        if (!type.isMemberToCboe()) return String.format("Cboe-only message type 0x%02X (%s)", messageType, type);
        if (!BoeMessageFactory.isRequest(messageType)) return String.format("Unsupported message type 0x%02X (%s)", messageType, type);
        return null;
    }

    private void rejectReceivedDuringReplay(ApplicationMessage message) {
        switch (message) {
            case NewOrderMessage m -> sendOrderRejected(m.getClOrdID(),
                    OrderRejectedMessage.REASON_RECEIVED_DURING_REPLAY, "Order received by Cboe during replay", OrderReturnFields.forNewOrder(m));
            case ModifyOrderMessage m -> sendUserModifyRejected(m.getClOrdID(),
                    UserModifyRejectedMessage.REASON_RECEIVED_DURING_REPLAY, "Order received by Cboe during replay", modifyReturnFields(m));
            case ResetRiskMessage m -> sendRiskResetAcknowledgment(m.getRiskStatusID(), RiskResetAcknowledgmentMessage.RESULT_IN_REPLAY);
            default -> LOGGER.log(Level.WARNING, "[Session {0}] Ignoring {1} received during replay",
                    new Object[]{session.getConnectionId(), message.getClass().getSimpleName()});
        }
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


        OrderManager.OrderResponse response = orderManager.processNewOrder(newOrder, session);

        if (response.isAcknowledged()) {
            sendOrderAcknowledgment(response.getOrder());
            sendExecutions(response.getExecutions());
            if (response.getOrder().getState() == OrderState.CANCELLED) {
                sendOrderCancelled(response.getOrder(), cancelReasonOf(response.getOrder(), OrderCancelledMessage.REASON_NO_LIQUIDITY));
            }
        } else {
            sendOrderRejected(
                    response.getClOrdID(),
                    response.getRejectReason(),
                    response.getRejectText(),
                    OrderReturnFields.forNewOrder(newOrder)
            );
        }
    }

    private void handleModifyOrder(ModifyOrderMessage modifyOrder) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing ModifyOrder: origClOrdID={1}, newClOrdID={2}",
                new Object[]{session.getConnectionId(),
                        modifyOrder.getOrigClOrdID(), modifyOrder.getClOrdID()});

        OrderManager.ModifyResponse response = orderManager.processModifyOrder(modifyOrder, session);

        if (response.isModified()) {
            sendOrderModified(response.getOrder(), modifyOrder.getOrigClOrdID());
            sendExecutions(response.getExecutions());
        } else if (response.isAutoCancelled()) {
            sendExecutions(response.getExecutions());
            sendOrderCancelled(response.getOrder(), cancelReasonOf(response.getOrder(), OrderCancelledMessage.REASON_USER_REQUESTED));
        } else {
            sendUserModifyRejected(response.getClOrdID(),
                    response.getRejectReason(), response.getRejectText(), modifyReturnFields(modifyOrder));
            if (response.cancelledOriginal()) sendOrderCancelled(response.getOrder(), OrderCancelledMessage.REASON_USER_REQUESTED);
        }
    }

    private void handleCancelOrder(CancelOrderMessage cancelOrder) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing CancelOrder: {1}", new Object[]{
                session.getConnectionId(), cancelOrder
        });

        if (cancelOrder.isMassCancel() && cancelOrder.getFieldError() == null
                && !identicalMassCancels.tryAcquire(identicalMassCancelKey(cancelOrder))) {
            sendCancelRejected(cancelOrder.getOrigClOrdID(), CancelRejectedMessage.REASON_RATE_THRESHOLD,
                    "More than 10 identical mass cancels per second", cancelReturnFields(cancelOrder));
            return;
        }

        OrderManager.CancelResponse response = orderManager.processCancelOrder(cancelOrder, session);

        if (response.isCancelled()) {
            sendOrderCancelled(response.getOrder(), response.getCancelReason());
        } else if (response.isMassCancelled()) {
            char style = response.getAckStyle();
            if (style == 'M' || style == 'B') {
                for (com.boe.simulator.server.order.Order order : response.getMassCancelledOrders()) {
                    sendOrderCancelled(order, response.getCancelReason(), response.getSubreason());
                }
            }
            if (style == 'S' || style == 'B') sendMassCancelAcknowledgment(response.getMassCancelId(), response.getMassCancelCount());
        } else {
            sendCancelRejected(response.getClOrdID(), response.getRejectReason(), response.getRejectText(), cancelReturnFields(cancelOrder));
        }
    }

    private void handlePurgeOrders(PurgeOrdersMessage purge) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing PurgeOrders: {1}", new Object[]{session.getConnectionId(), purge});

        if (purge.getFieldError() == null && !identicalPurges.tryAcquire(identicalPurgeKey(purge))) {
            sendPurgeRejected(CancelRejectedMessage.REASON_RATE_THRESHOLD, "More than 10 identical purges per second", purge.getMassCancelId());
            return;
        }

        OrderManager.CancelResponse response = orderManager.processPurgeOrders(purge, session);
        if (response.isRejected()) {
            sendPurgeRejected(response.getRejectReason(), response.getRejectText(), purge.getMassCancelId());
            return;
        }

        int count = response.getMassCancelCount();
        switch (response.getAckStyle()) {
            case 'M' -> response.getMassCancelledOrders().forEach(o -> sendOrderCancelled(o, response.getCancelReason(), response.getSubreason()));
            case 'S' -> sendMassCancelAcknowledgment(response.getMassCancelId(), count, 0);
            case 'B' -> {
                response.getMassCancelledOrders().forEach(o -> sendOrderCancelled(o, response.getCancelReason(), response.getSubreason()));
                sendMassCancelAcknowledgment(response.getMassCancelId(), count, 0);
            }
            case 'A' -> {
                sendMassCancelAcknowledgment(response.getMassCancelId(), count, 0);
                if (count > 0) sendPurgeNotification(response, count);
            }
            case 'I' -> {
                if (count > 0) sendMassCancelAcknowledgment(response.getMassCancelId(), count, BoeSessionState.MATCHING_UNIT);
                sendMassCancelAcknowledgment(response.getMassCancelId(), count, 0);
            }
            default -> LOGGER.log(Level.WARNING, "Unexpected acknowledgement style {0}", response.getAckStyle());
        }
    }

    private void handleResetRisk(ResetRiskMessage reset) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing ResetRisk: {1} {2}",
                new Object[]{session.getConnectionId(), reset.getRiskStatusID(), reset.getRiskReset()});
        boolean allowed = riskResets.tryAcquireAll(reset.getRiskReset().chars().mapToObj(type -> riskResetKey((char) type, reset)).toList());
        byte result = allowed ? orderManager.processResetRisk(reset, session.getUsername()) : RiskResetAcknowledgmentMessage.RESULT_IGNORED;
        sendRiskResetAcknowledgment(reset.getRiskStatusID(), result);
    }

    // One reset per type (Risk Root, EFID, EFID Group, CustomGroupID) and target per 100 ms (p.98); S=T and F=E in the simulator
    private static String riskResetKey(char type, ResetRiskMessage m) {
        return switch (type) {
            case 'S', 'T' -> "S|" + m.getClearingFirm() + "|" + m.getRiskRoot();
            case 'F', 'E' -> "F|" + m.getClearingFirm();
            case 'G' -> "G|" + m.getClearingFirm();
            case 'C' -> "C|" + m.getClearingFirm() + "|" + m.getCustomGroupId();
            default -> String.valueOf(type);
        };
    }

    // Identical = same CustomGroupID, Symbol, Clearing Firm, MatchingUnit, Lockout, Instrument Type and GTC filters (p.95)
    private static String identicalPurgeKey(PurgeOrdersMessage m) {
        return m.getCustomGroupIds() + "|" + m.getRiskRoot() + "|" + m.getClearingFirm() + "|" + m.getTargetMatchingUnit()
                + "|" + m.massCancelInstChar(3) + "|" + m.massCancelInstChar(4) + "|" + m.massCancelInstChar(5);
    }

    // Identical = same Symbol (RiskRoot), ClearingFirm, Lockout, Instrument Type and GTC filters (p.74)
    private static String identicalMassCancelKey(CancelOrderMessage m) {
        return m.getRiskRoot() + "|" + m.getClearingFirm() + "|" + m.massCancelInstChar(3)
                + "|" + m.massCancelInstChar(4) + "|" + m.massCancelInstChar(5);
    }

    public void sendSequenced(IntFunction<byte[]> encoder) throws IOException {
        BoeSessionState state = sessionState;
        if (state == null) throw new IllegalStateException("Sequenced message before login on session " + session.getConnectionId());
        state.sendSequenced(encoder, session.isAuthenticated() ? this::sendMessage : null);
    }

    public void sendMessage(byte[] messageBytes) throws IOException {
        sendLock.lock();
        try {
            outputStream.write(messageBytes);
            outputStream.flush();
            session.incrementMessagesSent();
            session.markOutbound();
            healthMetrics.recordBytesSent(messageBytes.length);

            LOGGER.log(Level.FINE, "[Session {0}] → Sent message ({1} bytes)", new Object[]{
                    session.getConnectionId(),
                    messageBytes.length
            });
        } finally {
            sendLock.unlock();
        }
    }

    private void sendExecutions(List<IntFunction<byte[]>> executions) {
        try {
            for (IntFunction<byte[]> execution : executions) sendSequenced(execution);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Failed to send executions (journaled for replay)");
        }
    }

        private void sendOrderAcknowledgment(com.boe.simulator.server.order.Order order) {
        try {
            sendSequenced(seq -> OrderAcknowledgmentMessage.fromOrder(
                    order,
                    BoeSessionState.MATCHING_UNIT,
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
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending OrderAcknowledgment");
        }
    }

    private void sendOrderRejected(String clOrdID, byte reason, String text, ReturnFields values) {
        try {
            sendMessage(new OrderRejectedMessage(clOrdID, reason, text)
                    .withReturnFields(values.select(session.getReturnBitfields(), OrderRejectedMessage.MESSAGE_TYPE)).toBytes());

            LOGGER.log(Level.INFO, "[Session {0}] → Sent OrderRejected: ClOrdID={1}, Reason={2}", new Object[]{
                    session.getConnectionId(),
                    clOrdID,
                    (char)reason
            });

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending OrderRejected");
        }
    }

    private void sendOrderModified(com.boe.simulator.server.order.Order order, String origClOrdID) {
        try {
            sendSequenced(seq -> OrderModifiedMessage.fromOrder(
                    order,
                    BoeSessionState.MATCHING_UNIT,
                    seq,
                    session.getReturnBitfields(),
                    origClOrdID
            ).toBytes());

            LOGGER.log(Level.INFO, "[Session {0}] → Sent OrderModified: ClOrdID={1}, OrderID={2}",
                    new Object[]{session.getConnectionId(),
                            order.getClOrdID(), order.getOrderID()});

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending OrderModified");
        }
    }

    private void sendUserModifyRejected(String clOrdID, byte reason, String text, ReturnFields values) {
        try {
            sendMessage(new UserModifyRejectedMessage(clOrdID, reason, text)
                    .withReturnFields(values.select(session.getReturnBitfields(), UserModifyRejectedMessage.MESSAGE_TYPE)).toBytes());

            LOGGER.log(Level.INFO, "[Session {0}] → Sent UserModifyRejected: ClOrdID={1}, Reason={2}",
                    new Object[]{session.getConnectionId(), clOrdID, (char) reason});

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending UserModifyRejected");
        }
    }

    private void sendCancelRejected(String clOrdID, byte reason, String text, ReturnFields values) {
        try {
            sendMessage(new CancelRejectedMessage(clOrdID, reason, text)
                    .withReturnFields(values.select(session.getReturnBitfields(), CancelRejectedMessage.MESSAGE_TYPE)).toBytes());
            LOGGER.log(Level.INFO, "[Session {0}] → Sent CancelRejected: ClOrdID={1}, Reason={2}",
                    new Object[]{session.getConnectionId(), clOrdID, (char) reason});
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending CancelRejected");
        }
    }

    private static byte cancelReasonOf(com.boe.simulator.server.order.Order order, byte fallback) {
        return order.getCancelReason() != 0 ? order.getCancelReason() : fallback;
    }

    private static ReturnFields modifyReturnFields(ModifyOrderMessage m) {
        return new ReturnFields().put(ReturnField.ROUTING_FIRM_ID, m.getRoutingFirmID());
    }

    private static ReturnFields cancelReturnFields(CancelOrderMessage m) {
        return new ReturnFields()
                .put(ReturnField.MASS_CANCEL_ID, m.getMassCancelId())
                .put(ReturnField.ROUTING_FIRM_ID, m.getRoutingFirmID());
    }

    private void sendOrderCancelled(com.boe.simulator.server.order.Order order, byte reason) {
        sendOrderCancelled(order, reason, (byte) 0);
    }

    private void sendOrderCancelled(com.boe.simulator.server.order.Order order, byte reason, byte subreason) {
        try {
            OrderCancelledMessage cancelled = OrderCancelledMessage.fromOrder(order, reason, OrderReturnFields.forOrder(order)
                    .put(ReturnField.SUBREASON, subreason != 0 ? subreason : null)
                    .select(session.getReturnBitfields(), OrderCancelledMessage.MESSAGE_TYPE));
            cancelled.setMatchingUnit(BoeSessionState.MATCHING_UNIT);
            sendSequenced(seq -> {
                cancelled.setSequenceNumber(seq);
                return cancelled.toBytes();
            });

            LOGGER.log(Level.INFO, "[Session {0}] → Sent OrderCancelled: ClOrdID={1}", new Object[]{
                    session.getConnectionId(),
                    order.getClOrdID()
            });

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending OrderCancelled");
        }
    }

    private void sendQuoteUpdateRejected(String quoteUpdateID) {
        try {
            sendMessage(new QuoteUpdateRejectedMessage(quoteUpdateID, QuoteUpdateRejectedMessage.REASON_NOT_ENABLED_FOR_QUOTES).toBytes());
            LOGGER.log(Level.INFO, "[Session {0}] → Sent QuoteUpdateRejected: {1}", new Object[]{session.getConnectionId(), quoteUpdateID});
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending QuoteUpdateRejected");
        }
    }

    private void sendPurgeRejected(byte reason, String text, String massCancelId) {
        try {
            sendMessage(new PurgeRejectedMessage(reason, text).withReturnFields(new ReturnFields()
                    .put(ReturnField.MASS_CANCEL_ID, massCancelId)
                    .select(session.getReturnBitfields(), PurgeRejectedMessage.MESSAGE_TYPE)).toBytes());
            LOGGER.log(Level.INFO, "[Session {0}] → Sent PurgeRejected: {1} {2}", new Object[]{session.getConnectionId(), (char) reason, text});
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending PurgeRejected");
        }
    }

    private void sendPurgeNotification(OrderManager.CancelResponse response, int count) {
        try {
            sendMessage(new PurgeNotificationMessage(response.getMassCancelId(), count, BoeSessionState.MATCHING_UNIT,
                    response.getPurgeClearingFirm(), response.getPurgeRiskRoot(), response.isLockout()).toBytes());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending PurgeNotification");
        }
    }

    private void sendRiskResetAcknowledgment(String riskStatusID, byte result) {
        try {
            sendMessage(new RiskResetAcknowledgmentMessage(riskStatusID, result).toBytes());
            LOGGER.log(Level.INFO, "[Session {0}] → Sent RiskResetAcknowledgment: {1} {2}",
                    new Object[]{session.getConnectionId(), riskStatusID, (char) result});
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending RiskResetAcknowledgment");
        }
    }

    private void sendMassCancelAcknowledgment(String massCancelId, int count) {
        sendMassCancelAcknowledgment(massCancelId, count, 0);
    }

    private void sendMassCancelAcknowledgment(String massCancelId, int count, int sourceMatchingUnit) {
        try {
            sendMessage(new MassCancelAcknowledgmentMessage(massCancelId, count, sourceMatchingUnit).toBytes());
            LOGGER.log(Level.INFO, "[Session {0}] → Sent MassCancelAcknowledgment: {1} orders, ID={2}",
                    new Object[]{session.getConnectionId(), count, massCancelId});
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "[Session " + session.getConnectionId() + "] Error sending MassCancelAcknowledgment");
        }
    }

    private void cleanup() {
        running = false;

        if (heartbeatMonitor != null) heartbeatMonitor.shutdown();
        if (ownsAuthSession) {
            authService.endSession(session.getUsername());
            orderManager.cancelOnDisconnect(session.getUsername());
        }

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

    private void disableReadTimeout() {
        try {
            socket.setSoTimeout(0);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Could not clear socket read timeout", e);
        }
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
