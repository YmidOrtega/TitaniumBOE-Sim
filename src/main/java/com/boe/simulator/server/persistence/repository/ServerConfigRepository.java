package com.boe.simulator.server.persistence.repository;

import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.config.ConfigVersion;
import com.boe.simulator.server.persistence.config.PersistedServerConfig;
import com.boe.simulator.server.persistence.util.SerializationUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerConfigRepository {
    private static final Logger LOGGER = Logger.getLogger(ServerConfigRepository.class.getName());
    private static final String CURRENT_CONFIG_KEY = "config:current";
    private static final String VERSION_PREFIX = "config:version:";

    private final RocksDBManager dbManager;
    private final SerializationUtil serializer;
    private final List<ConfigChangeListener> listeners;

    private volatile PersistedServerConfig cachedConfig;

    public ServerConfigRepository(RocksDBManager dbManager) {
        this.dbManager = dbManager;
        this.serializer = SerializationUtil.getInstance();
        this.listeners = new CopyOnWriteArrayList<>();

        loadCurrentConfig();
    }

    public void save(PersistedServerConfig config) {
        try {
            if (!config.isValid()) throw new IllegalArgumentException("Invalid configuration");

            byte[] data = serializer.serialize(config);
            dbManager.put(RocksDBManager.CF_CONFIG, CURRENT_CONFIG_KEY.getBytes(), data);

            String versionKey = VERSION_PREFIX + config.version().version();
            dbManager.put(RocksDBManager.CF_CONFIG, versionKey.getBytes(), data);

            cachedConfig = config;

            LOGGER.log(Level.INFO, "Saved server configuration version {0}", config.version().version());

            notifyConfigChanged(config);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save server configuration", e);
            throw new RuntimeException("Failed to save server configuration", e);
        }
    }

    public Optional<PersistedServerConfig> loadCurrent() {
        try {
            byte[] data = dbManager.get(RocksDBManager.CF_CONFIG, CURRENT_CONFIG_KEY.getBytes());

            if (data == null) {
                LOGGER.info("No current configuration found, using default");
                return Optional.empty();
            }

            PersistedServerConfig config = serializer.deserialize(data, PersistedServerConfig.class);

            if (!config.isValid()) {
                LOGGER.warning("Loaded configuration is invalid, using default");
                return Optional.empty();
            }

            cachedConfig = config;
            LOGGER.log(Level.INFO, "Loaded server configuration version {0}", config.version().version());

            return Optional.of(config);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load server configuration", e);
            return Optional.empty();
        }
    }

    private void loadCurrentConfig() {
        if (cachedConfig == null) cachedConfig = loadCurrent().orElse(null);
    }

    public PersistedServerConfig getCurrentOrDefault() {
        if (cachedConfig != null) return cachedConfig;

        return loadCurrent().orElseGet(() -> {
            PersistedServerConfig defaultConfig = PersistedServerConfig.createDefault();
            save(defaultConfig);
            return defaultConfig;
        });
    }

    public Optional<PersistedServerConfig> loadVersion(int version) {
        try {
            String versionKey = VERSION_PREFIX + version;
            byte[] data = dbManager.get(RocksDBManager.CF_CONFIG, versionKey.getBytes());

            if (data == null) return Optional.empty();

            PersistedServerConfig config = serializer.deserialize(data, PersistedServerConfig.class);
            return Optional.of(config);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load configuration version " + version, e);
            return Optional.empty();
        }
    }

    public List<ConfigVersion> listVersions() {
        try {
            List<ConfigVersion> versions = new ArrayList<>();
            Map<byte[], byte[]> allData = dbManager.getAll(RocksDBManager.CF_CONFIG);

            for (Map.Entry<byte[], byte[]> entry : allData.entrySet()) {
                String key = new String(entry.getKey());

                if (key.startsWith(VERSION_PREFIX)) {
                    PersistedServerConfig config = serializer.deserialize(
                            entry.getValue(),
                            PersistedServerConfig.class
                    );
                    versions.add(config.version());
                }
            }

            versions.sort((v1, v2) -> Integer.compare(v2.version(), v1.version()));

            return versions;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list configuration versions", e);
            return new ArrayList<>();
        }
    }

    public boolean rollback(int version, String modifiedBy) {
        try {
            Optional<PersistedServerConfig> oldVersionOpt = loadVersion(version);

            if (oldVersionOpt.isEmpty()) {
                LOGGER.log(Level.WARNING, "Cannot rollback: version {0} not found", version);
                return false;
            }

            PersistedServerConfig oldVersion = oldVersionOpt.get();

            PersistedServerConfig newConfig = new PersistedServerConfig(
                    ConfigVersion.next(
                            getCurrentOrDefault().version(),
                            modifiedBy,
                            "Rollback to version " + version
                    ),
                    oldVersion.host(),
                    oldVersion.port(),
                    oldVersion.maxConnections(),
                    oldVersion.connectionTimeout(),
                    oldVersion.heartbeatIntervalSeconds(),
                    oldVersion.heartbeatTimeoutSeconds(),
                    oldVersion.logLevel(),
                    oldVersion.databasePath(),
                    oldVersion.enablePersistence(),
                    oldVersion.enableMetrics(),
                    oldVersion.metricsPort(),
                    oldVersion.metadata()
            );

            save(newConfig);
            LOGGER.log(Level.INFO, "Rolled back to version {0}", version);

            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to rollback to version " + version, e);
            return false;
        }
    }

    public String exportToJson() {
        try {
            PersistedServerConfig config = getCurrentOrDefault();
            return serializer.getObjectMapper().writeValueAsString(config);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to export configuration", e);
            throw new RuntimeException("Failed to export configuration", e);
        }
    }

    public boolean importFromJson(String json, String modifiedBy) {
        try {
            PersistedServerConfig imported = serializer.getObjectMapper().readValue(json, PersistedServerConfig.class);

            PersistedServerConfig newConfig = imported.withVersion(
                    modifiedBy,
                    "Imported configuration"
            );

            save(newConfig);
            LOGGER.info("Imported configuration successfully");

            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to import configuration", e);
            return false;
        }
    }

    public void resetToDefault(String modifiedBy) {
        PersistedServerConfig currentConfig = getCurrentOrDefault();
        PersistedServerConfig defaultConfig = PersistedServerConfig.createDefault();

        PersistedServerConfig newConfig = new PersistedServerConfig(
                ConfigVersion.next(currentConfig.version(), modifiedBy, "Reset to default"),
                defaultConfig.host(),
                defaultConfig.port(),
                defaultConfig.maxConnections(),
                defaultConfig.connectionTimeout(),
                defaultConfig.heartbeatIntervalSeconds(),
                defaultConfig.heartbeatTimeoutSeconds(),
                defaultConfig.logLevel(),
                defaultConfig.databasePath(),
                defaultConfig.enablePersistence(),
                defaultConfig.enableMetrics(),
                defaultConfig.metricsPort(),
                defaultConfig.metadata()
        );

        save(newConfig);
        LOGGER.info("Reset configuration to default");
    }

    public void addConfigChangeListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    public void removeConfigChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyConfigChanged(PersistedServerConfig newConfig) {
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onConfigChanged(newConfig);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error notifying config change listener", e);
            }
        }
    }

    public void invalidateCache() {
        cachedConfig = null;
        loadCurrentConfig();
        LOGGER.info("Configuration cache invalidated");
    }

    @FunctionalInterface
    public interface ConfigChangeListener {
        void onConfigChanged(PersistedServerConfig newConfig);
    }
}