# Configuración de Seguridad para Documentación API

## Propósito

Este documento describe las consideraciones de seguridad implementadas en la documentación de la API usando Scalar.

## Medidas de Seguridad Implementadas

### 1. Separación de Entornos

La documentación Scalar se sirve desde el mismo dominio que la API para evitar problemas de CORS:
- Desarrollo: `http://localhost:8080/api/docs`
- Producción: Debe configurarse con HTTPS

### 2. Sin Credenciales Hardcodeadas

❌ **NUNCA** incluir credenciales reales en:
- Ejemplos de código
- Configuración de OpenAPI
- Headers de ejemplo
- Respuestas de ejemplo

✅ **SÍ** usar valores de ejemplo genéricos:
```json
{
  "username": "demo_user",
  "password": "********"
}
```

### 3. Configuración CORS

El servidor implementa CORS correctamente:
```java
// CorsFilter.java
config.before(new CorsFilter());
```

Esto previene:
- Requests no autorizados desde otros dominios
- Ataques CSRF
- Robo de credenciales

### 4. Rate Limiting

Se recomienda implementar rate limiting en endpoints de documentación para prevenir:
- Scraping automatizado
- Ataques DDoS
- Enumeración de endpoints

### 5. HTTPS en Producción

⚠️ **CRÍTICO**: En producción, SIEMPRE usar HTTPS:

```java
// Configurar SSL en producción
app = Javalin.create(config -> {
    // Forzar HTTPS
    config.http.strictTransportSecurity = true;
});
```

### 6. Sanitización de Respuestas

Los errores NO deben exponer:
- Stack traces completos
- Información de versiones de librerías
- Rutas del sistema de archivos
- Queries SQL
- Información de sesiones internas

✅ Implementado en `ErrorHandler.java`

### 7. Autenticación en Documentación

La especificación OpenAPI declara correctamente el esquema de seguridad:

```java
@OpenApi(
    security = {@OpenApiSecurity(name = "BasicAuth")}
)
```

Esto informa a los usuarios que el endpoint requiere autenticación.

### 8. Validación de Inputs

Todos los requests están validados:
- Tipos de datos
- Rangos válidos
- Formatos requeridos
- Sanitización de SQL injection
- Prevención de XSS

### 9. Content Security Policy

Recomendado para producción:

```java
app.before(ctx -> {
    ctx.header("Content-Security-Policy", 
        "default-src 'self'; " +
        "script-src 'self' 'unsafe-inline' cdn.jsdelivr.net; " +
        "style-src 'self' 'unsafe-inline' cdn.jsdelivr.net;");
});
```

### 10. Logs de Auditoría

Todas las operaciones sensibles son registradas:
- Intentos de autenticación
- Accesos a endpoints protegidos
- Modificaciones de órdenes
- Errores de autorización

## Checklist de Seguridad para Despliegue

Antes de desplegar a producción, verificar:

- [ ] HTTPS configurado y forzado
- [ ] Credenciales de ejemplo no son reales
- [ ] Rate limiting activado
- [ ] CORS configurado restrictivamente
- [ ] Content Security Policy habilitado
- [ ] Logs de auditoría funcionando
- [ ] Backup de datos configurado
- [ ] Monitoreo de errores activo
- [ ] Firewall configurado
- [ ] Actualizaciones de seguridad aplicadas

## Monitoreo de Seguridad

### Métricas a Vigilar

1. **Intentos de autenticación fallidos**
   - Más de 5 por minuto → Posible ataque de fuerza bruta
   
2. **Accesos a `/api/docs`**
   - Monitorear IPs que acceden frecuentemente
   
3. **Requests malformados**
   - Alto número puede indicar escaneo automatizado
   
4. **Errores 4xx/5xx**
   - Picos inusuales pueden indicar ataques

### Alertas Recomendadas

- ⚠️ Más de 10 autenticaciones fallidas en 1 minuto
- ⚠️ Requests desde IPs en listas negras conocidas
- ⚠️ Patrones de acceso sospechosos
- ⚠️ Cambios inusuales en volumen de requests

## Contacto de Seguridad

Si descubres una vulnerabilidad de seguridad:

1. **NO** la publiques públicamente
2. Reporta a: security@titaniumboe.com
3. Incluye detalles de reproducción
4. Espera respuesta en 48 horas

## Referencias

- [OWASP API Security Top 10](https://owasp.org/www-project-api-security/)
- [Scalar Security Best Practices](https://github.com/scalar/scalar/blob/main/SECURITY.md)
- [Javalin Security Guide](https://javalin.io/documentation#security)

## Última Actualización

Documento actualizado: 2025-11-12
Próxima revisión: 2025-12-12
