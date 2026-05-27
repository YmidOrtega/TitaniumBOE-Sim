# Documentación REST API — TitaniumBOE-Sim

**Puerto:** `9091`  
**Base URL:** `http://localhost:9091`  
**Autenticación:** HTTP Basic Auth  
**Formato:** JSON (UTF-8)

---

## Acceso a Documentación Interactiva

Una vez que el servidor esté corriendo:

| Interfaz | URL | Descripción |
|----------|-----|-------------|
| **Scalar UI** | http://localhost:9091/api/docs | Recomendada — UI moderna con ejemplos de código |
| **Swagger UI** | http://localhost:9091/api/swagger | Alternativa clásica |
| **OpenAPI JSON** | http://localhost:9091/openapi | Especificación cruda |

---

## Iniciar el Servidor

```bash
# Build y arranque rápido (modo demo — usuarios y datos precargados)
mvn clean package -DskipTests
DEMO_MODE=true java -jar target/boe-simulator-*.jar
```

**Credenciales demo:**
- `TRD1` / `Pass1234!`
- `TRD2` / `Pass5678!`

---

## Autenticación

Todos los endpoints marcados con **🔐** requieren credenciales via HTTP Basic Auth:

```bash
# Header manual
curl -H "Authorization: Basic $(echo -n 'TRD1:Pass1234!' | base64)" \
     http://localhost:9091/api/orders/active

# Forma abreviada con curl
curl -u TRD1:Pass1234! http://localhost:9091/api/orders/active
```

---

## Endpoints

### Sistema

#### `GET /api/health`

Estado del servidor. No requiere autenticación.

```bash
curl http://localhost:9091/api/health
```

```json
{
  "status": "UP",
  "uptime": "00:03:42",
  "activeConnections": 2,
  "totalOrders": 847,
  "totalTrades": 312
}
```

---

### Autenticación

#### `POST /api/auth/register`

Registra un nuevo usuario.

```bash
curl -X POST http://localhost:9091/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "TRD3", "password": "MiPass123!"}'
```

```json
{ "success": true, "message": "Usuario registrado correctamente" }
```

| Código | Significado |
|--------|-------------|
| 201 | Usuario creado |
| 409 | Username ya existe |
| 400 | Datos inválidos |

#### `POST /api/auth/login` 🔐

Valida credenciales (retorna 200 si son correctas, 401 si no).

```bash
curl -u TRD1:Pass1234! -X POST http://localhost:9091/api/auth/login
```

---

### Símbolos y Market Data

#### `GET /api/symbols`

Lista todos los símbolos con actividad en el simulador.

```bash
curl http://localhost:9091/api/symbols
```

```json
["AAPL", "MSFT", "SPX", "SPXW", "TSLA"]
```

#### `GET /api/symbols/{symbol}`

Market data de un símbolo: best bid/ask, último precio, volumen.

```bash
curl http://localhost:9091/api/symbols/AAPL
```

```json
{
  "symbol": "AAPL",
  "bestBid": 149.75,
  "bestAsk": 150.25,
  "lastTradePrice": 150.00,
  "volume": 2400
}
```

---

### Órdenes

#### `POST /api/orders` 🔐

Envía una nueva orden. El servidor la procesa a través del matching engine y retorna el estado inicial.

```bash
curl -X POST http://localhost:9091/api/orders \
  -u TRD1:Pass1234! \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "AAPL",
    "side": "BUY",
    "orderQty": 100,
    "price": 150.50,
    "orderType": "LIMIT",
    "capacity": "CUSTOMER"
  }'
```

**Body — campos:**

| Campo | Tipo | Requerido | Valores |
|-------|------|-----------|---------|
| `symbol` | string | Sí | Símbolo del activo |
| `side` | string | Sí | `BUY`, `SELL` |
| `orderQty` | int | Sí | 1 – 999,999 |
| `price` | decimal | Para LIMIT | Precio límite |
| `orderType` | string | Sí | `LIMIT`, `MARKET` |
| `capacity` | string | Sí | `CUSTOMER`, `AGENCY`, `MARKET_MAKER`, `PRINCIPAL` |
| `account` | string | No | Cuenta del cliente |

**Respuesta:**

```json
{
  "success": true,
  "data": {
    "clOrdID": "ORD-1714500000001",
    "symbol": "AAPL",
    "side": "BUY",
    "orderQty": 100,
    "leavesQty": 100,
    "price": 150.50,
    "orderType": "LIMIT",
    "status": "LIVE",
    "createdAt": 1714500000001
  }
}
```

#### `GET /api/orders/active` 🔐

Órdenes vivas (LIVE, PARTIALLY_FILLED) del usuario autenticado.

```bash
curl -u TRD1:Pass1234! http://localhost:9091/api/orders/active
```

#### `GET /api/orders/{clOrdID}` 🔐

Detalle de una orden por su ID de cliente.

```bash
curl -u TRD1:Pass1234! http://localhost:9091/api/orders/ORD-1714500000001
```

#### `DELETE /api/orders/{clOrdID}` 🔐

Cancela una orden viva. Retorna `404` si no existe o no pertenece al usuario autenticado.

```bash
curl -X DELETE -u TRD1:Pass1234! \
     http://localhost:9091/api/orders/ORD-1714500000001
```

```json
{ "success": true, "message": "Orden cancelada" }
```

---

### Posiciones

#### `GET /api/positions` 🔐

Todas las posiciones del usuario (netas por símbolo tras fills).

```bash
curl -u TRD1:Pass1234! http://localhost:9091/api/positions
```

```json
[
  { "symbol": "AAPL", "quantity": 200, "avgPrice": 150.25, "unrealizedPnL": 50.00 },
  { "symbol": "MSFT", "quantity": -100, "avgPrice": 310.00, "unrealizedPnL": -30.00 }
]
```

#### `GET /api/positions/{symbol}` 🔐

Posición neta en un símbolo específico.

```bash
curl -u TRD1:Pass1234! http://localhost:9091/api/positions/AAPL
```

---

### Trades

#### `GET /api/trades/my` 🔐

Trades ejecutados por el usuario autenticado (como comprador o vendedor).

```bash
curl -u TRD1:Pass1234! http://localhost:9091/api/trades/my
```

```json
[
  {
    "tradeId": 1000042,
    "symbol": "AAPL",
    "side": "BUY",
    "quantity": 100,
    "price": 150.25,
    "clOrdID": "ORD-1714500000001",
    "timestamp": 1714500001234
  }
]
```

#### `GET /api/trades/recent`

Últimos trades del mercado (todos los usuarios). No requiere autenticación.

```bash
curl http://localhost:9091/api/trades/recent
```

#### `GET /api/trades/symbol/{symbol}`

Trades de un símbolo específico (mercado completo).

```bash
curl http://localhost:9091/api/trades/symbol/AAPL
```

---

### Simulador (Admin)

#### `GET /api/simulator/status`

Estado general del simulador: uptime, órdenes totales, matches.

```bash
curl http://localhost:9091/api/simulator/status
```

#### `GET /api/simulator/bots`

Lista de bots configurados y su estado.

```bash
curl http://localhost:9091/api/simulator/bots
```

```json
[
  { "id": "market-maker", "status": "RUNNING", "ordersPlaced": 1240, "fills": 87 },
  { "id": "trend-follower", "status": "RUNNING", "ordersPlaced": 430, "fills": 23 },
  { "id": "random-trader", "status": "STOPPED", "ordersPlaced": 0, "fills": 0 }
]
```

#### `POST /api/simulator/bots/{botId}/start` 🔐

#### `POST /api/simulator/bots/{botId}/stop` 🔐

```bash
curl -X POST -u TRD1:Pass1234! http://localhost:9091/api/simulator/bots/random-trader/start
curl -X POST -u TRD1:Pass1234! http://localhost:9091/api/simulator/bots/market-maker/stop
```

---

## WebSocket Feed

**Endpoint:** `ws://localhost:9091/ws/feed`

Conecta para recibir eventos en tiempo real del mercado.

```javascript
const ws = new WebSocket('ws://localhost:9091/ws/feed');

ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  switch (msg.type) {
    case 'TRADE':
      console.log(`Trade: ${msg.symbol} ${msg.qty} @ ${msg.price}`);
      break;
    case 'ORDER_BOOK':
      console.log(`Book update: ${msg.symbol}`, msg.bids, msg.asks);
      break;
    case 'ORDER_STATUS':
      console.log(`Order ${msg.clOrdID} → ${msg.status}`);
      break;
  }
};
```

**Tipos de eventos:**

| Tipo | Campos | Descripción |
|------|--------|-------------|
| `TRADE` | symbol, price, qty, timestamp | Trade ejecutado en el mercado |
| `ORDER_BOOK` | symbol, bids[], asks[] | Actualización del libro de órdenes |
| `ORDER_STATUS` | clOrdID, status | Cambio de estado de una orden |

---

## Códigos de Respuesta

| Código | Significado |
|--------|-------------|
| 200 | OK |
| 201 | Recurso creado |
| 400 | Request inválido (campos faltantes o malformados) |
| 401 | No autenticado (credenciales requeridas) |
| 403 | No autorizado (la orden no pertenece al usuario) |
| 404 | Recurso no encontrado |
| 409 | Conflicto (username duplicado, ClOrdID duplicado) |
| 500 | Error interno del servidor |

---

## Formato de Error

```json
{
  "success": false,
  "error": "Order not found: ORD-9999999",
  "timestamp": 1714500005000
}
```

---

## Variables de Entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `DEMO_MODE` | `true` | Pre-carga usuarios demo y datos de mercado |
| `BOE_PORT` | `8081` | Puerto del protocolo BOE binario (TCP) |
| `API_PORT` | `9091` | Puerto REST API + dashboard |
| `LOG_LEVEL` | `INFO` | Verbosidad de logs (`FINE`, `INFO`, `WARNING`) |
| `ADMIN_USERNAME` | — | Usuario admin en modo no-demo |
| `ADMIN_PASSWORD` | — | Contraseña admin |

---

## Referencia del Protocolo BOE Nativo

Para interactuar directamente por TCP (puerto `8081`) usando el protocolo BOE binario, ver:

- [`docs/BOE Protocol Specification - Quick Reference.md`](BOE%20Protocol%20Specification%20-%20Quick%20Reference.md) — referencia completa del wire format
- [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) — diseño del parser y serialización
