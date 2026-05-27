package com.boe.simulator.load;

import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.NewOrderMessage;
import com.sun.management.UnixOperatingSystemMXBean;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TitaniumBOE-Sim — Load Test Runner
 *
 * Tests five dimensions independently:
 *   1. TCP Connection Capacity  — raw sockets to port 9090, no login
 *   2. BOE Login Throughput     — full login/hold/close cycle (needs unique users)
 *   3. REST API Throughput      — concurrent HTTP requests to port 8081
 *   4. Order Ack Latency        — spec-correct New Order → Order Ack RTT (μs precision)
 *   5. Memory Stability         — 10,000 orders, heap growth bounded check
 *
 * HOW TO RUN:
 *   ulimit -n 65535
 *   mvn test-compile
 *   java -cp "target/test-classes:target/classes:$(mvn -q dependency:build-classpath \
 *       -DincludeScope=test -Dmdep.outputFile=/dev/stdout)" \
 *       com.boe.simulator.load.LoadTestRunner [options]
 *
 * OPTIONS:
 *   --tcp=N            raw TCP connections to open             (default: 1000)
 *   --logins=N         BOE login sessions (users created)      (default: 100)
 *   --rest=N           total REST requests                     (default: 5000)
 *   --concurrency=N    max parallel REST requests              (default: 200)
 *   --hold=N           seconds to hold TCP connections open    (default: 5)
 *   --ack-sessions=N   parallel sessions for latency test      (default: 10)
 *   --ack-orders=N     orders per session for latency test     (default: 100)
 *   --mem-orders=N     total orders for memory stability test  (default: 10000)
 *   --skip-tcp         skip Phase 1
 *   --skip-login       skip Phase 2
 *   --skip-rest        skip Phase 3
 *   --skip-ack         skip Phase 4
 *   --skip-memory      skip Phase 5
 */
public class LoadTestRunner {

    static final String HOST      = "localhost";
    static final int    BOE_PORT  = 9090;
    static final int    REST_PORT = 8081;

    // BOE message type constants
    static final byte MSG_SERVER_HB   = 0x01;
    static final byte MSG_LOGIN_RESP  = 0x07;
    static final byte MSG_ORDER_ACK   = 0x25;
    static final byte MSG_ORDER_REJ   = 0x26;

    // Login response layout offsets (within full wire frame)
    static final int  LOGIN_RESP_STATUS_OFFSET = 10;
    static final byte STATUS_ACCEPTED          = 'A';

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        int tcpTarget    = intArg(args, "--tcp",           1_000);
        int loginTarget  = intArg(args, "--logins",          100);
        int restTotal    = intArg(args, "--rest",          5_000);
        int concurrency  = intArg(args, "--concurrency",     200);
        int holdSecs     = intArg(args, "--hold",              5);
        int ackSessions  = intArg(args, "--ack-sessions",     10);
        int ackOrders    = intArg(args, "--ack-orders",       100);
        int memOrders    = intArg(args, "--mem-orders",   10_000);

        boolean skipTcp    = hasFlag(args, "--skip-tcp");
        boolean skipLogin  = hasFlag(args, "--skip-login");
        boolean skipRest   = hasFlag(args, "--skip-rest");
        boolean skipAck    = hasFlag(args, "--skip-ack");
        boolean skipMemory = hasFlag(args, "--skip-memory");

        printBanner();
        printSystemInfo(tcpTarget);
        System.out.println();

        TcpCapacityResult     tcpResult    = null;
        LoginThroughputResult loginResult  = null;
        RestResult            restResult   = null;
        OrderAckLatencyResult ackResult    = null;
        MemoryStabilityResult memResult    = null;

        if (!skipTcp)    { tcpResult   = runTcpCapacityTest(tcpTarget, holdSecs);           System.out.println(); }
        if (!skipLogin)  { loginResult = runLoginThroughputTest(loginTarget, holdSecs);      System.out.println(); }
        if (!skipRest)   { restResult  = runRestTest(restTotal, concurrency);                System.out.println(); }
        if (!skipAck)    { ackResult   = runOrderAckLatencyTest(ackSessions, ackOrders);     System.out.println(); }
        if (!skipMemory) { memResult   = runMemoryStabilityTest(memOrders);                  System.out.println(); }

        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  FINAL REPORT");
        System.out.println("═══════════════════════════════════════════════════════════");
        if (tcpResult   != null) { printTcpCapacityReport(tcpResult);    System.out.println(); }
        if (loginResult != null) { printLoginReport(loginResult);         System.out.println(); }
        if (restResult  != null) { printRestReport(restResult);           System.out.println(); }
        if (ackResult   != null) { printOrderAckReport(ackResult);        System.out.println(); }
        if (memResult   != null) { printMemoryReport(memResult); }
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    // ── Phase 1: TCP Raw Connection Capacity ─────────────────────────────────

    static TcpCapacityResult runTcpCapacityTest(int target, int holdSecs) throws InterruptedException {
        System.out.printf("┌─ Phase 1: TCP Connection Capacity  port=%d  target=%,d%n", BOE_PORT, target);
        System.out.println("│  (raw sockets — no BOE login)");
        System.out.println("│");

        AtomicInteger accepted  = new AtomicInteger();
        AtomicInteger refused   = new AtomicInteger();
        AtomicInteger errors    = new AtomicInteger();
        ConcurrentLinkedQueue<Long> connectMs = new ConcurrentLinkedQueue<>();
        List<Socket> held = new CopyOnWriteArrayList<>();

        long t0 = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(target);
        ExecutorService exec  = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < target; i++) {
            exec.submit(() -> {
                try {
                    long start = System.nanoTime();
                    Socket s   = new Socket(HOST, BOE_PORT);
                    connectMs.add((System.nanoTime() - start) / 1_000_000);
                    accepted.incrementAndGet();
                    held.add(s);
                } catch (ConnectException e) {
                    refused.incrementAndGet();
                } catch (IOException e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                    int done = target - (int) latch.getCount();
                    if (done % 200 == 0 && done > 0)
                        System.out.printf("│  %,5d / %,d  open: %,d%n", done, target, held.size());
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);

        System.out.printf("│%n│  Peak open: %,d  —  holding for %ds...%n", held.size(), holdSecs);
        Thread.sleep(holdSecs * 1_000L);

        AtomicInteger stillOpen = new AtomicInteger();
        for (Socket s : held) {
            if (!s.isClosed() && s.isConnected()) stillOpen.incrementAndGet();
            closeQuietly(s);
        }
        exec.shutdownNow();

        return new TcpCapacityResult(target, accepted.get(), refused.get(), errors.get(),
                stillOpen.get(), System.currentTimeMillis() - t0, connectMs);
    }

    // ── Phase 2: BOE Login Throughput ─────────────────────────────────────────

    static LoginThroughputResult runLoginThroughputTest(int target, int holdSecs) throws Exception {
        System.out.printf("┌─ Phase 2: BOE Login Throughput  port=%d  sessions=%,d%n", BOE_PORT, target);
        System.out.println("│");

        System.out.printf("│  Registering %,d test users via REST...%n", target);
        List<String[]> creds = new ArrayList<>(target);
        for (int i = 0; i < target; i++) {
            int letter = i / 1000;
            int num    = i % 1000;
            String user = String.format("%c%03d", 'A' + letter, num);
            String pass = String.format("Ld%05d!", i);
            creds.add(new String[]{user, pass});
        }

        HttpClient http = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        AtomicInteger registered = new AtomicInteger();
        AtomicInteger regFailed  = new AtomicInteger();
        CountDownLatch regLatch  = new CountDownLatch(target);
        ExecutorService regExec  = Executors.newVirtualThreadPerTaskExecutor();

        for (String[] c : creds) {
            regExec.submit(() -> {
                try {
                    String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", c[0], c[1]);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("http://" + HOST + ":" + REST_PORT + "/api/auth/register"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .timeout(Duration.ofSeconds(5))
                            .build();
                    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 201 || resp.statusCode() == 409) registered.incrementAndGet();
                    else regFailed.incrementAndGet();
                } catch (Exception e) {
                    regFailed.incrementAndGet();
                } finally {
                    regLatch.countDown();
                }
            });
        }
        regLatch.await(60, TimeUnit.SECONDS);
        regExec.shutdownNow();
        System.out.printf("│  Registered: %,d  failed: %,d%n", registered.get(), regFailed.get());
        System.out.println("│");

        System.out.printf("│  Opening %,d concurrent BOE sessions...%n", registered.get());

        AtomicInteger loginOk     = new AtomicInteger();
        AtomicInteger loginFailed = new AtomicInteger();
        AtomicInteger connErrors  = new AtomicInteger();
        ConcurrentLinkedQueue<Long> loginMs = new ConcurrentLinkedQueue<>();
        List<Socket> openSessions = new CopyOnWriteArrayList<>();

        long t0 = System.currentTimeMillis();
        CountDownLatch loginLatch = new CountDownLatch(registered.get());
        ExecutorService loginExec = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < registered.get(); i++) {
            final int idx = i;
            loginExec.submit(() -> {
                try {
                    Socket s = new Socket(HOST, BOE_PORT);
                    s.setSoTimeout(10_000);

                    String[] c   = creds.get(idx);
                    String subID = String.format("S%03d", idx % 1000);
                    byte[] loginBytes = new LoginRequestMessage(c[0], c[1], subID).toBytes();

                    long t1 = System.nanoTime();
                    s.getOutputStream().write(loginBytes);
                    s.getOutputStream().flush();
                    byte[] resp = readAtLeast(s.getInputStream(), 11);
                    loginMs.add((System.nanoTime() - t1) / 1_000_000);

                    if (resp != null && resp.length >= 11
                            && resp[4] == MSG_LOGIN_RESP
                            && resp[LOGIN_RESP_STATUS_OFFSET] == STATUS_ACCEPTED) {
                        loginOk.incrementAndGet();
                        openSessions.add(s);
                    } else {
                        loginFailed.incrementAndGet();
                        closeQuietly(s);
                    }
                } catch (IOException e) {
                    connErrors.incrementAndGet();
                } finally {
                    loginLatch.countDown();
                    int done = registered.get() - (int) loginLatch.getCount();
                    if (done % 50 == 0 && done > 0)
                        System.out.printf("│  %,3d / %,d  sessions alive: %,d%n",
                                done, registered.get(), openSessions.size());
                }
            });
        }

        loginLatch.await(120, TimeUnit.SECONDS);
        long peakMs = System.currentTimeMillis() - t0;
        int peak    = openSessions.size();

        System.out.printf("│%n│  Peak concurrent sessions: %,d  —  holding for %ds...%n", peak, holdSecs);
        Thread.sleep(holdSecs * 1_000L);

        for (Socket s : openSessions) closeQuietly(s);
        loginExec.shutdownNow();

        return new LoginThroughputResult(target, registered.get(), loginOk.get(),
                loginFailed.get(), connErrors.get(), peak, peakMs, loginMs);
    }

    // ── Phase 3: REST API Throughput ──────────────────────────────────────────

    static final String[] PUBLIC_ENDPOINTS = {
            "http://" + HOST + ":" + REST_PORT + "/api/health",
            "http://" + HOST + ":" + REST_PORT + "/api/symbols",
            "http://" + HOST + ":" + REST_PORT + "/api/simulator/bots",
            "http://" + HOST + ":" + REST_PORT + "/api/simulator/status",
    };

    static RestResult runRestTest(int total, int concurrency) throws Exception {
        System.out.printf("┌─ Phase 3: REST API Throughput  port=%d  requests=%,d  concurrency=%d%n",
                REST_PORT, total, concurrency);
        System.out.println("│");

        HttpClient client = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        System.out.println("│  Warm-up (100 requests)...");
        for (int i = 0; i < 100; i++)
            client.send(HttpRequest.newBuilder().uri(URI.create(PUBLIC_ENDPOINTS[0])).GET().build(),
                    HttpResponse.BodyHandlers.discarding());

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed  = new AtomicInteger();
        ConcurrentLinkedQueue<Long> latMs = new ConcurrentLinkedQueue<>();
        Semaphore sem   = new Semaphore(concurrency);
        ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(total);

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < total; i++) {
            final int idx = i;
            sem.acquire();
            exec.submit(() -> {
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(PUBLIC_ENDPOINTS[idx % PUBLIC_ENDPOINTS.length]))
                            .timeout(Duration.ofSeconds(5)).GET().build();
                    long start = System.nanoTime();
                    HttpResponse<Void> r = client.send(req, HttpResponse.BodyHandlers.discarding());
                    latMs.add((System.nanoTime() - start) / 1_000_000);
                    if (r.statusCode() < 400) success.incrementAndGet();
                    else failed.incrementAndGet();
                    int done = success.get() + failed.get();
                    if (done % 1_000 == 0 && done > 0) {
                        double rps = done * 1000.0 / Math.max(System.currentTimeMillis() - t0, 1);
                        System.out.printf("│  %,5d / %,d  throughput: %.0f req/s%n", done, total, rps);
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    sem.release();
                    latch.countDown();
                }
            });
        }
        latch.await(120, TimeUnit.SECONDS);
        exec.shutdownNow();
        return new RestResult(total, success.get(), failed.get(), System.currentTimeMillis() - t0, latMs);
    }

    // ── Phase 4: Order Ack Latency ────────────────────────────────────────────
    // Registers N unique users, opens N parallel BOE sessions, sends M orders
    // per session sequentially (send → read ack → repeat), measures RTT in μs.

    static OrderAckLatencyResult runOrderAckLatencyTest(int sessions, int ordersPerSession)
            throws Exception {
        System.out.printf("┌─ Phase 4: Order Ack Latency  sessions=%d  orders/session=%d%n",
                sessions, ordersPerSession);
        System.out.println("│  (spec-correct New Order wire format: Side='1', OrdType='2')");
        System.out.println("│");

        // Register test users (prefix "Q" to avoid Phase 2 conflicts)
        HttpClient http = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        List<String[]> creds = new ArrayList<>(sessions);
        for (int i = 0; i < sessions; i++) {
            String user = String.format("Q%02d", i);
            String pass = "AckTest1!";
            creds.add(new String[]{user, pass});
            registerUser(http, user, pass);
        }
        System.out.printf("│  Registered %d test users%n", sessions);

        // Open sessions and send orders concurrently
        AtomicInteger ackOk     = new AtomicInteger();
        AtomicInteger ackFailed = new AtomicInteger();
        AtomicInteger connErr   = new AtomicInteger();
        ConcurrentLinkedQueue<Long> ackUs = new ConcurrentLinkedQueue<>();

        long t0 = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(sessions);
        ExecutorService exec  = Executors.newVirtualThreadPerTaskExecutor();

        for (int si = 0; si < sessions; si++) {
            final int sidx = si;
            final String[] cred = creds.get(si);
            exec.submit(() -> {
                try (Socket s = loginBoe(cred[0], cred[1], String.format("QS%02d", sidx))) {
                    if (s == null) { connErr.incrementAndGet(); return; }
                    s.setSoTimeout(5_000);
                    OutputStream out = s.getOutputStream();
                    InputStream  in  = s.getInputStream();

                    for (int oi = 0; oi < ordersPerSession; oi++) {
                        String clOrdID = String.format("Q%02d%07d", sidx, oi);
                        byte[] orderBytes = buildNewOrder(clOrdID, oi + 1);

                        long start = System.nanoTime();
                        out.write(orderBytes);
                        out.flush();
                        byte[] ack = readUntilOrderResponse(in);
                        long elapsed = (System.nanoTime() - start) / 1_000; // μs

                        if (ack != null && ack[4] == MSG_ORDER_ACK) {
                            ackOk.incrementAndGet();
                            ackUs.add(elapsed);
                        } else {
                            ackFailed.incrementAndGet();
                        }
                    }
                    int done = ackOk.get() + ackFailed.get();
                    System.out.printf("│  Session %02d done — total acked: %,d%n", sidx, done);
                } catch (Exception e) {
                    connErr.incrementAndGet();
                    LOGGER.warning("Session " + sidx + " error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(300, TimeUnit.SECONDS);
        exec.shutdownNow();

        return new OrderAckLatencyResult(sessions, ordersPerSession,
                ackOk.get(), ackFailed.get(), connErr.get(),
                System.currentTimeMillis() - t0, ackUs);
    }

    // ── Phase 5: Memory Stability ─────────────────────────────────────────────
    // Single session sends N orders sequentially; measures heap growth.
    // Orders use a price that will not match (buy at $0.01) so they rest in the book.
    // A bounded heap growth (< 100 bytes/order after GC) indicates no object leak.

    static MemoryStabilityResult runMemoryStabilityTest(int totalOrders) throws Exception {
        System.out.printf("┌─ Phase 5: Memory Stability  orders=%,d%n", totalOrders);
        System.out.println("│");

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        registerUser(http, "MSTAB1", "MemTest1!");
        System.out.println("│  User MSTAB1 registered");

        int ackOk     = 0;
        int ackFailed = 0;

        try (Socket s = loginBoe("MSTAB1", "MemTest1!", "MST")) {
            if (s == null) throw new IOException("Login failed for MSTAB1");
            s.setSoTimeout(10_000);
            OutputStream out = s.getOutputStream();
            InputStream  in  = s.getInputStream();

            // Warm-up: 100 orders to let JIT settle
            System.out.println("│  Warm-up (100 orders)...");
            for (int i = 0; i < 100; i++) {
                String clOrdID = String.format("WU%07d", i);
                out.write(buildNewOrder(clOrdID, i + 1));
                out.flush();
                byte[] ack = readUntilOrderResponse(in);
                if (ack != null && ack[4] == MSG_ORDER_ACK) ackOk++;
                else ackFailed++;
            }

            // Force GC and record baseline heap
            System.gc(); System.gc();
            Runtime rt = Runtime.getRuntime();
            long heapBefore = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            System.out.printf("│  Heap before: %,d MB%n", heapBefore);

            long t0 = System.currentTimeMillis();
            System.out.printf("│  Sending %,d orders...%n", totalOrders);

            for (int i = 0; i < totalOrders; i++) {
                String clOrdID = String.format("MS%07d", i);
                out.write(buildNewOrder(clOrdID, 100 + i + 1));
                out.flush();
                byte[] ack = readUntilOrderResponse(in);
                if (ack != null && ack[4] == MSG_ORDER_ACK) ackOk++;
                else ackFailed++;

                if ((i + 1) % 2_000 == 0)
                    System.out.printf("│  %,5d / %,d  ack: %,d%n", i + 1, totalOrders, ackOk);
            }
            long elapsedMs = System.currentTimeMillis() - t0;

            System.gc(); System.gc();
            long heapAfter = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);

            System.out.printf("│  Heap after : %,d MB (delta %+d MB)%n",
                    heapAfter, heapAfter - heapBefore);

            return new MemoryStabilityResult(totalOrders, ackOk, ackFailed,
                    heapBefore, heapAfter, elapsedMs);
        }
    }

    // ── Wire helpers ──────────────────────────────────────────────────────────

    /**
     * Builds a spec-correct New Order (0x38) — buy limit at $50.00.
     * Side='1' (Buy), OrdType='2' (Limit), Capacity='C' (Customer).
     */
    static byte[] buildNewOrder(String clOrdID, int seqNum) {
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID(clOrdID);
        msg.setSide((byte) '1');
        msg.setOrderQty(100);
        msg.setSequenceNumber(seqNum);
        msg.setMatchingUnit((byte) 0);
        msg.setSymbol("SPX");
        msg.setPrice(new BigDecimal("50.0000"));
        msg.setOrdType((byte) '2');
        msg.setCapacity((byte) 'C');
        return msg.toBytes();
    }

    /**
     * Reads one complete BOE wire message from the stream.
     * Frame: SOM(2) + MsgLen(2=LE, excludes SOM) + body(MsgLen-2).
     * Returns null on clean EOF; throws IOException on partial read.
     */
    static byte[] readBoeMessage(InputStream in) throws IOException {
        byte[] header = new byte[4];
        int pos = 0;
        while (pos < 4) {
            int n = in.read(header, pos, 4 - pos);
            if (n < 0) return null;
            pos += n;
        }
        int msgLen = (header[2] & 0xFF) | ((header[3] & 0xFF) << 8);
        if (msgLen < 2) throw new IOException("Malformed BOE frame: msgLen=" + msgLen);
        int bodyLen = msgLen - 2;
        byte[] full = new byte[4 + bodyLen];
        System.arraycopy(header, 0, full, 0, 4);
        int r = 0;
        while (r < bodyLen) {
            int n = in.read(full, 4 + r, bodyLen - r);
            if (n < 0) throw new EOFException("Truncated BOE message");
            r += n;
        }
        return full;
    }

    /**
     * Reads messages until an Order Ack (0x25) or Order Rejected (0x26) arrives.
     * Silently skips Server Heartbeats (0x01) and any other non-order messages.
     */
    static byte[] readUntilOrderResponse(InputStream in) throws IOException {
        while (true) {
            byte[] msg = readBoeMessage(in);
            if (msg == null) return null;
            byte type = msg[4];
            if (type == MSG_ORDER_ACK || type == MSG_ORDER_REJ) return msg;
        }
    }

    /**
     * Registers a user via the REST API. Accepts 201 (created) and 409 (already exists).
     */
    static void registerUser(HttpClient http, String user, String pass) throws Exception {
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", user, pass);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://" + HOST + ":" + REST_PORT + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();
        int status = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        if (status != 201 && status != 409)
            throw new IOException("Register failed for " + user + " — HTTP " + status);
    }

    /**
     * Opens a BOE session: connects, sends Login Request, reads Login Response.
     * Returns the open Socket on success (status='A'), null on login rejection.
     */
    static Socket loginBoe(String user, String pass, String subID) throws IOException {
        Socket s = new Socket(HOST, BOE_PORT);
        s.setSoTimeout(10_000);
        try {
            byte[] loginBytes = new LoginRequestMessage(user, pass, subID).toBytes();
            s.getOutputStream().write(loginBytes);
            s.getOutputStream().flush();
            byte[] resp = readAtLeast(s.getInputStream(), 11);
            if (resp != null && resp.length >= 11
                    && resp[4] == MSG_LOGIN_RESP
                    && resp[LOGIN_RESP_STATUS_OFFSET] == STATUS_ACCEPTED) {
                return s;
            }
            closeQuietly(s);
            return null;
        } catch (IOException e) {
            closeQuietly(s);
            throw e;
        }
    }

    // ── Report printers ───────────────────────────────────────────────────────

    static void printTcpCapacityReport(TcpCapacityResult r) {
        System.out.println("  ┌─ Phase 1: TCP Connection Capacity");
        System.out.printf("  │  Target               : %,d connections%n", r.target);
        System.out.printf("  │  Accepted by server   : %,d  (%.1f%%)%n", r.accepted, pct(r.accepted, r.target));
        System.out.printf("  │  Still open after %ds : %,d  (%.1f%%)%n",
                5, r.stillOpenAfterHold, pct(r.stillOpenAfterHold, r.accepted));
        System.out.printf("  │  Refused (limit/RST)  : %,d%n", r.refused);
        System.out.printf("  │  Errors               : %,d%n", r.errors);
        System.out.printf("  │  Total time           : %,dms%n", r.elapsedMs);
        if (!r.connectMs.isEmpty()) {
            long[] s = sorted(r.connectMs);
            System.out.printf("  │  Connect time p50/p99 : %dms / %dms%n", p(s, 50), p(s, 99));
        }
        boolean pass = r.accepted >= 500;
        System.out.printf("  │  Target ≥ 500 conns   : %s  (accepted=%,d)%n",
                pass ? "PASS" : "FAIL", r.accepted);
        System.out.println("  └──────────────────────────────────────────────────");
    }

    static void printLoginReport(LoginThroughputResult r) {
        double loginRate = r.loginOk * 1000.0 / Math.max(r.peakMs, 1);
        System.out.println("  ┌─ Phase 2: BOE Login Throughput");
        System.out.printf("  │  Users registered     : %,d%n", r.registered);
        System.out.printf("  │  Login OK             : %,d  (%.1f%%)%n", r.loginOk, pct(r.loginOk, r.registered));
        System.out.printf("  │  Login failed         : %,d%n", r.loginFailed);
        System.out.printf("  │  Connection errors    : %,d%n", r.connErrors);
        System.out.printf("  │  Peak concurrent sess : %,d%n", r.peakSessions);
        System.out.printf("  │  Time to peak         : %,dms%n", r.peakMs);
        System.out.printf("  │  Login rate           : %.0f logins/s%n", loginRate);
        if (!r.loginMs.isEmpty()) {
            long[] s = sorted(r.loginMs);
            System.out.printf("  │  Login RTT p50/p95/p99: %dms / %dms / %dms%n", p(s,50), p(s,95), p(s,99));
        }
        boolean pass = loginRate >= 200;
        System.out.printf("  │  Target ≥ 200 logins/s: %s  (actual=%.0f/s)%n",
                pass ? "PASS" : "FAIL", loginRate);
        System.out.println("  └──────────────────────────────────────────────────");
    }

    static void printRestReport(RestResult r) {
        double rps = r.total * 1000.0 / Math.max(r.elapsedMs, 1);
        System.out.println("  ┌─ Phase 3: REST API Throughput");
        System.out.printf("  │  Total requests       : %,d%n", r.total);
        System.out.printf("  │  Success (2xx/3xx)    : %,d  (%.1f%%)%n", r.success, pct(r.success, r.total));
        System.out.printf("  │  Failed               : %,d%n", r.failed);
        System.out.printf("  │  Total time           : %,dms%n", r.elapsedMs);
        System.out.printf("  │  Throughput           : %.0f req/s%n", rps);
        if (!r.latencies.isEmpty()) {
            long[] s = sorted(r.latencies);
            LongSummaryStatistics st = Arrays.stream(s).summaryStatistics();
            System.out.printf("  │  Latency min/p50      : %dms / %dms%n", st.getMin(), p(s, 50));
            System.out.printf("  │  Latency p95/p99      : %dms / %dms%n", p(s, 95), p(s, 99));
            System.out.printf("  │  Latency max          : %dms%n", st.getMax());
        }
        boolean pass = rps >= 800;
        System.out.printf("  │  Target ≥ 800 req/s   : %s  (actual=%.0f/s)%n",
                pass ? "PASS" : "FAIL", rps);
        System.out.println("  └──────────────────────────────────────────────────");
    }

    static void printOrderAckReport(OrderAckLatencyResult r) {
        int total = r.ackOk + r.ackFailed;
        System.out.println("  ┌─ Phase 4: Order Ack Latency");
        System.out.printf("  │  Sessions             : %d%n", r.sessions);
        System.out.printf("  │  Orders/session       : %d%n", r.ordersPerSession);
        System.out.printf("  │  Total orders sent    : %,d%n", total);
        System.out.printf("  │  Order Ack received   : %,d  (%.1f%%)%n", r.ackOk, pct(r.ackOk, total));
        System.out.printf("  │  Rejected / errors    : %,d / %,d%n", r.ackFailed, r.connErrors);
        System.out.printf("  │  Total time           : %,dms%n", r.elapsedMs);
        if (!r.ackUs.isEmpty()) {
            long[] s = sorted(r.ackUs);
            LongSummaryStatistics st = Arrays.stream(s).summaryStatistics();
            System.out.printf("  │  Latency min/p50      : %,dμs / %,dμs%n", st.getMin(), p(s, 50));
            System.out.printf("  │  Latency p95/p99      : %,dμs / %,dμs%n", p(s, 95), p(s, 99));
            System.out.printf("  │  Latency max          : %,dμs%n", st.getMax());
            boolean pass = p(s, 99) < 5_000; // < 5ms = 5000μs
            System.out.printf("  │  P99 < 5ms target     : %s  (p99=%,dμs)%n",
                    pass ? "PASS" : "FAIL", p(s, 99));
        }
        System.out.println("  └──────────────────────────────────────────────────");
    }

    static void printMemoryReport(MemoryStabilityResult r) {
        long deltaMB    = r.heapAfterMB - r.heapBeforeMB;
        long bytesPerOrder = r.totalOrders > 0 && deltaMB > 0
                ? (deltaMB * 1024 * 1024) / r.totalOrders : 0;
        double ackRate  = r.ackOk * 1000.0 / Math.max(r.elapsedMs, 1);
        System.out.println("  ┌─ Phase 5: Memory Stability");
        System.out.printf("  │  Total orders         : %,d%n", r.totalOrders);
        System.out.printf("  │  Ack OK / failed      : %,d / %,d%n", r.ackOk, r.ackFailed);
        System.out.printf("  │  Order throughput     : %.0f orders/s%n", ackRate);
        System.out.printf("  │  Heap before (post GC): %,d MB%n", r.heapBeforeMB);
        System.out.printf("  │  Heap after  (post GC): %,d MB%n", r.heapAfterMB);
        System.out.printf("  │  Heap delta           : %+d MB%n", deltaMB);
        System.out.printf("  │  Bytes/order (approx) : ~%,d B%n", bytesPerOrder);
        boolean pass = deltaMB < 200;
        System.out.printf("  │  Heap growth < 200 MB : %s  (delta=%+dMB)%n",
                pass ? "PASS" : "FAIL", deltaMB);
        System.out.println("  └──────────────────────────────────────────────────");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(LoadTestRunner.class.getName());

    static void printBanner() {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║        TitaniumBOE-Sim  —  Load Test Runner          ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
    }

    static void printSystemInfo(int tcpTarget) {
        System.out.println("System:");
        try {
            UnixOperatingSystemMXBean os = (UnixOperatingSystemMXBean)
                    ManagementFactory.getOperatingSystemMXBean();
            long maxFd  = os.getMaxFileDescriptorCount();
            long openFd = os.getOpenFileDescriptorCount();
            System.out.printf("  File descriptors : %,d open / %,d max%n", openFd, maxFd);
            if (maxFd < tcpTarget + 100)
                System.out.printf("  WARNING: maxFd (%,d) < target+100 (%,d) — run: ulimit -n 65535%n",
                        maxFd, tcpTarget + 100);
        } catch (Exception ignored) {
            System.out.println("  File descriptors : (unavailable on this platform)");
        }
        System.out.printf("  CPUs / JVM heap  : %d cores / %,dMB%n",
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / 1_048_576);
    }

    static byte[] readAtLeast(InputStream in, int minBytes) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[256];
        while (buf.size() < minBytes) {
            int n = in.read(tmp);
            if (n < 0) break;
            buf.write(tmp, 0, n);
        }
        return buf.toByteArray();
    }

    static void closeQuietly(Socket s) {
        try { s.close(); } catch (IOException ignored) {}
    }

    static int intArg(String[] args, String key, int def) {
        for (String a : args) if (a.startsWith(key + "=")) return Integer.parseInt(a.substring(key.length() + 1));
        return def;
    }

    static boolean hasFlag(String[] args, String flag) {
        for (String a : args) if (a.equals(flag)) return true;
        return false;
    }

    static long[] sorted(ConcurrentLinkedQueue<Long> q) {
        return q.stream().mapToLong(Long::longValue).sorted().toArray();
    }

    static long p(long[] sorted, int pct) {
        return sorted[(int) Math.min((long)(sorted.length * pct / 100.0), sorted.length - 1)];
    }

    static double pct(long n, long total) { return total == 0 ? 0 : n * 100.0 / total; }

    // ── Result records ────────────────────────────────────────────────────────

    record TcpCapacityResult(
            int target, int accepted, int refused, int errors, int stillOpenAfterHold,
            long elapsedMs, ConcurrentLinkedQueue<Long> connectMs
    ) {}

    record LoginThroughputResult(
            int target, int registered, int loginOk, int loginFailed, int connErrors,
            int peakSessions, long peakMs, ConcurrentLinkedQueue<Long> loginMs
    ) {}

    record RestResult(
            int total, int success, int failed, long elapsedMs,
            ConcurrentLinkedQueue<Long> latencies
    ) {}

    record OrderAckLatencyResult(
            int sessions, int ordersPerSession, int ackOk, int ackFailed, int connErrors,
            long elapsedMs, ConcurrentLinkedQueue<Long> ackUs
    ) {}

    record MemoryStabilityResult(
            int totalOrders, int ackOk, int ackFailed,
            long heapBeforeMB, long heapAfterMB, long elapsedMs
    ) {}
}
