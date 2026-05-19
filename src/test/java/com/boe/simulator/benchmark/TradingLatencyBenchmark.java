package com.boe.simulator.benchmark;

import com.boe.simulator.protocol.message.*;
import com.boe.simulator.protocol.serialization.BoeMessageSerializer;
import com.boe.simulator.server.CboeServer;
import com.boe.simulator.server.config.ServerConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mide la latencia real del protocolo BOE: tiempo desde que se envía un
 * NewOrder hasta recibir el OrderAcknowledgment/OrderRejected del servidor.
 *
 * Ejecutar con:
 *   mvn exec:java -Dexec.mainClass="com.boe.simulator.benchmark.TradingLatencyBenchmark" \
 *                 -Dexec.classpathScope="test" -q
 */
public class TradingLatencyBenchmark {

    // Puerto exclusivo para el benchmark (no interfiere con el servidor de producción en 8080)
    private static final int BENCH_PORT   = 19_870;
    private static final String HOST      = "localhost";
    private static final int MAX_CLIENTS  = 50;
    private static final int WARMUP       = 200;   // órdenes de calentamiento por cliente (descartar)
    private static final int MEASURE      = 1_000; // órdenes medidas por cliente

    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        silenceLogs();

        // DB exclusiva para el benchmark — evita conflicto con el servidor de producción
        String benchDbPath = Files.createTempDirectory("cboe-benchmark-").toAbsolutePath().toString();
        System.setProperty("cboe.db.path", benchDbPath);
        System.setProperty("DEMO_MODE", "true");

        ServerConfiguration config = ServerConfiguration.builder()
                .host("0.0.0.0")
                .port(BENCH_PORT)
                .maxConnections(10_000)
                .connectionTimeout(60_000)
                .heartbeatIntervalSeconds(3_600)  // desactivado durante el benchmark
                .heartbeatTimeoutSeconds(7_200)
                .rateLimitPerMinute(Integer.MAX_VALUE) // sin rate limit para el benchmark
                .build();
        config.setMarketSimulatorEnabled(false); // sin bots — benchmark limpio

        System.out.print("\n  Iniciando servidor embebido en puerto " + BENCH_PORT + " ... ");
        CboeServer server = new CboeServer(config);

        // Crear usuarios de benchmark B001-B050
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            try { server.getAuthService().createUser(String.format("B%03d", i), "BmP1234!"); }
            catch (Exception ignored) {} // ya existe si se ejecuta por segunda vez
        }

        server.start();
        Thread.sleep(1_500); // esperar a que el servidor esté listo
        System.out.println("listo.\n");

        int osThreadsIdle = countOsThreads();

        printBanner();

        // ── Escenarios ────────────────────────────────────────────────────────
        ScenarioResult r1 = runScenario("1 cliente   (latencia base)      ",  1);
        ScenarioResult r2 = runScenario("10 clientes (concurrencia media) ", 10);
        ScenarioResult r3 = runScenario("50 clientes (concurrencia alta)  ", MAX_CLIENTS);

        // ── Resultado ─────────────────────────────────────────────────────────
        printTable(osThreadsIdle, r1, r2, r3);

        server.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ejecución de un escenario
    // ─────────────────────────────────────────────────────────────────────────

    static ScenarioResult runScenario(String name, int numClients) throws Exception {
        System.out.printf("  Ejecutando: %s [%d clientes × %d órdenes + %d warmup]%n",
                name.strip(), numClients, MEASURE, WARMUP);
        System.out.flush();

        CountDownLatch readyGate = new CountDownLatch(numClients); // todos listos
        CountDownLatch startGate = new CountDownLatch(1);          // señal de inicio
        CountDownLatch doneLatch = new CountDownLatch(numClients);

        long[][] allLatencies = new long[numClients][];
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < numClients; i++) {
            final int ci = i;
            Thread.ofVirtual().name("bench-client-" + ci).start(() -> {
                String user    = String.format("B%03d", ci + 1);
                String session = String.format("S%03d", ci + 1);

                try (BenchmarkClient client = new BenchmarkClient(HOST, BENCH_PORT, user, "BmP1234!", session)) {
                    client.login();
                    client.warmup(WARMUP);     // calentamiento JIT
                    readyGate.countDown();     // avisar que estoy listo
                    startGate.await();         // esperar la señal de inicio simultáneo
                    allLatencies[ci] = client.measure(MEASURE);
                } catch (Exception e) {
                    errors.incrementAndGet();
                    allLatencies[ci] = new long[0];
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Esperar a que todos terminen el warmup antes de medir
        readyGate.await(60, TimeUnit.SECONDS);

        long tStart = System.nanoTime();
        startGate.countDown(); // ¡todos a la vez!
        boolean completed = doneLatch.await(120, TimeUnit.SECONDS);
        long tEnd = System.nanoTime();

        if (!completed) System.err.println("  ADVERTENCIA: el escenario agotó el tiempo de espera.");

        long[] merged = mergeAndSort(allLatencies);
        double durationSec = (tEnd - tStart) / 1_000_000_000.0;
        int osThreadsDuringLoad = countOsThreads();

        System.out.printf("  → %d órdenes en %.2fs  (%.0f ord/seg)  OS threads bajo carga: %d%n%n",
                merged.length, durationSec, merged.length / durationSec, osThreadsDuringLoad);

        return new ScenarioResult(name, numClients, merged, durationSec, errors.get(), osThreadsDuringLoad);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cliente de benchmark — socket raw, sin overhead del BoeClient asíncrono
    // ─────────────────────────────────────────────────────────────────────────

    static final class BenchmarkClient implements AutoCloseable {
        private final Socket           socket;
        private final InputStream      in;
        private final OutputStream     out;
        private final BoeMessageSerializer serializer = new BoeMessageSerializer();
        private final String           username;
        private final String           password;
        private final String           sessionSubId;
        private final AtomicInteger    seqNum = new AtomicInteger(1);

        BenchmarkClient(String host, int port, String user, String pass, String session) throws IOException {
            this.socket       = new Socket(host, port);
            this.socket.setTcpNoDelay(true); // desactivar el algoritmo de Nagle — crítico para trading
            this.socket.setReceiveBufferSize(65_536);
            this.socket.setSendBufferSize(65_536);
            this.in           = socket.getInputStream();
            this.out          = socket.getOutputStream();
            this.username     = user;
            this.password     = pass;
            this.sessionSubId = session;
        }

        void login() throws IOException {
            sendRaw(new LoginRequestMessage(username, password, sessionSubId).toBytes());
            // Leer hasta recibir LoginResponse
            BoeMessage resp;
            do { resp = serializer.deserialize(in); }
            while (resp.getMessageType() != BoeMessageFactory.LOGIN_RESPONSE);

            LoginResponseMessage lr = new LoginResponseMessage(resp.getData());
            if (!lr.isAccepted()) throw new IOException("Login rechazado para " + username + ": " + lr.getLoginResponseText());
        }

        void warmup(int count) throws IOException {
            for (int i = 0; i < count; i++) {
                sendOrder();
                readOrderResponse();
            }
        }

        long[] measure(int count) throws IOException {
            long[] latencies = new long[count];
            for (int i = 0; i < count; i++) {
                long t0 = System.nanoTime();
                sendOrder();
                readOrderResponse();
                latencies[i] = (System.nanoTime() - t0) / 1_000; // nanosegundos → microsegundos
            }
            return latencies;
        }

        private void sendOrder() throws IOException {
            NewOrderMessage order = new NewOrderMessage();
            order.setClOrdID(String.valueOf(seqNum.getAndIncrement())); // único por cliente
            order.setSide((byte) 1);                // Buy
            order.setOrderQty(100);
            order.setSymbol("AAPL");
            order.setPrice(new BigDecimal("1.00")); // precio muy bajo: no generará match (orden pasiva)
            order.setOrdType((byte) 2);             // Limit
            order.setCapacity((byte) 'C');
            sendRaw(order.toBytes());
        }

        private void readOrderResponse() throws IOException {
            // Leer mensajes hasta encontrar uno de tipo orden (ACK, Rejected, Executed, Cancelled)
            // Los heartbeats se ignoran (no deberían llegar — heartbeat desactivado en config del benchmark)
            while (true) {
                BoeMessage msg = serializer.deserialize(in);
                byte type = msg.getMessageType();
                if (type == BoeMessageFactory.ORDER_ACKNOWLEDGMENT ||
                    type == BoeMessageFactory.ORDER_REJECTED        ||
                    type == BoeMessageFactory.ORDER_EXECUTED        ||
                    type == BoeMessageFactory.ORDER_CANCELLED) {
                    return;
                }
                // cualquier otro mensaje (ej: heartbeat inesperado): continuar leyendo
            }
        }

        private void sendRaw(byte[] bytes) throws IOException {
            out.write(bytes);
            out.flush();
        }

        void logout() {
            try {
                sendRaw(new LogoutRequestMessage().toBytes());
            } catch (IOException ignored) {}
        }

        @Override
        public void close() {
            logout();
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Estadísticas
    // ─────────────────────────────────────────────────────────────────────────

    static long[] mergeAndSort(long[][] arrays) {
        int total = Arrays.stream(arrays).filter(Objects::nonNull).mapToInt(a -> a.length).sum();
        long[] merged = new long[total];
        int pos = 0;
        for (long[] a : arrays) {
            if (a != null && a.length > 0) {
                System.arraycopy(a, 0, merged, pos, a.length);
                pos += a.length;
            }
        }
        Arrays.sort(merged);
        return merged;
    }

    record ScenarioResult(
            String name, int clients, long[] sorted,
            double durationSec, int errors, int osThreads
    ) {
        long p(double pct) {
            if (sorted.length == 0) return -1;
            return sorted[Math.min((int)(sorted.length * pct / 100.0), sorted.length - 1)];
        }
        long min()         { return sorted.length > 0 ? sorted[0] : -1; }
        long max()         { return sorted.length > 0 ? sorted[sorted.length - 1] : -1; }
        double throughput(){ return sorted.length / durationSec; }
        int totalOrders()  { return sorted.length; }
    }

    static int countOsThreads() {
        return ManagementFactory.getThreadMXBean().getThreadCount();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Salida
    // ─────────────────────────────────────────────────────────────────────────

    static void printBanner() {
        System.out.println("  ╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("  ║   CBOE BOE Protocol — Benchmark de Latencia (Java 21 VThreads)  ║");
        System.out.println("  ║   Métrica: RTT de NewOrder → OrderAck/Reject  (microsegundos)   ║");
        System.out.println("  ╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    static void printTable(int osThreadsIdle, ScenarioResult... results) {
        String sep  = "─";
        String col  = "│";

        System.out.printf("  OS threads en reposo: %d  (con Virtual Threads, este número no sube%n" +
                          "  linealmente con las conexiones — esa es la prueba)%n%n", osThreadsIdle);

        // Cabecera
        String header = String.format("  %-38s %s %5s %s %5s %s %5s %s %5s %s %5s %s %5s %s %8s %s %7s %s %5s",
                "Escenario", col,
                "Ords", col, "min", col, "p50", col, "p95", col, "p99", col, "p999", col,
                "ord/seg", col, "OSthrd", col, "Errs");
        System.out.println(header);

        // Separador
        System.out.println("  " + sep.repeat(38) + "─┼─" + sep.repeat(5) + "─┼─" + sep.repeat(5) + "─┼─"
                + sep.repeat(5) + "─┼─" + sep.repeat(5) + "─┼─" + sep.repeat(5) + "─┼─"
                + sep.repeat(5) + "─┼─" + sep.repeat(8) + "─┼─" + sep.repeat(6) + "─┼─" + sep.repeat(5));

        // Filas
        for (ScenarioResult r : results) {
            System.out.printf("  %-38s %s %5d %s %5d %s %5d %s %5d %s %5d %s %5d %s %8.0f %s %6d %s %5d%n",
                    r.name(), col,
                    r.totalOrders(), col,
                    r.min(), col,
                    r.p(50), col,
                    r.p(95), col,
                    r.p(99), col,
                    r.p(99.9), col,
                    r.throughput(), col,
                    r.osThreads(), col,
                    r.errors());
        }

        System.out.println();
        System.out.println("  Todas las latencias en microsegundos (μs). 1000 μs = 1 ms.");
        System.out.println();
        System.out.println("  Cómo interpretar:");
        System.out.printf ("    p50  → latencia mediana%n");
        System.out.printf ("    p99  → el 99%% de las órdenes completadas en este tiempo o menos%n");
        System.out.printf ("    p999 → tail latency: la peor orden en 1 de cada 1000%n");
        System.out.printf ("    OSthrd → threads del SO bajo carga: con VThreads no crece con el número de clientes%n");
        System.out.println();
        System.out.println("  Para comparar antes/después de los cambios de Java 21:");
        System.out.println("    git stash && mvn exec:java ... > before.txt");
        System.out.println("    git stash pop && mvn exec:java ... > after.txt");
        System.out.println("    diff before.txt after.txt");
        System.out.println();
    }

    static void silenceLogs() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.OFF);
        for (var h : root.getHandlers()) h.setLevel(Level.OFF);
        // Silenciar SLF4J/Javalin via propiedad de sistema
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");
    }
}
