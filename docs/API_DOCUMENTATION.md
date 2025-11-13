# Documentación de la API - TitaniumBOE Simulator

## 📚 Acceso a la Documentación

TitaniumBOE Simulator incluye documentación interactiva de la API REST utilizando **Scalar**, una herramienta moderna de documentación OpenAPI.

### URLs de Documentación

Una vez que el servidor esté ejecutándose, la documentación estará disponible en:

- **Scalar UI** (Recomendado): http://localhost:8081/api/docs
- **Swagger UI** (Alternativa): http://localhost:8081/api/swagger
- **Especificación OpenAPI** (JSON): http://localhost:8081/openapi

## 🚀 Características de Scalar

Scalar proporciona una interfaz de documentación moderna con:

- ✅ **Diseño limpio y moderno** - Interfaz intuitiva y fácil de navegar
- ✅ **Pruebas interactivas** - Ejecuta requests directamente desde la documentación
- ✅ **Ejemplos de código** - Snippets en múltiples lenguajes (curl, JavaScript, Python, etc.)
- ✅ **Búsqueda avanzada** - Encuentra rápidamente endpoints y modelos
- ✅ **Modo oscuro** - Para reducir fatiga visual
- ✅ **Soporte completo de OpenAPI 3.0** - Compatible con todas las especificaciones

## 📖 Estructura de la API

### Endpoints Públicos
- `GET /api/health` - Estado del servidor
- `GET /api/symbols` - Lista de símbolos disponibles para trading

### Endpoints Autenticados (Basic Auth)

#### Órdenes
- `POST /api/orders` - Crear una nueva orden
- `GET /api/orders/active` - Obtener órdenes activas
- `GET /api/orders/{clOrdID}` - Obtener detalles de una orden
- `DELETE /api/orders/{clOrdID}` - Cancelar una orden

#### Posiciones
- `GET /api/positions` - Obtener todas las posiciones
- `GET /api/positions/{symbol}` - Obtener posición por símbolo

#### Trades
- `GET /api/trades/recent` - Obtener trades recientes
- `GET /api/trades/symbol/{symbol}` - Obtener trades por símbolo
- `GET /api/trades/my` - Obtener mis trades

#### Simulador (Admin)
- `GET /api/simulator/status` - Estado del simulador
- `GET /api/simulator/bots` - Lista de bots
- `GET /api/simulator/bots/{botId}` - Detalles de un bot
- `POST /api/simulator/bots/{botId}/start` - Iniciar un bot
- `POST /api/simulator/bots/{botId}/stop` - Detener un bot
- `POST /api/simulator/start` - Iniciar simulador
- `POST /api/simulator/stop` - Detener simulador

### WebSocket
- `WS /ws/feed` - Feed en tiempo real de market data

## 🔐 Autenticación

La API utiliza **HTTP Basic Authentication**. Incluye las credenciales en el header:

```bash
curl -u username:password http://localhost:8081/api/orders/active
```

## 📝 Ejemplo de Uso

### Crear una orden LIMIT

```bash
curl -X POST http://localhost:8081/api/orders \
  -u trader1:password \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "AAPL",
    "side": "BUY",
    "orderQty": 100,
    "price": 150.50,
    "orderType": "LIMIT",
    "account": "ACC001",
    "capacity": "CUSTOMER"
  }'
```

### Respuesta

```json
{
  "success": true,
  "data": {
    "clOrdID": "ORD-1234567890",
    "symbol": "AAPL",
    "side": "BUY",
    "orderQty": 100,
    "price": 150.50,
    "orderType": "LIMIT",
    "status": "NEW",
    "timestamp": 1699876543210
  },
  "error": null,
  "timestamp": 1699876543210
}
```

## 🛠️ Desarrollo

### Agregar Anotaciones OpenAPI

Para documentar nuevos endpoints, usa las anotaciones `@OpenApi`:

```java
@OpenApi(
    summary = "Get all orders",
    description = "Retrieve all active orders for the authenticated user",
    operationId = "getOrders",
    path = "/api/orders",
    methods = HttpMethod.GET,
    tags = {"Orders"},
    security = {@OpenApiSecurity(name = "BasicAuth")},
    responses = {
        @OpenApiResponse(status = "200", content = {@OpenApiContent(from = OrderResponse[].class)})
    }
)
public void getOrders(Context ctx) {
    // Implementation
}
```

### Configuración Personalizada

La configuración de OpenAPI se encuentra en `RestApiServer.java`. Puedes personalizar:

- Título y descripción de la API
- Información de contacto
- Servidores disponibles
- Esquemas de seguridad
- Tags y categorías

## 🔒 Seguridad

### Mejores Prácticas Implementadas

1. **Autenticación obligatoria** - Endpoints sensibles requieren autenticación
2. **Validación de entrada** - Todos los requests son validados
3. **CORS configurado** - Prevención de ataques cross-origin
4. **Rate limiting** - Protección contra abuso (si está configurado)
5. **Logs de auditoría** - Todas las operaciones son registradas
6. **Sanitización de errores** - No se exponen detalles internos en producción

### Consideraciones de Seguridad

- ✅ No exponer credenciales en la documentación
- ✅ Usar HTTPS en producción
- ✅ Implementar rate limiting apropiado
- ✅ Mantener logs de acceso y auditoría
- ✅ Validar todos los inputs del usuario
- ✅ Usar tokens JWT para producción (en lugar de Basic Auth)

## 📚 Recursos Adicionales

- [Documentación de Scalar](https://github.com/scalar/scalar)
- [OpenAPI Specification](https://swagger.io/specification/)
- [Javalin OpenAPI Plugin](https://javalin.io/plugins/openapi)

## 🤝 Contribuir

Para mejorar la documentación:

1. Actualiza las anotaciones `@OpenApi` en los controladores
2. Verifica que los modelos DTO estén bien documentados
3. Asegúrate de que los ejemplos sean claros y funcionales
4. Prueba la documentación interactiva en `/api/docs`
5. Actualiza este README si es necesario

## 📞 Soporte

Si encuentras problemas con la documentación o la API:

1. Verifica que el servidor esté ejecutándose
2. Revisa los logs del servidor para errores
3. Consulta la documentación interactiva en `/api/docs`
4. Reporta issues en el repositorio del proyecto
