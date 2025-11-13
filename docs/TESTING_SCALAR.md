# Testing Scalar API Documentation Integration

## 🧪 Pasos para Probar

### 1. Iniciar el Servidor

```bash
# Compilar el proyecto
mvn clean package -DskipTests

# Ejecutar el servidor
java -jar target/boe-simulator-1.0-SNAPSHOT.jar
```

O usando Maven directamente:
```bash
mvn exec:java
```

### 2. Verificar que el Servidor Está Ejecutándose

Abrir en el navegador: http://localhost:8081/api/health

Deberías ver una respuesta JSON:
```json
{
  "status": "healthy",
  "timestamp": 1699876543210,
  "activeOrders": 0,
  "totalMatches": 0
}
```

### 3. Acceder a la Documentación Scalar

Abrir en el navegador: http://localhost:8081/api/docs

Deberías ver:
- ✅ Interfaz Scalar moderna y limpia
- ✅ Lista de todos los endpoints organizados por tags
- ✅ Especificación OpenAPI completa
- ✅ Información de autenticación (Basic Auth)
- ✅ Modelos de datos (DTOs)

### 4. Probar las Otras Interfaces de Documentación

#### Swagger UI (Alternativa)
http://localhost:8081/api/swagger

#### OpenAPI Specification (JSON)
http://localhost:8081/api/openapi

### 5. Probar Endpoints Interactivamente desde Scalar

#### 5.1. Endpoint Público (Sin Autenticación)

1. En Scalar, navegar a `GET /api/symbols`
2. Click en "Send Request" o "Try it out"
3. Deberías ver la lista de símbolos disponibles

#### 5.2. Endpoint Protegido (Con Autenticación)

1. En Scalar, navegar a `GET /api/orders/active`
2. Configurar autenticación Basic Auth:
   - Username: `trader1` (o el usuario que hayas creado)
   - Password: `password`
3. Click en "Send Request"
4. Deberías ver las órdenes activas del usuario

#### 5.3. Crear una Orden (POST)

1. En Scalar, navegar a `POST /api/orders`
2. Configurar autenticación Basic Auth
3. Usar el siguiente body de ejemplo:

```json
{
  "symbol": "AAPL",
  "side": "BUY",
  "orderQty": 100,
  "price": 150.50,
  "orderType": "LIMIT",
  "account": "ACC001",
  "capacity": "CUSTOMER"
}
```

4. Click en "Send Request"
5. Deberías recibir un status 201 con los detalles de la orden creada

### 6. Verificar Características de Scalar

#### ✅ Búsqueda
- Usar la barra de búsqueda en Scalar
- Buscar "order", "trade", "position", etc.
- Verificar que encuentra los endpoints correctamente

#### ✅ Modelos de Datos
- Expandir la sección "Schemas" o "Models"
- Verificar que se muestran los DTOs:
  - OrderRequest
  - OrderResponse
  - ApiResponse
  - TradeDTO
  - PositionDTO

#### ✅ Ejemplos de Código
- En cualquier endpoint, buscar la sección "Code Examples"
- Verificar que se generan ejemplos en:
  - cURL
  - JavaScript (fetch/axios)
  - Python (requests)
  - Java
  - Go

#### ✅ Modo Oscuro
- Buscar el botón de tema (🌙/☀️)
- Alternar entre modo claro y oscuro
- Verificar que funciona correctamente

#### ✅ Respuestas de Ejemplo
- Expandir las respuestas (200, 400, 401, etc.)
- Verificar que se muestran ejemplos de respuestas

### 7. Probar Desde la Línea de Comandos

#### Obtener especificación OpenAPI:
```bash
curl http://localhost:8081/openapi | jq
```

#### Probar endpoint público:
```bash
curl http://localhost:8081/api/symbols | jq
```

#### Probar endpoint protegido:
```bash
curl -u trader1:password http://localhost:8081/api/orders/active | jq
```

#### Crear una orden:
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
  }' | jq
```

### 8. Verificar Seguridad

#### ✅ Autenticación requerida
```bash
# Sin credenciales (debe fallar con 401)
curl -v http://localhost:8081/api/orders/active
```

Respuesta esperada: `401 Unauthorized`

#### ✅ Credenciales inválidas
```bash
# Con credenciales incorrectas (debe fallar con 401)
curl -v -u wronguser:wrongpass http://localhost:8081/api/orders/active
```

Respuesta esperada: `401 Unauthorized`

### 9. Verificar Logs del Servidor

En la consola del servidor, deberías ver logs como:
```
✓ REST API Server started successfully on http://localhost:8081
✓ WebSocket available at ws://localhost:8081/ws/feed
✓ API Documentation available at http://localhost:8081/api/docs (Scalar)
✓ OpenAPI Specification at http://localhost:8081/api/openapi
✓ Swagger UI at http://localhost:8081/api/swagger
```

## 🐛 Troubleshooting

### Problema: No se carga la interfaz Scalar
**Solución**: 
- Verificar que hay conexión a internet (Scalar carga desde CDN)
- Revisar la consola del navegador para errores de red
- Verificar que el puerto 8081 no está bloqueado por firewall

### Problema: 401 Unauthorized en endpoints protegidos
**Solución**:
- Verificar que el usuario existe en el sistema
- Revisar que las credenciales son correctas
- Asegurarse de enviar el header `Authorization: Basic ...`

### Problema: La documentación está desactualizada
**Solución**:
- Recompilar el proyecto: `mvn clean package`
- Reiniciar el servidor
- Limpiar caché del navegador (Ctrl+F5)

### Problema: No se muestran los ejemplos de código
**Solución**:
- Verificar que las anotaciones `@OpenApi` están completas
- Revisar que los DTOs tienen las anotaciones de Jackson
- Recargar la página de Scalar

## ✅ Checklist de Validación

Marca cada item cuando lo hayas verificado:

- [ ] Servidor inicia correctamente
- [ ] `/api/health` responde
- [ ] `/api/docs` carga la interfaz Scalar
- [ ] `/api/swagger` carga Swagger UI
- [ ] `/api/openapi` devuelve el JSON de especificación
- [ ] Endpoints públicos funcionan sin autenticación
- [ ] Endpoints protegidos requieren autenticación
- [ ] Se pueden probar requests desde Scalar
- [ ] Se muestran ejemplos de código
- [ ] El modo oscuro funciona
- [ ] La búsqueda encuentra endpoints
- [ ] Los modelos de datos se muestran correctamente
- [ ] Las respuestas de error son apropiadas
- [ ] Los logs del servidor son claros

## 📊 Métricas de Éxito

La integración es exitosa si:
- ✅ Tiempo de carga de Scalar < 3 segundos
- ✅ Todos los endpoints están documentados
- ✅ Los ejemplos de código son funcionales
- ✅ La autenticación funciona correctamente
- ✅ No hay errores en la consola del navegador
- ✅ La documentación es fácil de navegar

## 🎉 ¡Felicidades!

Si todos los checks están ✅, la integración de Scalar está completa y funcionando correctamente.
