# ========================================
# CBOE CLI - Integration Test Script
# ========================================

# 1. Conexión y Autenticación
connect localhost 8081
TRD1
PASS1

# Verificar: ✓ Connected and authenticated successfully
# Verificar: ℹ Real-time notifications enabled

# 2. Verificar Estado
status

# Verificar: Connection: ✓ CONNECTED
# Verificar: Auth: ✓ AUTHENTICATED

# 3. Ver Order Book
book AAPL

# Verificar: Muestra Best Bid/Ask

# 4. Enviar Orden Límite (NO debería ejecutarse)
order buy AAPL 100 149.50

# Verificar notificación: [HH:MM:SS] ✓ Order CLI-XXXXX acknowledged

# 5. Ver Órdenes Activas
orders

# Verificar: Muestra la orden CLI-XXXXX como OPEN

# 6. Enviar Orden Agresiva (DEBERÍA ejecutarse contra ask)
order buy AAPL 50 150.50

# Verificar notificaciones:
#   [HH:MM:SS] ✓ Order CLI-YYYYY acknowledged
#   [HH:MM:SS] 💰 Filled AAPL: 50 @ 150.50 (Exec ID: ZZZZZ)

# 7. Ver Posiciones (debería mostrar +50 AAPL)
positions

# Verificar: AAPL | 50 | Long

# 8. Ver Trades
trades

# Verificar: Muestra el trade reciente

# 9. Cancelar Orden Abierta
cancel CLI-XXXXX

# Verificar notificación: [HH:MM:SS] ℹ Order CLI-XXXXX cancelled

# 10. Verificar que ya no está en órdenes activas
orders

# Verificar: No muestra CLI-XXXXX

# 11. Intentar orden inválida (debería rechazarse)
order buy INVALID 100 10.00

# Verificar notificación: [HH:MM:SS] ✗ Order CLI-ZZZZZ rejected: Invalid symbol

# 12. Ver Historial
history

# Verificar: Muestra todos los comandos ejecutados

# 13. Limpiar consola
clear

# 14. Desconectar
exit

# ========================================
# Resultado Esperado: TODOS LOS PASOS ✅
# ========================================