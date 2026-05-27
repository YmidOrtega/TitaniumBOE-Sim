# Arquitectura Técnica — TitaniumBOE-Sim

**Versión del documento:** 2.0  
**Protocolo de referencia:** Cboe Titanium U.S. Options BOE Specification v2.11.90  
**Runtime:** Java 21 · Maven 3.9 · Virtual Threads

---

## 1. Contexto del Problema

El protocolo **Binary Order Entry (BOE)** de Cboe es un protocolo binario de baja latencia usado para enviar órdenes de opciones en exchanges institucionales. Su complejidad radica en:

- **Formato binario Little Endian** — cada campo tiene un offset fijo, tamaño exacto y tipo propio (`Binary`, `Alpha`, `Text`, `Binary Price` con 4 decimales implícitos, `DateTime` en nanosegundos).
- **Campos opcionales controlados por bitfields** — la presencia de cada campo se negocia por sesión mediante un *Return Bitfields Parameter Group* en el Login Request. Cada mensaje es auto-descriptivo.
- **Múltiples versiones del protocolo** — cambiar de BOEv2.11 a BOEv3.x requiere reescribir manualmente todo el parser, la serialización y las pruebas.
- **Flujo de sesión estricto** — Login obligatorio antes de cualquier orden; secuencias por matching unit; heartbeats en ambas direcciones.

TitaniumBOE-Sim resuelve esto en Java 21 con una implementación completa y testeable del protocolo, un motor de matching real, y una capa de persistencia, REST API y dashboard todo en un único JAR.

---

## 2. Visión General del Sistema

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         Clientes Externos                                 │
│  Browser ─────────── REST / WebSocket ─────────── CLI / BOE Client SDK  │
└────────────┬─────────────────────────────────────────────────────┬───────┘
             │ HTTP/WS (puerto 9091)                               │ TCP (puerto 8081)
             ▼                                                     ▼
┌────────────────────────────┐              ┌────────────────────────────────┐
│      RestApiServer         │              │       CboeServer               │
│  Javalin 6.7 + Scalar UI   │              │  ServerSocketChannel           │
│  ┌──────────┬───────────┐  │              │  VirtualThread por conexión     │
│  │ REST     │ WebSocket │  │              │  ┌──────────────────────────┐  │
│  │ endpoints│ /ws/feed  │  │              │  │ ClientConnectionHandler  │  │
│  └──────────┴───────────┘  │              │  │  - Login / Logout / HB   │  │
└────────────┬───────────────┘              │  │  - New/Cancel/Modify Ord │  │
             │                              │  └────────────┬─────────────┘  │
             │                              └───────────────┼────────────────┘
             │                                              │
             └──────────────────────┬───────────────────────┘
                                    │
                    ┌───────────────▼──────────────────┐
                    │           OrderManager            │
                    │  Valida, enruta, responde         │
                    └───────────────┬──────────────────┘
                                    │
               ┌────────────────────┼─────────────────────┐
               │                    │                       │
    ┌──────────▼───────┐  ┌─────────▼────────┐  ┌─────────▼──────────┐
    │  MatchingEngine   │  │  OrderRepository │  │  TradeRepository   │
    │  synchronized     │  │  (RocksDB async) │  │  (RocksDB async)   │
    │  por símbolo      │  └──────────────────┘  └────────────────────┘
    │  ┌────────────┐  │
    │  │ OrderBook  │  │  ── TreeMap<BigDecimal, List<Order>>
    │  │ (por sym.) │  │  ── precio-tiempo (FIFO por nivel)
    │  └────────────┘  │
    └──────────────────┘
               │
    ┌──────────▼──────────┐
    │   BotManager        │
    │  MarketMaker        │
    │  TrendFollower      │
    │  RandomTrader       │
    └─────────────────────┘
```

---

## 3. Stack Tecnológico

| Capa | Tecnología | Justificación |
|------|-----------|---------------|
| Runtime | Java 21, Virtual Threads | Un VThread por conexión TCP sin overhead de OS threads; >500 conexiones concurrentes |
| Build | Maven 3.9, frontend-maven-plugin | Compila Astro y empaqueta el frontend en el JAR — un solo artefacto deployable |
| Servidor BOE | NIO ServerSocketChannel | No bloqueante en el accept; cada cliente corre en su propio VThread |
| REST / WebSocket | Javalin 6.7 | Ligero, sin reflection en el hot path, compatible con VThreads |
| Frontend | Astro 5 + Tailwind CSS | Generación estática en build time; servido desde classpath |
| Persistencia | RocksDB 9.11 | Escritura asíncrona (write-behind queue), alta throughput para órdenes |
| Seguridad | JBCrypt | Hash de contraseñas con work factor configurable |
| Testing | JUnit 5 + Awaitility | 319 tests; pruebas de wire format contra la spec |

---

## 4. Capa de Protocolo BOE

### 4.1 Jerarquía de Mensajes (Java 21 Sealed Classes)

```
BoeProtocolMessage (sealed abstract)
├── SessionMessage (sealed abstract)
│   ├── LoginRequestMessage       (0x37) ← Member → Cboe
│   ├── LoginResponseMessage      (0x24) ← Cboe → Member
│   ├── LogoutRequestMessage      (0x02) ← Member → Cboe
│   ├── LogoutResponseMessage     (0x08) ← Cboe → Member
│   ├── ClientHeartbeatMessage    (0x03) ← Member → Cboe
│   ├── ServerHeartbeatMessage    (0x09) ← Cboe → Member
│   └── ReplayCompleteMessage     (0x13) ← Cboe → Member
│
└── ApplicationMessage (sealed abstract)
    ├── NewOrderMessage           (0x38) ← Member → Cboe
    ├── CancelOrderMessage        (0x39) ← Member → Cboe
    ├── ModifyOrderMessage        (0x3A) ← Member → Cboe
    ├── OrderAcknowledgmentMessage(0x25) ← Cboe → Member
    ├── OrderRejectedMessage      (0x26) ← Cboe → Member
    ├── OrderModifiedMessage      (0x27) ← Cboe → Member
    ├── UserModifyRejectedMessage (0x29) ← Cboe → Member
    ├── OrderCancelledMessage     (0x2A) ← Cboe → Member
    ├── CancelRejectedMessage     (0x2B) ← Cboe → Member
    ├── OrderExecutedMessage      (0x2C) ← Cboe → Member
    └── OrderRestatedMessage      (0x28) ← Cboe → Member
```

Las sealed classes permiten **pattern matching exhaustivo** en el dispatcher:

```java
switch (message) {
    case NewOrderMessage m    -> handleNewOrder(m);
    case CancelOrderMessage m -> handleCancelOrder(m);
    case ModifyOrderMessage m -> handleModifyOrder(m);
    // El compilador verifica exhaustividad
}
```

### 4.2 Header de Mensajes (10 bytes — todos los mensajes)

```
Offset  Len  Campo            Notas
------  ---  ---------------  -------------------------------------------------
0       2    StartOfMessage   Siempre 0xBA 0xBA
2       2    MessageLength    Bytes del mensaje incluyendo este campo, sin SOM
4       1    MessageType      Identificador 1 byte del tipo de mensaje
5       1    MatchingUnit     Siempre 0 en mensajes inbound
6       4    SequenceNumber   Contador LE 32-bit por stream de aplicación
```

**Fórmula:** `MessageLength = total_bytes - 2`  
**Endianness:** Little Endian en todos los campos binarios (Intel x86 byte order)

### 4.3 Tipos de Datos del Protocolo

| Tipo | Tamaño | Representación | Ejemplo |
|------|--------|----------------|---------|
| `Binary` | variable | LE unsigned | `64 00 00 00` = 100 |
| `Binary Price` | 8 bytes | LE signed, 4 decimales implícitos | `08 E2 01 00...` = 12.34 (= 123400 / 10000) |
| `Short Binary Price` | 4 bytes | LE signed, 4 decimales implícitos | `0C 30 00 00` = 1.23 |
| `DateTime` | 8 bytes | Nanosegundos desde Unix epoch | |
| `Text` | variable | ASCII, relleno con NUL (0x00) | `"ABC\x00\x00"` |
| `Alpha` | variable | ASCII, relleno con NUL (0x00) | `"MSFT\x00\x00\x00\x00"` |

### 4.4 Campos Opcionales y Bitfields

Los mensajes como New Order y Modify Order incluyen campos opcionales controlados por bytes de bitfield:

```
[Fixed Header (10B)] [Campo fijo...] [NumBitfields (1B)] [Bitfield₁] ... [BitfieldN] [Campo_opt₁] [Campo_opt₂] ...
```

**Regla:** los campos opcionales aparecen en orden — primer bitfield primero, bit menos significativo primero dentro de cada byte.

**Negociación en login:** el cliente declara via el *Return Bitfields Parameter Group* (`0x81`) qué campos quiere recibir en cada tipo de mensaje de respuesta. El servidor respeta esa negociación durante toda la sesión y cada mensaje de respuesta es auto-descriptivo (incluye sus propios bitfield bytes).

```java
// Clase ReturnBitfields encapsula la negociación por tipo de mensaje
public class ReturnBitfields {
    private final Map<Byte, byte[]> masksByMessageType;

    public byte[] maskFor(byte messageType) { ... }
}
```

### 4.5 Binary Price: Implementación

```java
public record BinaryPrice(long rawValue) {
    private static final long SCALE = 10_000L;  // 4 decimales implícitos

    public BigDecimal toPrice() {
        return BigDecimal.valueOf(rawValue, 4);  // scale=4
    }

    public static BinaryPrice fromPrice(BigDecimal price) {
        long raw = price.scaleByPowerOfTen(4).longValueExact();
        return new BinaryPrice(raw);
    }
}
```

`12.3400` → rawValue `123400` → wire `08 E2 01 00 00 00 00 00`

---

## 5. Capa del Servidor BOE

### 5.1 Modelo de Concurrencia

```
CboeServer
  └── ServerSocketChannel (non-blocking accept)
        └── Por cada conexión aceptada:
              Thread.ofVirtual().start(() -> {
                  ClientConnectionHandler handler = new ClientConnectionHandler(socket, ...);
                  handler.run();  // Bucle bloqueante — el VThread gestiona el bloqueo
              })
```

Java 21 Virtual Threads permiten el modelo de programación más simple (blocking I/O) sin el overhead de OS threads reales. Cada conexión tiene su propio VThread; >500 conexiones simultáneas con footprint mínimo.

### 5.2 ClientConnectionHandler — Flujo de Sesión

```
                     ┌─ Conexión TCP establecida ─┐
                     │                            │
                     ▼                            │
              [Lee LoginRequest]                  │
                     │                            │
          ┌──────────▼────────────┐               │
          │  AuthenticationService│               │
          │  - Busca usuario BD   │               │
          │  - BCrypt.checkpw()   │               │
          └──────────┬────────────┘               │
                     │ OK / FAIL                  │
          ┌──────────▼────────────┐               │
          │  Envía LoginResponse  │               │
          │  Negocia ReturnBFlds  │               │
          └──────────┬────────────┘               │
                     │                            │
         ┌───────────▼─────────────┐              │
         │   Bucle de mensajes     │◄─────────────┘
         │   (HeartbeatMonitor)    │
         │  switch(message) {      │
         │    NewOrder    → ...    │
         │    CancelOrder → ...    │
         │    ModifyOrder → ...    │
         │    ClientHB    → ...    │
         │    LogoutReq   → ...    │
         │  }                      │
         └───────────┬─────────────┘
                     │
              [LogoutRequest / timeout]
              Envía Logout → cierra socket
```

### 5.3 Secuencias y Heartbeats

- Mensajes de sesión (Login, Logout, Heartbeat): `SequenceNumber = 0` siempre
- Mensajes de aplicación Member→Cboe: stream único por sesión, incrementa el cliente
- Mensajes de aplicación Cboe→Member: por matching unit (independiente por unidad)
- Heartbeat: si no llega ningún dato en **1 segundo**, el servidor envía `ServerHeartbeat`
- Timeout: **5 segundos** sin dato → el servidor envía `Logout` y cierra conexión

---

## 6. Motor de Matching

### 6.1 Estructura de Datos — OrderBook

```java
// Un OrderBook por símbolo, creado bajo demanda
class OrderBook {
    private final TreeMap<BigDecimal, List<Order>> bids;  // descendente (mejor precio primero)
    private final TreeMap<BigDecimal, List<Order>> asks;  // ascendente  (mejor precio primero)
}
```

Dentro de cada nivel de precio: las órdenes se mantienen en una `List<Order>` en orden de llegada — **FIFO (price-time priority)**.

### 6.2 Ciclo de Matching — processOrder

```
processOrder(Order incoming)
    │
    ├── ¿Puede cruzar? (canMatch)
    │    ├── MARKET → siempre sí
    │    ├── BUY LIMIT → sí si price >= bestAsk
    │    └── SELL LIMIT → sí si price <= bestBid
    │
    ├── SÍ → executeMatching (loop):
    │         ├── Obtiene la mejor contrapartida (FIFO en ese nivel)
    │         ├── Verifica self-trade (misma username → cancela pasiva)
    │         ├── fillQty = min(aggressiveLeavesQty, passiveLeavesQty)
    │         ├── execPrice = precio de la pasiva (price-time priority)
    │         ├── Crea Trade, actualiza leavesQty en ambas órdenes
    │         ├── Si pasiva completada → removeOrder(passive)
    │         └── Notifica listeners (WebSocket broadcast)
    │
    └── Si leavesQty > 0 y order.isLive() → addOrder(book)
```

### 6.3 Sincronización

```java
// Lock por símbolo — no hay contención entre AAPL y SPX
private final Map<String, Object> symbolLocks = new ConcurrentHashMap<>();

public List<Trade> processOrder(Order order) {
    Object lock = symbolLocks.computeIfAbsent(order.getSymbol(), k -> new Object());
    synchronized (lock) {
        // ... matching completo bajo el lock del símbolo
    }
}
```

### 6.4 Modify Order — Lógica Spec (p.77)

El Modify Order requiere un orden específico para mantener la integridad del OrderBook:

```
1. removeOrder(book)        // ANTES de cambiar el precio (TreeMap key)
2. Calcular delta:
     delta        = newOrderQty - order.getEffectiveOrderQty()
     newLeavesQty = order.getLeavesQty() + delta
3. Si newLeavesQty <= 0 → order.cancel() → retorna []
4. order.modify(newClOrdID, newPrice, newOrdType, newOrderQty, newLeavesQty)
5. Intentar matching al nuevo precio
6. Si leavesQty > 0 → addOrder(book) al nuevo precio
```

**Por qué este orden:** `OrderBook` usa `TreeMap<BigDecimal, List<Order>>` donde el precio es la clave. Si se actualizara el precio *antes* de remover la orden, el `removeOrder` buscaría en el nivel de precio *nuevo* y no encontraría la orden (todavía está en el nivel *viejo*).

### 6.5 Shadow Fields en Order (soporte a modify sin romper TreeMap)

```java
public class Order {
    private final BigDecimal price;      // precio original (inmutable)

    // Mutable overrides aplicados por Modify Order
    private volatile BigDecimal modifiedPrice;
    private volatile String modifiedClOrdID;
    private volatile OrdType modifiedOrdType;
    private volatile int modifiedOrderQty;  // 0 = no modificado

    public BigDecimal getPrice() {
        return modifiedPrice != null ? modifiedPrice : price;
    }
    public int getEffectiveOrderQty() {
        return modifiedOrderQty > 0 ? modifiedOrderQty : orderQty;
    }
}
```

---

## 7. Ciclo de Vida de una Orden

```
                          [New Order recibida]
                                  │
                    ┌─────────────▼────────────┐
                    │      OrderValidator       │
                    │  - ClOrdID único?         │
                    │  - Side, Symbol, Qty OK?  │
                    │  - Precio para LIMIT?     │
                    └──────┬──────────┬─────────┘
                      OK   │          │ Error
                           │          ▼
                           │   OrderRejected (0x26)
                           │
              ┌────────────▼────────────────┐
              │  order.state = PENDING_NEW   │
              │  orderRepository.saveAsync() │
              └────────────┬────────────────┘
                           │
              ┌────────────▼────────────────┐
              │    matchingEngine.process()  │
              └────────────┬────────────────┘
                           │
               ┌───────────▼───────────────┐
               │  order.acknowledge()       │
               │  order.state = LIVE        │
               │  Envía OrderAck (0x25)     │
               └───────────┬───────────────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
      FILL parcial    FILL completo   Sin fill
           │               │               │
   PARTIALLY_FILLED    FILLED           LIVE
     ┌─────┘               │            ┌──┘
     │   Envía             │            │  Permanece en
     │   OrderExec         │            │  OrderBook
     │   (0x2C)       OrderExec         │
     │                (0x2C)            │
     │                                  │
     └──── Modify/Cancel posibles ──────┘
                           │
              [Cancel] → OrderCancelled (0x2A)
              [Modify] → OrderModified  (0x27)
```

**Estados de OrderState:**

| Estado | isActive() | isCancellable() | isLive() |
|--------|-----------|-----------------|---------|
| `PENDING_NEW` | true | false | false |
| `LIVE` | true | true | true |
| `PARTIALLY_FILLED` | true | true | true |
| `FILLED` | false | false | false |
| `CANCELLED` | false | false | false |
| `REJECTED` | false | false | false |
| `EXPIRED` | false | false | false |

---

## 8. Persistencia — RocksDB

### 8.1 Arquitectura Write-Behind

```
OrderManager / TradeService
       │
       │  orderRepository.saveAsync(order)
       │
       ▼
┌──────────────────────┐
│  OrderRepository     │
│  LinkedBlockingQueue │  ← hot path no bloqueado
│  (write-behind)      │
└──────────────────────┘
       │ hilo background
       ▼
┌──────────────────────┐
│  RocksDBManager      │
│  - orders CF         │
│  - trades CF         │
│  - sessions CF       │
│  - users CF          │
│  - audit CF          │
│  - statistics CF     │
└──────────────────────┘
```

**Ventaja:** el matching engine y el dispatcher nunca esperan I/O de disco. La confirmación va al cliente (OrderAck) antes de que la persistencia se complete.

### 8.2 Column Families

| Column Family | Clave | Valor |
|---------------|-------|-------|
| `orders` | `clOrdID` (String) | `Order` serializado (Jackson CBOR) |
| `trades` | `tradeId` (Long → bytes) | `Trade` serializado |
| `sessions` | `username+subID` | `PersistedSession` |
| `users` | `username` | `PersistedUser` (password BCrypt hash) |
| `audit` | timestamp + UUID | `AuditEvent` |
| `statistics` | `stats` | `PersistedStatistics` |

---

## 9. REST API y WebSocket

### 9.1 Endpoints

**Puerto:** 9091

| Grupo | Método | Path | Auth | Descripción |
|-------|--------|------|------|-------------|
| Sistema | GET | `/api/health` | No | Estado del servidor |
| Símbolos | GET | `/api/symbols` | No | Símbolos disponibles |
| Símbolos | GET | `/api/symbols/{symbol}` | No | Market data de un símbolo |
| Auth | POST | `/api/auth/register` | No | Registrar usuario |
| Auth | POST | `/api/auth/login` | Basic | Validar credenciales |
| Órdenes | POST | `/api/orders` | Basic | Crear orden |
| Órdenes | GET | `/api/orders/active` | Basic | Órdenes activas del usuario |
| Órdenes | GET | `/api/orders/{clOrdID}` | Basic | Detalle de una orden |
| Órdenes | DELETE | `/api/orders/{clOrdID}` | Basic | Cancelar orden |
| Posiciones | GET | `/api/positions` | Basic | Posiciones del usuario |
| Posiciones | GET | `/api/positions/{symbol}` | Basic | Posición por símbolo |
| Trades | GET | `/api/trades/my` | Basic | Mis trades |
| Trades | GET | `/api/trades/recent` | Basic | Trades recientes (mercado) |
| Trades | GET | `/api/trades/symbol/{symbol}` | Basic | Trades por símbolo |
| Simulador | GET | `/api/simulator/status` | No | Estado del simulador |
| Simulador | GET | `/api/simulator/bots` | No | Lista de bots |
| Simulador | POST | `/api/simulator/bots/{id}/start` | Basic | Iniciar bot |
| Simulador | POST | `/api/simulator/bots/{id}/stop` | Basic | Detener bot |
| Docs | GET | `/api/docs` | No | Scalar UI (OpenAPI) |

### 9.2 WebSocket Feed

**Endpoint:** `ws://localhost:9091/ws/feed`

Eventos emitidos en tiempo real:

```json
// Trade ejecutado
{ "type": "TRADE", "symbol": "AAPL", "price": 150.50, "qty": 100, "timestamp": 1234567890 }

// Order book update
{ "type": "ORDER_BOOK", "symbol": "AAPL", "bids": [...], "asks": [...] }

// Cambio de estado de orden
{ "type": "ORDER_STATUS", "clOrdID": "ABC123", "status": "FILLED" }
```

### 9.3 Autenticación

**HTTP Basic Auth** en todos los endpoints autenticados:

```
Authorization: Basic base64(username:password)
```

Credenciales demo (modo `DEMO_MODE=true`):
- `TRD1` / `Pass1234!`
- `TRD2` / `Pass5678!`

---

## 10. Bots de Trading

Tres estrategias ejecutan en hilos virtuales paralelos:

| Bot | Estrategia | Descripción |
|-----|-----------|-------------|
| `MarketMakerStrategy` | Provee liquidez | Coloca bids y asks dentro del spread; ajusta según P&L |
| `TrendFollowerStrategy` | Sigue tendencia | Detecta momentum de precio; compra en uptrend, vende en downtrend |
| `RandomTraderStrategy` | Ruido aleatorio | Órdenes aleatorias para simular mercado activo |

Cada bot opera via la REST API interna, generando actividad continua en el matching engine. En `DEMO_MODE=true` los tres arrancan automáticamente.

---

## 11. Decisiones de Diseño Clave

### 11.1 Java 21 Virtual Threads

**Decisión:** un VThread por conexión TCP, I/O bloqueante.  
**Alternativa considerada:** NIO non-blocking con Selector loop (Netty style).  
**Por qué VThreads:** código más simple y legible, sin callback hell, sin CompletableFuture chains. Java 21 garantiza que el scheduler mapea VThreads a OS threads eficientemente. En pruebas: >500 conexiones simultáneas con latencia P99 < 5ms.

### 11.2 Sealed Class Hierarchy + Pattern Matching

**Decisión:** `BoeProtocolMessage` como sealed class con dos ramas: `SessionMessage` y `ApplicationMessage`.  
**Por qué:** el compilador verifica exhaustividad del switch. Agregar un nuevo tipo de mensaje requiere actualizar el switch — la omisión es un error de compilación, no un bug silencioso en runtime.

### 11.3 Synchronization por Símbolo en MatchingEngine

**Decisión:** `ConcurrentHashMap<String, Object>` de locks, uno por símbolo.  
**Por qué:** `AAPL` y `SPX` no comparten estado; no tiene sentido serializar su matching. Esto permite matching paralelo entre símbolos con zero contención entre ellos.

### 11.4 TreeMap para OrderBook

**Decisión:** `TreeMap<BigDecimal, List<Order>>` ordenado por precio.  
**Por qué:** `getBestBid()` = `firstKey()` y `getBestAsk()` = `firstKey()` son O(log n). Insertar un nivel nuevo también es O(log n). La alternativa (PriorityQueue) no permite acceso eficiente a un nivel ya existente.

### 11.5 Write-Behind Queue para Persistencia

**Decisión:** `LinkedBlockingQueue` consumida por un thread background hacia RocksDB.  
**Por qué:** el hot path (matching + ACK al cliente) nunca espera disco. El trade-off: en un crash abrupto se pueden perder las últimas escrituras en cola. Aceptable en un simulador; en producción se usaría WAL + fsync.

### 11.6 Shadow Fields en Order para Modify

**Decisión:** campos `modifiedPrice`, `modifiedClOrdID`, etc. en vez de mutar los campos `final`.  
**Por qué:** los campos originales son `final` para garantizar que el `OrderBook` puede remover la orden al precio correcto. El getter efectivo devuelve el campo modificado si existe, el original si no. Esto hace el Modify Order seguro respecto al TreeMap.

---

## 12. Testing

### 12.1 Cobertura

319 tests distribuidos en 30 clases:

| Área | Tests | Enfoque |
|------|-------|---------|
| Wire format (protocol) | ~150 | Parseo byte a byte contra la spec |
| Matching engine | ~20 | Price-time priority, self-trade, Modify delta logic |
| Session layer | ~40 | Login, logout, heartbeat, secuencias |
| Order management | ~30 | Validación, ciclo de vida, estados |
| Persistencia | ~15 | RocksDB CRUD, serialización |
| REST / auth | ~20 | Endpoints, autenticación, errores |
| Load test | manual | `LoadTestRunner` (5 fases, no en el suite de CI) |

### 12.2 Load Test Runner (Fase 10)

```bash
mvn test-compile
java -cp "target/test-classes:target/classes:$(mvn -q dependency:build-classpath \
    -DincludeScope=test -Dmdep.outputFile=/dev/stdout)" \
    com.boe.simulator.load.LoadTestRunner \
    --tcp=500 --logins=100 --rest=5000 --ack-sessions=10 --ack-orders=100
```

| Fase | Métrica | Criterio |
|------|---------|---------|
| 1 — TCP Capacity | Conexiones aceptadas | ≥ 500 simultáneas |
| 2 — BOE Login | Login rate | ≥ 200 logins/seg |
| 3 — REST API | Throughput | ≥ 800 req/seg |
| 4 — Order Ack Latency | P99 | < 5ms (usando `NewOrderMessage` spec-compliant) |
| 5 — Memory Stability | Heap growth | < 200 MB por 10,000 órdenes |

---

## 13. Estructura del Proyecto

```
TitaniumBOE-Sim/
├── src/main/java/com/boe/simulator/
│   ├── protocol/
│   │   ├── message/          # 20 clases de mensajes BOE (sealed hierarchy)
│   │   ├── types/            # Enums de dominio (Side, OrdType, Capacity…)
│   │   └── serialization/    # BoeMessageSerializer
│   ├── server/
│   │   ├── CboeServer.java           # Servidor TCP principal
│   │   ├── connection/               # ClientConnectionHandler por sesión
│   │   ├── matching/                 # MatchingEngine, OrderBook, Trade
│   │   ├── order/                    # OrderManager, Order, OrderValidator
│   │   ├── auth/                     # AuthenticationService (BCrypt)
│   │   ├── session/                  # ClientSession, ClientSessionManager
│   │   ├── heartbeat/                # HeartbeatMonitor
│   │   ├── ratelimit/                # RateLimiter
│   │   └── persistence/              # RocksDB (repos + services + models)
│   ├── api/
│   │   ├── RestApiServer.java        # Javalin setup + OpenAPI
│   │   ├── controller/               # Controladores REST
│   │   ├── service/                  # Servicios de aplicación
│   │   ├── websocket/                # WebSocketHandler + WebSocketService
│   │   ├── dto/                      # Request/Response DTOs
│   │   └── middleware/               # Auth, CORS, Error filters
│   └── bot/
│       ├── BotManager.java
│       └── strategy/                 # MarketMaker, TrendFollower, Random
├── src/test/
│   └── java/com/boe/simulator/
│       ├── protocol/                 # Wire format tests
│       ├── server/                   # Engine, auth, session tests
│       └── load/                     # LoadTestRunner (manual)
├── frontend/                         # Astro 5 + Tailwind (compilado en el JAR)
├── docs/
│   ├── ARCHITECTURE.md               # Este documento
│   ├── API_DOCUMENTATION.md          # Referencia REST completa
│   └── BOE Protocol Specification - Quick Reference.md
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

*Documento mantenido junto al código — si el comportamiento cambia, actualizar esta descripción.*
