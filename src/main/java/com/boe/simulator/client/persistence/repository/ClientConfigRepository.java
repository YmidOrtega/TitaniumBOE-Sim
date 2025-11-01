package com.boe.simulator.client.persistence.repository;

import com.boe.simulator.client.persistence.config.PersistedClientConfig;
import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.config.ConfigVersion;
import com.boe.simulator.server.persistence.util.SerializationUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientConfigRepository {
    private static final Logger LOGGER = Logger.getLogger(ClientConfigRepository.class.getName());
    private static final String DEFAULT_PROFILE = "default";
    private static final String PROFILE_PREFIX = "client:config:profile:";
    private static final String VERSION_PREFIX = "client:config:version:";
    private static final String ACTIVE_PROFILE_KEY = "client:config:active";

    private final RocksDBManager dbManager;
    private final SerializationUtil serializer;
    private final List<ConfigChangeListener> listeners;

    private volatile PersistedClientConfig cachedConfig;
    private volatile String activeProfile;

    public ClientConfigRepository(RocksDBManager dbManager) {
        this.dbManager = dbManager;
        this.serializer = SerializationUtil.getInstance();
        this.listeners = new CopyOnWriteArrayList<>();

        loadActiveProfile();
    }

    public void save(String profileName, PersistedClientConfig config) {
        try {
            if (!config.isValid()) throw new IllegalArgumentException("Invalid configuration");

            String profileKey = PROFILE_PREFIX + profileName;
            byte[] data = serializer.serialize(config);
            dbManager.put(RocksDBManager.CF_CONFIG, profileKey.getBytes(), data);

            String versionKey = VERSION_PREFIX + profileName + ":" + config.version().version();
            dbManager.put(RocksDBManager.CF_CONFIG, versionKey.getBytes(), data);

            if (profileName.equals(activeProfile)) cachedConfig = config;

            LOGGER.log(Level.INFO, "Saved client configuration for profile ''{0}'' version {1}", new Object[]{profileName, config.version().version()});

            if (profileName.equals(activeProfile)) notifyConfigChanged(config);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save client configuration for profile " + profileName, e);
            throw new RuntimeException("Failed to save client configuration", e);
        }
    }

    public void save(PersistedClientConfig config) {
        save(DEFAULT_PROFILE, config);
    }

    public Optional<PersistedClientConfig> load(String profileName) {
        try {
            String profileKey = PROFILE_PREFIX + profileName;
            byte[] data = dbManager.get(RocksDBManager.CF_CONFIG, profileKey.getBytes());

            if (data == null) {
                LOGGER.log(Level.INFO, "No configuration found for profile ''{0}''", profileName);
                return Optional.empty();
            }

            PersistedClientConfig config = serializer.deserialize(data, PersistedClientConfig.class);

            if (!config.isValid()) {
                LOGGER.log(Level.WARNING, "Loaded configuration for profile ''{0}'' is invalid", profileName);
                return Optional.empty();
            }

            LOGGER.log(Level.INFO, "Loaded client configuration for profile ''{0}'' version {1}", new Object[]{profileName, config.version().version()});

            return Optional.of(config);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load client configuration for profile " + profileName, e);
            return Optional.empty();
        }
    }

    public Optional<PersistedClientConfig> load() {
        return load(DEFAULT_PROFILE);
    }

    public PersistedClientConfig getCurrentOrDefault() {
        if (cachedConfig != null) return cachedConfig;

        return load(activeProfile).orElseGet(() -> {
            PersistedClientConfig defaultConfig = PersistedClientConfig.createDefault();
            save(activeProfile, defaultConfig);
            return defaultConfig;
        });
    }

    private void loadActiveProfile() {
        try {
            byte[] data = dbManager.get(RocksDBManager.CF_CONFIG, ACTIVE_PROFILE_KEY.getBytes());

            if (data != null) {
                activeProfile = new String(data);
                LOGGER.log(Level.INFO, "Active profile: {0}", activeProfile);
            } else {
                activeProfile = DEFAULT_PROFILE;
                setActiveProfile(DEFAULT_PROFILE);
            }

            cachedConfig = load(activeProfile).orElse(null);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load active profile, using default", e);
            activeProfile = DEFAULT_PROFILE;
        }
    }

    public void setActiveProfile(String profileName) {
        try {
            dbManager.put(
                    RocksDBManager.CF_CONFIG,
                    ACTIVE_PROFILE_KEY.getBytes(),
                    profileName.getBytes()
            );

            activeProfile = profileName;
            cachedConfig = load(profileName).orElse(null);

            LOGGER.log(Level.INFO, "Active profile changed to: {0}", profileName);

            if (cachedConfig != null) notifyConfigChanged(cachedConfig);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to set active profile", e);
            throw new RuntimeException("Failed to set active profile", e);
        }
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public List<String> listProfiles() {
        try {
            List<String> profiles = new ArrayList<>();
            Map<byte[], byte[]> allData = dbManager.getAll(RocksDBManager.CF_CONFIG);

            for (byte[] keyBytes : allData.keySet()) {
                String key = new String(keyBytes);

                if (key.startsWith(PROFILE_PREFIX)) {
                    String profileName = key.substring(PROFILE_PREFIX.length());
                    profiles.add(profileName);
                }
            }

            profiles.sort(String::compareTo);

            return profiles;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list profiles", e);
            return new ArrayList<>();
        }
    }

    public boolean deleteProfile(String profileName) {
        if (DEFAULT_PROFILE.equals(profileName)) {
            LOGGER.warning("Cannot delete default profile");
            return false;
        }

        if (activeProfile.equals(profileName)) {
            LOGGER.warning("Cannot delete active profile");
            return false;
        }

        try {
            String profileKey = PROFILE_PREFIX + profileName;
            dbManager.delete(RocksDBManager.CF_CONFIG, profileKey.getBytes());

            LOGGER.log(Level.INFO, "Deleted profile: {0}", profileName);
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete profile " + profileName, e);
            return false;
        }
    }

    public boolean copyProfile(String sourceProfile, String targetProfile) {
        Optional<PersistedClientConfig> sourceConfigOpt = load(sourceProfile);

        if (sourceConfigOpt.isEmpty()) {
            LOGGER.log(Level.WARNING, "Source profile ''{0}'' not found", sourceProfile);
            return false;
        }

        try {
            PersistedClientConfig sourceConfig = sourceConfigOpt.get();
            PersistedClientConfig newConfig = sourceConfig.withMetadata(
                    "copiedFrom",
                    sourceProfile,
                    "system"
            );

            save(targetProfile, newConfig);
            LOGGER.log(Level.INFO, "Copied profile ''{0}'' to ''{1}''", new Object[]{sourceProfile, targetProfile});

            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to copy profile", e);
            return false;
        }
    }

    public Optional<PersistedClientConfig> loadVersion(String profileName, int version) {
        try {
            String versionKey = VERSION_PREFIX + profileName + ":" + version;
            byte[] data = dbManager.get(RocksDBManager.CF_CONFIG, versionKey.getBytes());

            if (data == null) return Optional.empty();

            PersistedClientConfig config = serializer.deserialize(data, PersistedClientConfig.class);
            return Optional.of(config);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load version " + version + " for profile " + profileName, e);
            return Optional.empty();
        }
    }

    public List<ConfigVersion> listVersions(String profileName) {
        try {
            List<ConfigVersion> versions = new ArrayList<>();
            Map<byte[], byte[]> allData = dbManager.getAll(RocksDBManager.CF_CONFIG);
            String prefix = VERSION_PREFIX + profileName + ":";

            for (Map.Entry<byte[], byte[]> entry : allData.entrySet()) {
                String key = new String(entry.getKey());

                if (key.startsWith(prefix)) {
                    PersistedClientConfig config = serializer.deserialize(
                            entry.getValue(),
                            PersistedClientConfig.class
                    );
                    versions.add(config.version());
                }
            }

            versions.sort((v1, v2) -> Integer.compare(v2.version(), v1.version()));

            return versions;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list versions for profile " + profileName, e);
            return new ArrayList<>();
        }
    }

    public boolean rollback(String profileName, int version, String modifiedBy) {
        try {
            Optional<PersistedClientConfig> oldVersionOpt = loadVersion(profileName, version);

            if (oldVersionOpt.isEmpty()) {
                LOGGER.log(Level.WARNING, "Cannot rollback: version {0} not found for profile {1}", new Object[]{version, profileName});
                return false;
            }

            PersistedClientConfig oldVersion = oldVersionOpt.get();
            PersistedClientConfig currentConfig = load(profileName).orElse(PersistedClientConfig.createDefault());

            PersistedClientConfig newConfig = new PersistedClientConfig(
                    ConfigVersion.next(
                            currentConfig.version(),
                            modifiedBy,
                            "Rollback to version " + version
                    ),
                    oldVersion.host(),
                    oldVersion.port(),
                    oldVersion.username(),
                    oldVersion.passwordHint(),
                    oldVersion.sessionSubID(),
                    oldVersion.connectionTimeout(),
                    oldVersion.readTimeout(),
                    oldVersion.heartbeatIntervalSeconds(),
                    oldVersion.autoHeartbeat(),
                    oldVersion.autoReconnect(),
                    oldVersion.maxReconnectAttempts(),
                    oldVersion.reconnectDelaySeconds(),
                    oldVersion.logLevel(),
                    oldVersion.enableLogging(),
                    oldVersion.metadata()
            );

            save(profileName, newConfig);
            LOGGER.log(Level.INFO, "Rolled back profile ''{0}'' to version {1}", new Object[]{profileName, version});

            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to rollback profile " + profileName, e);
            return false;
        }
    }

    public String exportToJson(String profileName) {
        try {
            PersistedClientConfig config = load(profileName).orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileName));

            return serializer.getObjectMapper().writeValueAsString(config);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to export profile " + profileName, e);
            throw new RuntimeException("Failed to export configuration", e);
        }
    }

    public boolean importFromJson(String profileName, String json, String modifiedBy) {
        try {
            PersistedClientConfig imported = serializer.getObjectMapper().readValue(json, PersistedClientConfig.class);

            PersistedClientConfig newConfig = imported.withVersion(
                    modifiedBy,
                    "Imported configuration"
            );

            save(profileName, newConfig);
            LOGGER.log(Level.INFO, "Imported configuration for profile ''{0}''", profileName);

            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to import configuration for profile " + profileName, e);
            return false;
        }
    }

    public Map<String, String> exportAllProfiles() {
        Map<String, String> exports = new HashMap<>();

        for (String profile : listProfiles()) {
            try {
                exports.put(profile, exportToJson(profile));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to export profile " + profile, e);
            }
        }

        return exports;
    }

    public void addConfigChangeListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    public void removeConfigChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyConfigChanged(PersistedClientConfig newConfig) {
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
        loadActiveProfile();
        LOGGER.info("Configuration cache invalidated");
    }

    @FunctionalInterface
    public interface ConfigChangeListener {
        void onConfigChanged(PersistedClientConfig newConfig);
    }
}