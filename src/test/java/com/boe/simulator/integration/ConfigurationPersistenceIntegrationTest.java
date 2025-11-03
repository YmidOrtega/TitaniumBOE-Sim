package com.boe.simulator.integration;

import com.boe.simulator.client.persistence.config.PersistedClientConfig;
import com.boe.simulator.client.persistence.repository.ClientConfigRepository;
import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.config.ConfigVersion;
import com.boe.simulator.server.persistence.config.PersistedServerConfig;
import com.boe.simulator.server.persistence.repository.ServerConfigRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class ConfigurationPersistenceIntegrationTest {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║  Test Persistencia Fase 3: Configuración Persistente  ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        try {
            // Inicializar DB
            RocksDBManager dbManager = RocksDBManager.getInstance("./data/test_config_db");

            // Test 1: Configuración del Servidor
            testServerConfiguration(dbManager);

            Thread.sleep(1000);

            // Test 2: Versionado de Configuración
            testConfigVersioning(dbManager);

            Thread.sleep(1000);

            // Test 3: Configuración del Cliente con Perfiles
            testClientConfiguration(dbManager);

            Thread.sleep(1000);

            // Test 4: Export/Import
            testExportImport(dbManager);

            Thread.sleep(1000);

            // Test 5: Hot-reload
            testHotReload(dbManager);

            System.out.println("\n╔════════════════════════════════════════════════════════╗");
            System.out.println("║    ✓ Todos los Tests Pasaron Exitosamente             ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("\n✗ Test falló: " + e.getMessage());
            e.printStackTrace();
        } finally {
            RocksDBManager.getInstance().close();
        }
    }

    /**
     * Test 1: Configuración del Servidor
     */
    private static void testServerConfiguration(RocksDBManager dbManager) {
        System.out.println("═══ Test 1: Configuración del Servidor ═══\n");

        ServerConfigRepository serverConfigRepo = new ServerConfigRepository(dbManager);

        // Crear configuración por defecto
        PersistedServerConfig defaultConfig = PersistedServerConfig.createDefault();
        serverConfigRepo.save(defaultConfig);

        System.out.println("✓ Configuración por defecto guardada");
        System.out.println("  - Host: " + defaultConfig.host());
        System.out.println("  - Port: " + defaultConfig.port());
        System.out.println("  - Max Connections: " + defaultConfig.maxConnections());
        System.out.println("  - Version: " + defaultConfig.version().version());
        System.out.println();

        // Cargar configuración
        PersistedServerConfig loaded = serverConfigRepo.getCurrentOrDefault();
        assert loaded.host().equals(defaultConfig.host()) : "Host should match";
        assert loaded.port() == defaultConfig.port() : "Port should match";
        System.out.println("✓ Configuración cargada correctamente");
        System.out.println();

        // Modificar configuración
        PersistedServerConfig modified = loaded.withPort(9090, "admin");
        serverConfigRepo.save(modified);

        System.out.println("✓ Configuración modificada");
        System.out.println("  - Nuevo puerto: " + modified.port());
        System.out.println("  - Nueva versión: " + modified.version().version());
        System.out.println("  - Modificado por: " + modified.version().modifiedBy());
        System.out.println();

        // Verificar nueva versión
        PersistedServerConfig reloaded = serverConfigRepo.getCurrentOrDefault();
        assert reloaded.port() == 9090 : "Port should be updated";
        assert reloaded.version().version() == 2 : "Version should be 2";
        System.out.println("✓ Nueva versión verificada");
        System.out.println();
    }

    /**
     * Test 2: Versionado de Configuración
     */
    private static void testConfigVersioning(RocksDBManager dbManager) {
        System.out.println("═══ Test 2: Versionado de Configuración ═══\n");

        ServerConfigRepository serverConfigRepo = new ServerConfigRepository(dbManager);

        // Obtener configuración actual
        PersistedServerConfig current = serverConfigRepo.getCurrentOrDefault();
        System.out.println("Versión actual: " + current.version().version());
        System.out.println();

        // Hacer varios cambios para crear historial
        PersistedServerConfig v1 = current.withMaxConnections(50, "admin");
        serverConfigRepo.save(v1);
        System.out.println("✓ Versión " + v1.version().version() + ": Max connections = 50");

        PersistedServerConfig v2 = v1.withHeartbeatInterval(15, "admin");
        serverConfigRepo.save(v2);
        System.out.println("✓ Versión " + v2.version().version() + ": Heartbeat interval = 15s");

        PersistedServerConfig v3 = v2.withLogLevel(Level.FINE.getName(), "admin");
        serverConfigRepo.save(v3);
        System.out.println("✓ Versión " + v3.version().version() + ": Log level = FINE");
        System.out.println();

        // Listar todas las versiones
        List<ConfigVersion> versions = serverConfigRepo.listVersions();
        System.out.println("Historial de versiones:");
        for (ConfigVersion version : versions) {
            System.out.println("  - v" + version.version() + " by " + version.modifiedBy() +
                    ": " + version.description() + " (" + version.createdAt() + ")");
        }
        System.out.println();

        // Rollback a versión anterior
        int targetVersion = v1.version().version();
        boolean rollbackSuccess = serverConfigRepo.rollback(targetVersion, "admin");
        assert rollbackSuccess : "Rollback should succeed";

        PersistedServerConfig rolledBack = serverConfigRepo.getCurrentOrDefault();
        System.out.println("✓ Rollback exitoso a versión " + targetVersion);
        System.out.println("  - Max connections restaurado: " + rolledBack.maxConnections());
        System.out.println("  - Nueva versión: " + rolledBack.version().version());
        System.out.println();
    }

    /**
     * Test 3: Configuración del Cliente con Perfiles
     */
    private static void testClientConfiguration(RocksDBManager dbManager) {
        System.out.println("═══ Test 3: Configuración del Cliente con Perfiles ═══\n");

        ClientConfigRepository clientConfigRepo = new ClientConfigRepository(dbManager);

        // Crear perfil por defecto
        PersistedClientConfig defaultConfig = PersistedClientConfig.createDefault();
        clientConfigRepo.save("default", defaultConfig);
        System.out.println("✓ Perfil 'default' creado");
        System.out.println();

        // Crear perfil de producción
        PersistedClientConfig prodConfig = PersistedClientConfig.fromProfile(
                "production",
                "prod.server.com",
                8080,
                "prod_user"
        );
        clientConfigRepo.save("production", prodConfig);
        System.out.println("✓ Perfil 'production' creado");
        System.out.println("  - Host: " + prodConfig.host());
        System.out.println("  - Username: " + prodConfig.username());
        System.out.println();

        // Crear perfil de testing
        PersistedClientConfig testConfig = PersistedClientConfig.fromProfile(
                "testing",
                "test.server.com",
                9090,
                "test_user"
        );
        clientConfigRepo.save("testing", testConfig);
        System.out.println("✓ Perfil 'testing' creado");
        System.out.println();

        // Listar todos los perfiles
        List<String> profiles = clientConfigRepo.listProfiles();
        System.out.println("Perfiles disponibles:");
        for (String profile : profiles) {
            System.out.println("  - " + profile);
        }
        System.out.println();

        // Cambiar perfil activo
        clientConfigRepo.setActiveProfile("production");
        System.out.println("✓ Perfil activo cambiado a 'production'");

        PersistedClientConfig activeConfig = clientConfigRepo.getCurrentOrDefault();
        assert activeConfig.host().equals("prod.server.com") : "Should load production config";
        System.out.println("  - Host activo: " + activeConfig.host());
        System.out.println();

        // Copiar perfil
        boolean copied = clientConfigRepo.copyProfile("production", "production-backup");
        assert copied : "Copy should succeed";
        System.out.println("✓ Perfil 'production' copiado a 'production-backup'");
        System.out.println();

        // Modificar perfil
        PersistedClientConfig modifiedProd = activeConfig
                .withAutoReconnect(true, "user")
                .withAutoHeartbeat(true, "user");
        clientConfigRepo.save("production", modifiedProd);
        System.out.println("✓ Perfil 'production' modificado");
        System.out.println("  - Auto-reconnect: " + modifiedProd.autoReconnect());
        System.out.println("  - Auto-heartbeat: " + modifiedProd.autoHeartbeat());
        System.out.println("  - Version: " + modifiedProd.version().version());
        System.out.println();
    }

    /**
     * Test 4: Export/Import
     */
    private static void testExportImport(RocksDBManager dbManager) {
        System.out.println("═══ Test 4: Export/Import de Configuración ═══\n");

        ServerConfigRepository serverConfigRepo = new ServerConfigRepository(dbManager);
        ClientConfigRepository clientConfigRepo = new ClientConfigRepository(dbManager);

        // Exportar configuración del servidor
        String serverJson = serverConfigRepo.exportToJson();
        System.out.println("✓ Configuración del servidor exportada a JSON");
        System.out.println("Primeros 200 caracteres:");
        System.out.println(serverJson.substring(0, Math.min(200, serverJson.length())) + "...");
        System.out.println();

        // Modificar y re-importar
        serverConfigRepo.resetToDefault("admin");
        System.out.println("✓ Configuración reseteada a valores por defecto");

        boolean imported = serverConfigRepo.importFromJson(serverJson, "admin");
        assert imported : "Import should succeed";
        System.out.println("✓ Configuración importada desde JSON");
        System.out.println();

        // Exportar perfil de cliente
        String clientJson = clientConfigRepo.exportToJson("production");
        System.out.println("✓ Perfil 'production' exportado a JSON");
        System.out.println();

        // Importar a nuevo perfil
        boolean clientImported = clientConfigRepo.importFromJson(
                "production-imported",
                clientJson,
                "admin"
        );
        assert clientImported : "Client import should succeed";
        System.out.println("✓ Configuración importada a perfil 'production-imported'");
        System.out.println();

        // Exportar todos los perfiles
        Map<String, String> allProfiles = clientConfigRepo.exportAllProfiles();
        System.out.println("✓ Todos los perfiles exportados");
        System.out.println("  - Total perfiles: " + allProfiles.size());
        for (String profile : allProfiles.keySet()) {
            System.out.println("    * " + profile);
        }
        System.out.println();
    }

    /**
     * Test 5: Hot-reload
     */
    private static void testHotReload(RocksDBManager dbManager) throws InterruptedException {
        System.out.println("═══ Test 5: Hot-reload de Configuración ═══\n");

        ServerConfigRepository serverConfigRepo = new ServerConfigRepository(dbManager);
        ClientConfigRepository clientConfigRepo = new ClientConfigRepository(dbManager);

        CountDownLatch serverLatch = new CountDownLatch(1);
        CountDownLatch clientLatch = new CountDownLatch(1);

        // Registrar listener para servidor
        serverConfigRepo.addConfigChangeListener(newConfig -> {
            System.out.println("🔥 Hot-reload (Server): Nueva configuración detectada!");
            System.out.println("  - Versión: " + newConfig.version().version());
            System.out.println("  - Puerto: " + newConfig.port());
            System.out.println("  - Descripción: " + newConfig.version().description());
            serverLatch.countDown();
        });

        // Registrar listener para cliente
        clientConfigRepo.addConfigChangeListener(newConfig -> {
            System.out.println("🔥 Hot-reload (Client): Nueva configuración detectada!");
            System.out.println("  - Versión: " + newConfig.version().version());
            System.out.println("  - Host: " + newConfig.host());
            System.out.println("  - Descripción: " + newConfig.version().description());
            clientLatch.countDown();
        });

        System.out.println("✓ Listeners registrados para hot-reload");
        System.out.println();

        // Modificar configuración del servidor
        PersistedServerConfig serverConfig = serverConfigRepo.getCurrentOrDefault();
        PersistedServerConfig newServerConfig = serverConfig.withPort(8888, "system");
        serverConfigRepo.save(newServerConfig);

        // Esperar notificación
        boolean serverNotified = serverLatch.await(2, TimeUnit.SECONDS);
        assert serverNotified : "Server listener should be notified";
        System.out.println();

        // Modificar configuración del cliente
        PersistedClientConfig clientConfig = clientConfigRepo.getCurrentOrDefault();
        PersistedClientConfig newClientConfig = clientConfig.withConnection(
                "new.server.com",
                9999,
                "system"
        );
        clientConfigRepo.save(clientConfigRepo.getActiveProfile(), newClientConfig);

        // Esperar notificación
        boolean clientNotified = clientLatch.await(2, TimeUnit.SECONDS);
        assert clientNotified : "Client listener should be notified";
        System.out.println();

        System.out.println("✓ Hot-reload funcionando correctamente");
        System.out.println("  - Ambos listeners fueron notificados");
        System.out.println("  - No se requirió reinicio");
        System.out.println();
    }
}