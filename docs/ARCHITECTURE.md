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
│  Browser ─────────── REST / WebSocket ─────────── Cliente BOE (TCP)     │
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

    public static BinaryPrice fromPrice(BigDecimal price) {
        long raw = price.setScale(4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(10000))
                        .longValueExact();
        return new BinaryPrice(raw);
    }

    public BigDecimal toPrice() {
        return BigDecimal.valueOf(rawValue).divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);
    }

    public void putInto(ByteBuffer buf) {   // sin asignación, para el hot path
        buf.putLong(rawValue);
    }
}
```

`12.3400` → rawValue `123400` → wire `08 E2 01 00 00 00 00 00`

**Cuatro decisiones en estas pocas líneas:**

- **Nunca `double` para dinero.** Un error de redondeo de un ULP puede hacer que dos órdenes
  crucen cuando no deben.
- **`long` como representación interna, no `BigDecimal`.** El protocolo transmite un entero con
  4 decimales implícitos: el `long` *es* el formato de cable. `BigDecimal` solo aparece en la
  frontera de la API.
- **`longValueExact()` y no `longValue()`.** Si el precio desborda un `long`, lanza
  `ArithmeticException` en lugar de truncar en silencio.
- **`putInto(ByteBuffer)`** escribe directamente sobre un buffer existente, sin asignar un
  array intermedio como hace `toBytes()`.

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

### 6.3 Sincronización — dos capas con propósitos distintos

**Capa 1 — `MatchingEngine`: un lock por símbolo.** Serializa las *escrituras*.

```java
private final Map<String, Object> symbolLocks = new ConcurrentHashMap<>();

public List<Trade> processOrder(Order order) {
    Object lock = symbolLocks.computeIfAbsent(order.getSymbol(), k -> new Object());
    synchronized (lock) {
        // ... ciclo de matching completo bajo el lock del símbolo
    }
}
```

Hace atómico el ciclo entero —cruzar contra varios niveles, generar trades, actualizar
cantidades— sin serializar símbolos entre sí: `AAPL` y `SPX` no comparten estado, así que su
matching corre en paralelo con contención cero.

**Capa 2 — `OrderBook`: `StampedLock` con lectura optimista.** Protege a los *lectores*.

```java
private final StampedLock lock = new StampedLock();

public BigDecimal getBestBid() {
    long stamp = lock.tryOptimisticRead();
    BigDecimal result = bestBidUnlocked();
    if (!lock.validate(stamp)) {              // ¿hubo escritura mientras leía?
        stamp = lock.readLock();
        try { result = bestBidUnlocked(); } finally { lock.unlockRead(stamp); }
    }
    return result;
}
```

No es redundante con la capa 1: al libro lo leen consumidores que **no pasan por el motor** —
la API REST devolviendo profundidad de mercado, el `WebSocketService` emitiendo
actualizaciones, los bots consultando el mejor bid. Esos leen mientras el motor escribe.

La lectura optimista importa porque las lecturas superan con mucho a las escrituras:
`tryOptimisticRead` no toma lock, lee y valida después. Si nadie escribió en medio —el caso
habitual— no ha habido contención en absoluto; solo si hubo escritura concurrente se reintenta
con un read lock real.

`OrderBook` es una clase pública y se protege a sí misma en vez de confiar en que todos sus
llamantes pasen antes por el lock del motor.

**Limitación conocida:** la notificación a listeners (`notifyOrderAdded`, difusión por
WebSocket) ocurre hoy *dentro* de la sección crítica del símbolo. Un consumidor lento alarga
el lock y frena el matching de ese símbolo. Debería encolarse y emitirse fuera del lock.

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

| Estado | isActive() | isCancellable() | isLive() | isTerminal() |
|--------|-----------|-----------------|----------|--------------|
| `PENDING_NEW` | false | false | false | false |
| `LIVE` | true | true | true | false |
| `PARTIALLY_FILLED` | true | true | true | false |
| `FILLED` | false | false | false | true |
| `CANCELLED` | false | false | false | true |
| `REJECTED` | false | false | false | true |
| `EXPIRED` | false | false | false | true |
| `PENDING_CANCEL` | false | true | false | false |
| `PENDING_REPLACE` | false | true | false | false |

> `isActive()` devuelve `true` solo para `LIVE` y `PARTIALLY_FILLED`. Importa porque
> `processModifyOrder` rechaza la modificación si `!order.getState().isActive()`.
> `PENDING_CANCEL` y `PENDING_REPLACE` están declarados en el enum pero no se usan en el flujo actual.

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
│  - messages CF       │
│  - sessions CF       │
│  - users CF          │
│  - audit CF          │
│  - config CF         │
└──────────────────────┘
```

**Ventaja:** el matching engine y el dispatcher nunca esperan I/O de disco. La confirmación va al cliente (OrderAck) antes de que la persistencia se complete.

El hilo consumidor es un hilo virtual (`order-persist`) y no escribe de una en una:

```java
public void saveAsync(Order order) {
    if (!writeQueue.offer(order)) {
        save(order);                      // cola llena → escritura síncrona
    }
}

private void runAsyncWriter() {
    List<Order> batch = new ArrayList<>(256);
    while (asyncRunning) {
        Order first = writeQueue.poll(1, TimeUnit.MILLISECONDS);
        if (first == null) continue;
        batch.add(first);
        writeQueue.drainTo(batch, 255);   // hasta 256 por flush
        flushBatch(batch);
        batch.clear();
    }
    flushRemaining();
}
```

**Batching adaptativo:** `drainTo` agrupa lo que haya en cola en un `WriteBatch` de RocksDB,
mucho más eficiente que N escrituras sueltas. El lote se forma solo bajo carga: si llega una
orden aislada, `flushBatch` detecta `size() == 1` y hace un `put` normal.

**Backpressure en vez de pérdida:** si la cola se llena, `saveAsync` no descarta la orden ni
deja crecer la cola sin límite — el productor paga el coste de escribir él mismo. Bajo
saturación el sistema se ralentiza; no pierde datos.

**Parada limpia:** `stopAsyncPersistence()` interrumpe el hilo, espera hasta 5 s y drena lo que
quede pendiente con `flushRemaining()`.

**Trade-off:** se confirma al cliente antes de que la orden esté en disco, así que un crash
abrupto puede perder lo que quedara en cola. Aceptable en un simulador; en producción harían
falta WAL con `fsync` síncrono para las órdenes, dejando write-behind solo para lo que tolera
pérdida (estadísticas, auditoría).

### 8.2 Column Families

Ocho column families (la `default` más siete propias), declaradas en `RocksDBManager`. Los valores se serializan con
**Jackson a JSON UTF-8** (`SerializationUtil`), no a un formato binario: el contenido de la
base de datos es legible directamente, lo que resulta útil al depurar.

| Column Family | Prefijo de clave | Valor |
|---------------|------------------|-------|
| `orders` | `order:<clOrdID>` | `PersistedOrder` |
| `trades` | `trade:<tradeId>` | `PersistedTrade` |
| `messages` | `msg:<messageId>` | `PersistedMessage` |
| `sessions` | `session:<…>` | `PersistedSession` |
| `users` | `user:<username>` | `PersistedUser` (hash BCrypt) |
| `audit` | `audit:<eventId>` | `AuditEvent` |
| `config` | `config:<…>` y `stats:<fecha>` | `PersistedServerConfig` y `PersistedStatistics` |
| `default` | — | sin uso |

> Las estadísticas comparten la column family `config`. Es intencionado: así **sobreviven al
> reset diario**, que limpia `messages`, `trades`, `audit` y `sessions` pero conserva `users`
> y `config`.

#### Índices secundarios

RocksDB solo busca por clave exacta o por prefijo, así que los índices se escriben a mano como
entradas adicionales cuyo valor es el ID de la entrada principal:

```
trades:    trade-symbol:<symbol>:<ts>    trade-user:<username>:<ts>    trade-date:<fecha>:<ts>
messages:  user:<username>:<ts>   type:<0xNN>:<ts>   date:<fecha>:<ts>   conn:<id>:<ts>
```

Cada trade genera cinco escrituras: la principal más cuatro de índice (símbolo, comprador,
vendedor y fecha). El timestamp al final de la clave garantiza unicidad y, al coincidir el
orden lexicográfico con el cronológico, devuelve los resultados ya ordenados por tiempo.

#### Compatibilidad al añadir una column family

`RocksDB.open` se invoca con `setCreateMissingColumnFamilies(true)`, de modo que añadir una
column family nueva a la lista de descriptores la crea automáticamente sobre una base de datos
existente. No hace falta migración.

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
| Simulador | POST | `/api/simulator/bots/{id}/start` | No | Iniciar bot |
| Simulador | POST | `/api/simulator/bots/{id}/stop` | No | Detener bot |
| Simulador | POST | `/api/simulator/start` | No | Iniciar todos los bots |
| Simulador | POST | `/api/simulator/stop` | No | Detener todos los bots |
| Docs | GET | `/api/docs` | No | Scalar UI (OpenAPI) |

> Los filtros `before` registrados son `/api/orders*`, `/api/positions*`, `/api/trades*` y
> `/api/auth/me`. Las rutas `/api/simulator/*` quedan **sin autenticar** a propósito: el botón
> «START BOTS» del dashboard las invoca sin credenciales.

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

Cada bot llama directamente a `OrderManager.processNewOrder(msg, username)` dentro del proceso —construye un `NewOrderMessage` y usa la sobrecarga pensada para REST, sin pasar por HTTP—, de modo que recorre la misma validación, matching y persistencia que cualquier otra orden. Los tres arrancan automáticamente cuando `marketSimulatorEnabled` está activo (valor por defecto).

---

## 11. Decisiones de Diseño Clave

### 11.1 Java 21 Virtual Threads

**Decisión:** un VThread por conexión TCP, I/O bloqueante.  
**Alternativa considerada:** NIO non-blocking con Selector loop (Netty style).  
**Por qué VThreads:** código más simple y legible, sin callback hell, sin CompletableFuture chains. Java 21 garantiza que el scheduler mapea VThreads a OS threads eficientemente. En pruebas: >500 conexiones simultáneas con latencia P99 < 5ms.

### 11.2 Sealed Class Hierarchy + Pattern Matching

**Decisión:** `BoeProtocolMessage` como sealed class con dos ramas: `SessionMessage` y `ApplicationMessage`.  
**Por qué:** el compilador verifica exhaustividad del switch. Agregar un nuevo tipo de mensaje requiere actualizar el switch — la omisión es un error de compilación, no un bug silencioso en runtime.

### 11.3 Dos Capas de Sincronización

**Decisión:** `synchronized` sobre un lock por símbolo en `MatchingEngine` para las escrituras,
y `StampedLock` con lectura optimista dentro de `OrderBook` para las lecturas.

**Por qué el lock por símbolo:** `AAPL` y `SPX` no comparten estado; serializar su matching no
aporta nada y cuesta throughput. Dentro de un mismo símbolo, en cambio, el matching *tiene* que
ser secuencial: si dos órdenes cruzan en paralelo, el orden de llegada deja de determinar quién
ejecuta primero y la prioridad precio-tiempo pierde su significado. Los exchanges reales
tampoco paralelizan dentro de un símbolo — paralelizan entre símbolos.

**Por qué además el `StampedLock`:** el libro lo leen consumidores que no pasan por el motor
(REST, WebSocket, bots) y esas lecturas concurren con las escrituras del matching. La lectura
optimista permite que el caso dominante —leer sin escritura simultánea— no tome ningún lock.

Detalle completo y limitación conocida en §6.3.

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

347 tests distribuidos en 33 clases (cifras de `mvn test`, no estimadas):

| Área | Tests | Enfoque |
|------|-------|---------|
| Wire format (`protocol/message/`) | 173 | Parseo y serialización byte a byte contra la spec |
| Session layer (`server/session/`) | 35 | Login, logout, estadísticas de sesión |
| Order management (`server/order/`) | 31 | Validación, ciclo de vida, estados |
| **Matching engine (`server/matching/`)** | **29** | Prioridad precio-tiempo, self-trade, Modify, concurrencia |
| Auth (`server/auth/`) | 15 | BCrypt, resultados de autenticación |
| Tipos del protocolo (`protocol/types/`) | 15 | `BinaryPrice`, enums de dominio |
| Serialización (`protocol/serialization/`) | 14 | `BoeMessageSerializer` |
| Config (`server/config/`) | 8 | Construcción y validación de `ServerConfiguration` |
| Error handling (`server/error/`) | 6 | Mapeo de errores del protocolo |
| Rate limiting (`server/ratelimit/`) | 6 | Ventana fija por conexión |
| Validación de mensajes (`server/validation/`) | 5 | Campos obligatorios y rangos |
| Heartbeat (`server/heartbeat/`) | 5 | Intervalos y timeout |
| Métricas (`server/metrics/`) | 5 | Contadores de salud |
| Load test | manual | `LoadTestRunner` (5 fases, fuera del suite de CI) |

### 12.2 Cobertura del Motor de Matching

Repartida en cuatro clases, cubre las invariantes que hacen correcto a un motor de órdenes:

**`MatchingEnginePriorityTest`** — prioridad precio-tiempo:
FIFO dentro de un nivel de precio, mejor precio primero entre niveles, precio de ejecución
igual al de la orden pasiva (mejora de precio para el agresor), barrido de varios niveles,
fills parciales que dejan remanente en el libro, órdenes MARKET, y aislamiento entre símbolos.

**`MatchingEngineSelfTradeTest`** — prevención de wash trades:
la orden pasiva propia se cancela y la agresiva continúa contra la siguiente contrapartida
ajena; con `allowSelfTrade=true` el cruce sí se ejecuta.

**`MatchingEngineModifyTest`** — Modify Order (§6.4):
el reprecio mueve la orden entre niveles sin dejar fantasmas y **la orden sigue siendo
cancelable después**, que es la regresión concreta que aparecería si se actualizara el precio
antes de removerla del `TreeMap`. Cubre también la lógica de delta sobre `leavesQty` en ambos
sentidos, la auto-cancelación cuando el delta la deja en cero, y el reprecio agresivo que cruza.

**`MatchingEngineConcurrencyTest`** — las dos capas de bloqueo (§6.3):
símbolos distintos procesados en paralelo mantienen sus libros aislados; agresores concurrentes
sobre el mismo símbolo casan exactamente `min(oferta, demanda)` sin que ninguna orden ejecute
más de su cantidad —la invariante que rompería un lock mal puesto—; y lectores concurrentes
(`getBestBid`, `getSnapshot`, `size`) sobre el libro durante 500 escrituras no observan estado
inconsistente ni lanzan excepciones.

Pendiente: tests de integración extremo a extremo que ejerciten el camino completo
`ClientConnectionHandler → OrderManager → MatchingEngine` sobre un socket real.

### 12.3 Load Test Runner

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
