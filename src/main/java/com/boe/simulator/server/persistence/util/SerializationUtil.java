package com.boe.simulator.server.persistence.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.logging.Level;
import java.util.logging.Logger;

public class SerializationUtil {
    private static final Logger LOGGER = Logger.getLogger(SerializationUtil.class.getName());
    private static SerializationUtil instance;
    private final ObjectMapper objectMapper;

    private SerializationUtil() {
        this.objectMapper = new ObjectMapper();

        objectMapper.registerModule(new JavaTimeModule());
        // Configure
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static synchronized SerializationUtil getInstance() {
        if (instance == null) instance = new SerializationUtil();

        return instance;
    }

    public byte[] serialize(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to serialize object", e);
            throw new RuntimeException("Serialization failed", e);
        }
    }

    public <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            return objectMapper.readValue(data, clazz);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to deserialize object", e);
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
