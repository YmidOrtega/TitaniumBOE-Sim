package com.boe.simulator.server.persistence.repository;

import com.boe.simulator.server.persistence.model.PersistedUser;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    void save(PersistedUser user);

    Optional<PersistedUser> findByUsername(String username);

    List<PersistedUser> findAll();

    List<PersistedUser> findAllActive();

    void delete(String username);

    boolean exists(String username);

    long count();

    void updateLastLogin(String username);
}
