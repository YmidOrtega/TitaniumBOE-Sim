package com.boe.simulator.server.persistence.service;

import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.model.PersistedUser;
import com.boe.simulator.server.persistence.repository.UserRepository;
import com.boe.simulator.server.persistence.util.SerializationUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserRepositoryService implements UserRepository {
    private static final Logger LOGGER = Logger.getLogger(UserRepositoryService.class.getName());
    private final RocksDBManager dbManager;
    private final SerializationUtil serializer;

    public UserRepositoryService(RocksDBManager dbManager) {
        this.dbManager = dbManager;
        this.serializer = SerializationUtil.getInstance();
    }

    @Override
    public void save(PersistedUser user) {
        try {
            String key = buildKey(user.username());
            byte[] value = serializer.serialize(user);
            dbManager.put(RocksDBManager.CF_USERS, key.getBytes(), value);
            LOGGER.fine("Saved user: " + user.username());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save user: " + user.username(), e);
            throw new RuntimeException("Failed to save user", e);
        }
    }

    @Override
    public Optional<PersistedUser> findByUsername(String username) {
        try {
            String key = buildKey(username);
            byte[] data = dbManager.get(RocksDBManager.CF_USERS, key.getBytes());
            if (data == null) return Optional.empty();

            PersistedUser user = serializer.deserialize(data, PersistedUser.class);
            return Optional.of(user);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find user: " + username, e);
            return Optional.empty();
        }
    }

    @Override
    public List<PersistedUser> findAll() {
        try {
            List<PersistedUser> users = new ArrayList<>();
            Map<byte[], byte[]> allData = dbManager.getAll(RocksDBManager.CF_USERS);

            for (byte[] data : allData.values()) {
                PersistedUser user = serializer.deserialize(data, PersistedUser.class);
                users.add(user);
            }

            LOGGER.fine("Found " + users.size() + " users");
            return users;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find all users", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedUser> findAllActive() {
        return findAll().stream()
                .filter(PersistedUser::active)
                .toList();
    }

    @Override
    public void delete(String username) {
        try {
            String key = buildKey(username);
            dbManager.delete(RocksDBManager.CF_USERS, key.getBytes());
            LOGGER.info("Deleted user: " + username);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete user: " + username, e);
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    @Override
    public boolean exists(String username) {
        try {
            String key = buildKey(username);
            return dbManager.exists(RocksDBManager.CF_USERS, key.getBytes());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to check if user exists: " + username, e);
            return false;
        }
    }

    @Override
    public long count() {
        return findAll().size();
    }

    @Override
    public void updateLastLogin(String username) {
        Optional<PersistedUser> userOpt = findByUsername(username);
        if (userOpt.isPresent()) {
            PersistedUser updatedUser = userOpt.get().withLogin();
            save(updatedUser);
            LOGGER.fine("Updated last login for user: " + username);
        } else {
            LOGGER.warning("Cannot update last login: user not found: " + username);
        }
    }

    private String buildKey(String username) {
        return "user:" + username;
    }
}
