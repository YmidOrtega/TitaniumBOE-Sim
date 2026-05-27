package com.boe.simulator.load;

import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.sun.management.UnixOperatingSystemMXBean;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TitaniumBOE-Sim — Load Test Runner
 *
 * Tests three dimensions independently:
 *   1. TCP Connection Capacity  — raw sockets to port 9090, no login
 *   2. BOE Login Throughput     — full login/hold/close cycle (needs unique users)
 *   3. REST API Throughput      — concurrent HTTP requests to port 8081
 *
 * HOW TO RUN:
 *   ulimit -n 65535
 *   mvn test-compile
 *   java -cp "target/test-classes:target/classes:$(mvn -q dependency:build-classpath \
 *       -DincludeScope=test -Dmdep.outputFile=/dev/stdout)" \
 *       com.boe.simulator.load.LoadTestRunner [options]
 *
 * OPTIONS:
 *   --tcp=N          raw TCP connections to open           (default: 1000)
 *   --logins=N       BOE login sessions (users created)    (default: 100)
 *   --rest=N         total REST requests                   (default: 5000)
 *   --concurrency=N  max parallel REST requests            (default: 200)
 *   --hold=N         seconds to hold TCP connections open  (default: 5)
 *   --skip-tcp       skip the raw TCP test
 *   --skip-login     skip the BOE login test
 *   --skip-rest      skip the REST test
 */
public class LoadTestRunner {

    static final String HOST     = "localhost";
    static final int    BOE_PORT = 9090;
    static final int    REST_PORT = 8081;

    // LoginResponse byte layout:
    //   [0-1] 0xBA 0xBA  [2-3] length(LE)  [4] type=0x07  [5] unit
    //   [6-9] seqNo(LE)  [10] status: 'A'=accepted 'R'=rejected 'S'=session-in-use
    static final int  LOGIN_RESP_STATUS_OFFSET = 10;
    static final byte LOGIN_RESP_MSG_TYPE      = 0x07;
    static final byte STATUS_ACCEPTED          = 'A';

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        int tcpTarget   = intArg(args, "--tcp",         1_000);
        int loginTarget = intArg(args, "--logins",        100);
        int restTotal   = intArg(args, "--rest",        5_000);
        int concurrency = intArg(args, "--concurrency",   200);
        int holdSecs    = intArg(args, "--hold",            5);
        boolean skipTcp   = hasFlag(args, "--skip-tcp");
        boolean skipLogin = hasFlag(args, "--skip-login");
        boolean skipRest  = hasFlag(args, "--skip-rest");

        printBanner();
        printSystemInfo(tcpTarget);
        System.out.println();

        TcpCapacityResult  tcpResult   = null;
        LoginThroughputResult loginResult = null;
        RestResult         restResult  = null;

        if (!skipTcp) {
            tcpResult = runTcpCapacityTest(tcpTarget, holdSecs);
            System.out.println();
        }
        if (!skipLogin) {
            loginResult = runLoginThroughputTest(loginTarget, holdSecs);
            System.out.println();
        }
        if (!skipRest) {
            restResult = runRestTest(restTotal, concurrency);
            System.out.println();
        }

        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  FINAL REPORT");
        System.out.println("═══════════════════════════════════════════════════════════");
        if (tcpResult   != null) { printTcpCapacityReport(tcpResult);   System.out.println(); }
        if (loginResult != null) { printLoginReport(loginResult);       System.out.println(); }
        if (restResult  != null) { printRestReport(restResult); }
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    // ── Phase 1: TCP Raw Connection Capacity ─────────────────────────────────
    // Opens N raw TCP sockets without logging in.
    // Verifies the server accepts and holds them open (no immediate reset).

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

        // Verify connections are still open (server didn't reset them)
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
    // Registers N unique test users via REST, then opens N BOE sessions simultaneously.
    // Measures login RTT, peak concurrent sessions, and login success rate.

    static LoginThroughputResult runLoginThroughputTest(int target, int holdSecs) throws Exception {
        System.out.printf("┌─ Phase 2: BOE Login Throughput  port=%d  sessions=%,d%n", BOE_PORT, target);
        System.out.println("│");

        // ── Step 1: Register unique test users ────────────────────────────────
        System.out.printf("│  Registering %,d test users via REST...%n", target);
        List<String[]> creds = new ArrayList<>(target);
        for (int i = 0; i < target; i++) {
            // 4-char username: A000-A999 → B000-B999 etc.
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
                    // 201 = created, 409 = already exists (OK)
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

        // ── Step 2: Concurrent BOE logins ─────────────────────────────────────
        System.out.printf("│  Opening %,d concurrent BOE sessions...%n", registered.get());

        AtomicInteger loginOk      = new AtomicInteger();
        AtomicInteger loginFailed  = new AtomicInteger();
        AtomicInteger connErrors   = new AtomicInteger();
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
                            && resp[4] == LOGIN_RESP_MSG_TYPE
                            && resp[LOGIN_RESP_STATUS_OFFSET] == STATUS_ACCEPTED) {
                        loginOk.incrementAndGet();
                        openSessions.add(s);
                    } else {
                        char status = (resp != null && resp.length > LOGIN_RESP_STATUS_OFFSET)
                                ? (char) resp[LOGIN_RESP_STATUS_OFFSET] : '?';
                        LOGGER.fine("Login rejected status=" + status + " user=" + creds.get(idx)[0]);
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
    // Public endpoints only — no auth required, no 401 noise.

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

        // Warm-up
        System.out.println("│  Warm-up (100 requests)...");
        for (int i = 0; i < 100; i++)
            client.send(HttpRequest.newBuilder().uri(URI.create(PUBLIC_ENDPOINTS[0])).GET().build(),
                    HttpResponse.BodyHandlers.discarding());

        AtomicInteger success  = new AtomicInteger();
        AtomicInteger failed   = new AtomicInteger();
        ConcurrentLinkedQueue<Long> latMs = new ConcurrentLinkedQueue<>();
        Semaphore sem    = new Semaphore(concurrency);
        ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch  = new CountDownLatch(total);

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
        System.out.println("  └──────────────────────────────────────────────────");
    }

    static void printLoginReport(LoginThroughputResult r) {
        System.out.println("  ┌─ Phase 2: BOE Login Throughput");
        System.out.printf("  │  Users registered     : %,d%n", r.registered);
        System.out.printf("  │  Login OK             : %,d  (%.1f%%)%n", r.loginOk, pct(r.loginOk, r.registered));
        System.out.printf("  │  Login failed         : %,d%n", r.loginFailed);
        System.out.printf("  │  Connection errors    : %,d%n", r.connErrors);
        System.out.printf("  │  Peak concurrent sess : %,d%n", r.peakSessions);
        System.out.printf("  │  Time to peak         : %,dms%n", r.peakMs);
        System.out.printf("  │  Login rate           : %.0f logins/s%n",
                r.loginOk * 1000.0 / Math.max(r.peakMs, 1));
        if (!r.loginMs.isEmpty()) {
            long[] s = sorted(r.loginMs);
            System.out.printf("  │  Login RTT p50/p95/p99: %dms / %dms / %dms%n", p(s,50), p(s,95), p(s,99));
        }
        System.out.println("  └──────────────────────────────────────────────────");
    }

    static void printRestReport(RestResult r) {
        System.out.println("  ┌─ Phase 3: REST API Throughput");
        System.out.printf("  │  Total requests       : %,d%n", r.total);
        System.out.printf("  │  Success (2xx/3xx)    : %,d  (%.1f%%)%n", r.success, pct(r.success, r.total));
        System.out.printf("  │  Failed               : %,d%n", r.failed);
        System.out.printf("  │  Total time           : %,dms%n", r.elapsedMs);
        System.out.printf("  │  Throughput           : %.0f req/s%n", r.total * 1000.0 / Math.max(r.elapsedMs, 1));
        if (!r.latencies.isEmpty()) {
            long[] s = sorted(r.latencies);
            LongSummaryStatistics st = Arrays.stream(s).summaryStatistics();
            System.out.printf("  │  Latency min/p50      : %dms / %dms%n", st.getMin(), p(s, 50));
            System.out.printf("  │  Latency p95/p99      : %dms / %dms%n", p(s, 95), p(s, 99));
            System.out.printf("  │  Latency max          : %dms%n", st.getMax());
        }
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
            long maxFd = os.getMaxFileDescriptorCount();
            long openFd = os.getOpenFileDescriptorCount();
            System.out.printf("  File descriptors : %,d open / %,d max%n", openFd, maxFd);
            if (maxFd < tcpTarget + 100)
                System.out.printf("  ⚠  maxFd (%,d) < target+100 (%,d) — run: ulimit -n 65535%n",
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
}
