package com.boe.simulator.server.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

public class RocksDBManager {
    private static final Logger LOGGER = Logger.getLogger(RocksDBManager.class.getName());
    private static RocksDBManager instance;

    private final String dbPath;
    private RocksDB db;
    private final Map<String, ColumnFamilyHandle> columnFamilyHandles;

    // Column Family names
    public static final String CF_DEFAULT = "default";
    public static final String CF_USERS = "users";
    public static final String CF_SESSIONS = "sessions";
    public static final String CF_CONFIG = "config";
    public static final String CF_MESSAGES = "messages";
    public static final String CF_AUDIT = "audit";

    private RocksDBManager(String dbPath) {
        this.dbPath = dbPath;
        this.columnFamilyHandles = new ConcurrentHashMap<>();
    }

    public static synchronized RocksDBManager getInstance(String dbPath) {
        if (instance == null) {
            instance = new RocksDBManager(dbPath);
            instance.initialize();
        }
        return instance;
    }

    public static RocksDBManager getInstance() {
        return getInstance("./data/rocksdb");
    }

    private void initialize() {
        try {
            // Load RocksDB native library
            RocksDB.loadLibrary();

            // Create directory if not exists
            Path path = Paths.get(dbPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                LOGGER.log(Level.INFO, "Created RocksDB directory: {0}", dbPath);
            }

            try (Options options = new Options()
                    .setCreateIfMissing(true)
                    .setCreateMissingColumnFamilies(true)) {

                // List existing column families
                @SuppressWarnings("unused")
                List<byte[]> existingCFs = new ArrayList<>();
                try {
                    existingCFs = RocksDB.listColumnFamilies(options, dbPath);
                } catch (RocksDBException e) {
                    // DB doesn't exist yet, will be created
                    LOGGER.info("No existing column families found, will create new DB");
                }

                // Prepare column family descriptors
                List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();

                // Add default CF (always exists)
                columnFamilyDescriptors.add(new ColumnFamilyDescriptor(
                        RocksDB.DEFAULT_COLUMN_FAMILY,
                        new ColumnFamilyOptions()
                ));

                // Add custom CFs
                Set<String> cfNames = Set.of(CF_USERS, CF_SESSIONS, CF_CONFIG, CF_MESSAGES, CF_AUDIT);
                for (String cfName : cfNames) {
                    columnFamilyDescriptors.add(new ColumnFamilyDescriptor(
                            cfName.getBytes(),
                            new ColumnFamilyOptions()
                    ));
                }

                // Open DB with column families
                List<ColumnFamilyHandle> handles = new ArrayList<>();
                try (DBOptions dbOptions = new DBOptions()
                        .setCreateIfMissing(true)
                        .setCreateMissingColumnFamilies(true)) {
                    db = RocksDB.open(dbOptions, dbPath, columnFamilyDescriptors, handles);
                }

                // Map column family names to handles
                for (int i = 0; i < columnFamilyDescriptors.size(); i++) {
                    String cfName = new String(columnFamilyDescriptors.get(i).getName());
                    columnFamilyHandles.put(cfName, handles.get(i));
                }

                LOGGER.log(Level.INFO, "RocksDB initialized successfully at: {0}", dbPath);
                LOGGER.log(Level.INFO, "Column families: {0}", columnFamilyHandles.keySet());
            }
        } catch (IOException | RocksDBException | UnsatisfiedLinkError e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize RocksDB", e);
            throw new RuntimeException("Failed to initialize RocksDB", e);
        }
    }

    public void put(String columnFamily, byte[] key, byte[] value) throws RocksDBException {
        ColumnFamilyHandle handle = getColumnFamilyHandle(columnFamily);
        db.put(handle, key, value);
    }

    public byte[] get(String columnFamily, byte[] key) throws RocksDBException {
        ColumnFamilyHandle handle = getColumnFamilyHandle(columnFamily);
        return db.get(handle, key);
    }

    public void delete(String columnFamily, byte[] key) throws RocksDBException {
        ColumnFamilyHandle handle = getColumnFamilyHandle(columnFamily);
        db.delete(handle, key);
    }

    public boolean exists(String columnFamily, byte[] key) throws RocksDBException {
        byte[] value = get(columnFamily, key);
        return value != null;
    }

    public List<byte[]> getKeysWithPrefix(String columnFamily, byte[] prefix) throws RocksDBException {
        List<byte[]> keys = new ArrayList<>();
        ColumnFamilyHandle handle = getColumnFamilyHandle(columnFamily);

        try (RocksIterator iterator = db.newIterator(handle)) {
            iterator.seek(prefix);
            while (iterator.isValid()) {
                byte[] key = iterator.key();
                // Check if key starts with prefix
                if (startsWith(key, prefix)) {
                    keys.add(key);
                    iterator.next();
                } else {
                    break;
                }
            }
        }
        return keys;
    }

    public Map<byte[], byte[]> getAll(String columnFamily) throws RocksDBException {
        Map<byte[], byte[]> result = new HashMap<>();
        ColumnFamilyHandle handle = getColumnFamilyHandle(columnFamily);

        try (RocksIterator iterator = db.newIterator(handle)) {
            iterator.seekToFirst();
            while (iterator.isValid()) {
                result.put(iterator.key(), iterator.value());
                iterator.next();
            }
        }
        return result;
    }

    public void writeBatch(WriteBatchOperation... operations) throws RocksDBException {
        try (WriteBatch batch = new WriteBatch();
             WriteOptions writeOptions = new WriteOptions()) {

            for (WriteBatchOperation op : operations) {
                ColumnFamilyHandle handle = getColumnFamilyHandle(op.columnFamily());
                switch (op.type()) {
                    case PUT -> batch.put(handle, op.key(), op.value());
                    case DELETE -> batch.delete(handle, op.key());
                }
            }
            db.write(writeOptions, batch);
        }
    }

    private ColumnFamilyHandle getColumnFamilyHandle(String columnFamily) {
        ColumnFamilyHandle handle = columnFamilyHandles.get(columnFamily);
        if (handle == null) throw new IllegalArgumentException("Unknown column family: " + columnFamily);

        return handle;
    }

    private boolean startsWith(byte[] array, byte[] prefix) {
        if (array.length < prefix.length) return false;

        for (int i = 0; i < prefix.length; i++) {
            if (array[i] != prefix[i]) return false;
        }
        return true;
    }

    public synchronized void close() {
        if (db != null) {

            for (ColumnFamilyHandle handle : columnFamilyHandles.values()) {
                handle.close();
            }
            columnFamilyHandles.clear();

            // Close DB
            db.close();
            db = null;
            LOGGER.info("RocksDB closed successfully");
        }
    }

    public String getDbPath() {
        return dbPath;
    }

    public boolean isOpen() {
        return db != null;
    }

    public boolean isClosed() {
        return db == null;
    }

    // Nested record for batch operations
    public record WriteBatchOperation(
            OperationType type,
            String columnFamily,
            byte[] key,
            byte[] value
    ) {
        public WriteBatchOperation(OperationType type, String columnFamily, byte[] key) {
            this(type, columnFamily, key, null);
        }
    }

    public enum OperationType {
        PUT, DELETE
    }
}
