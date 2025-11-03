package com.boe.simulator.integration;

import java.util.List;
import java.util.Optional;

import com.boe.simulator.server.auth.AuthenticationResult;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.model.PersistedUser;
import com.boe.simulator.server.persistence.repository.UserRepository;
import com.boe.simulator.server.persistence.service.UserRepositoryService;
import com.boe.simulator.server.persistence.util.PasswordHasher;

public class UserPersistenceIntegrationTest {
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║    Test Persistencia Fase 1: RocksDB + Usuarios       ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");
        
        try {
            // Test 1: Inicialización de RocksDB
            testRocksDBInitialization();
            
            // Test 2: CRUD básico de usuarios
            testUserCRUD();
            
            // Test 3: Password hashing
            testPasswordHashing();
            
            // Test 4: AuthenticationService con persistencia
            testAuthenticationService();
            
            // Test 5: Persistencia entre reinicios
            testPersistenceAcrossRestarts();
            
            System.out.println("\n╔════════════════════════════════════════════════════════╗");
            System.out.println("║    ✓ Todos los Tests Pasaron Exitosamente             ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            System.err.println("\n✗ Test falló: " + e.getMessage());
        } finally {
            // Cleanup
            RocksDBManager.getInstance().close();
        }
    }
    
    /**
     * Test 1: Inicialización de RocksDB
     */
    private static void testRocksDBInitialization() {
        System.out.println("═══ Test 1: Inicialización de RocksDB ═══");
        
        RocksDBManager dbManager = RocksDBManager.getInstance("./data/test_rocksdb");
        
        assert dbManager.isOpen() : "DB should be open";
        System.out.println("✓ RocksDB inicializado en: " + dbManager.getDbPath());
        System.out.println("✓ DB está abierta: " + dbManager.isOpen());
        
        System.out.println();
    }
    
    /**
     * Test 2: CRUD básico de usuarios
     */
    private static void testUserCRUD() {
        System.out.println("═══ Test 2: CRUD de Usuarios ═══");
        
        RocksDBManager dbManager = RocksDBManager.getInstance();
        UserRepository userRepo = new UserRepositoryService(dbManager);
        
        // Create
        String passwordHash = PasswordHasher.hash("testpass123");
        PersistedUser testUser = PersistedUser.create("testuser", passwordHash);
        userRepo.save(testUser);
        System.out.println("✓ Usuario creado: " + testUser.username());
        
        // Read
        Optional<PersistedUser> foundUser = userRepo.findByUsername("testuser");
        assert foundUser.isPresent() : "User should exist";
        System.out.println("✓ Usuario encontrado: " + foundUser.get().username());
        System.out.println("  - Creado: " + foundUser.get().createdAt());
        System.out.println("  - Login count: " + foundUser.get().loginCount());
        System.out.println("  - Activo: " + foundUser.get().active());
        
        // Update (with login)
        PersistedUser updatedUser = foundUser.get().withLogin();
        userRepo.save(updatedUser);
        System.out.println("✓ Usuario actualizado con login");
        
        // Verify update
        Optional<PersistedUser> afterLogin = userRepo.findByUsername("testuser");
        assert afterLogin.isPresent() : "User should still exist";
        assert afterLogin.get().loginCount() == 1 : "Login count should be 1";
        assert afterLogin.get().lastLogin() != null : "Last login should be set";
        System.out.println("✓ Login count actualizado: " + afterLogin.get().loginCount());
        System.out.println("✓ Last login: " + afterLogin.get().lastLogin());
        
        // List all
        List<PersistedUser> allUsers = userRepo.findAll();
        System.out.println("✓ Total usuarios en DB: " + allUsers.size());
        
        // Delete
        userRepo.delete("testuser");
        System.out.println("✓ Usuario eliminado");
        
        // Verify deletion
        Optional<PersistedUser> afterDelete = userRepo.findByUsername("testuser");
        assert afterDelete.isEmpty() : "User should not exist after delete";
        System.out.println("✓ Usuario confirmado como eliminado");
        
        System.out.println();
    }
    
    /**
     * Test 3: Password hashing
     */
    private static void testPasswordHashing() {
        System.out.println("═══ Test 3: Password Hashing (BCrypt) ═══");
        
        String plainPassword = "mySecurePassword123!";
        
        // Hash password
        String hash1 = PasswordHasher.hash(plainPassword);
        System.out.println("✓ Password hasheado: " + hash1.substring(0, 20) + "...");
        
        // Hash again (should be different due to salt)
        String hash2 = PasswordHasher.hash(plainPassword);
        assert !hash1.equals(hash2) : "Hashes should be different (different salts)";
        System.out.println("✓ Segundo hash es diferente (salt único): " + hash2.substring(0, 20) + "...");
        
        // Verify correct password
        boolean validPassword = PasswordHasher.verify(plainPassword, hash1);
        assert validPassword : "Password should be valid";
        System.out.println("✓ Password correcto verificado");
        
        // Verify incorrect password
        boolean invalidPassword = PasswordHasher.verify("wrongPassword", hash1);
        assert !invalidPassword : "Wrong password should not verify";
        System.out.println("✓ Password incorrecto rechazado");
        
        System.out.println();
    }
    
    /**
     * Test 4: AuthenticationService con persistencia
     */
    private static void testAuthenticationService() {
        System.out.println("═══ Test 4: AuthenticationService con Persistencia ═══");
        
        RocksDBManager dbManager = RocksDBManager.getInstance();
        
        // Clear existing users for clean test
        UserRepository userRepo = new UserRepositoryService(dbManager);
        for (PersistedUser user : userRepo.findAll()) {
            userRepo.delete(user.username());
        }
        
        // Create AuthenticationService (will create default users)
        AuthenticationService authService = new AuthenticationService(dbManager);
        
        long userCount = authService.getUserCount();
        System.out.println("✓ AuthenticationService inicializado");
        System.out.println("  - Total usuarios: " + userCount);
        assert userCount == 5 : "Should have 5 default users";
        
        // Test login with valid credentials
        AuthenticationResult validLogin = authService.authenticate("USER", "PASS", "TEST1");
        assert validLogin.isAccepted() : "Login should be accepted";
        System.out.println("✓ Login exitoso con credenciales válidas");
        System.out.println("  - Mensaje: " + validLogin.getMessage());
        
        // Test login with invalid password
        AuthenticationResult invalidLogin = authService.authenticate("USER", "WRONGPASS", "TEST2");
        assert invalidLogin.isRejected() : "Login should be rejected";
        System.out.println("✓ Login rechazado con password inválido");
        System.out.println("  - Mensaje: " + invalidLogin.getMessage());
        
        // Test duplicate session
        AuthenticationResult duplicate = authService.authenticate("USER", "PASS", "TEST3");
        assert duplicate.isSessionInUse() : "Should detect duplicate session";
        System.out.println("✓ Sesión duplicada detectada");
        System.out.println("  - Mensaje: " + duplicate.getMessage());
        
        // End session
        authService.endSession("USER");
        System.out.println("✓ Sesión terminada");
        
        // Now login should work again
        AuthenticationResult afterLogout = authService.authenticate("USER", "PASS", "TEST4");
        assert afterLogout.isAccepted() : "Login should work after logout";
        System.out.println("✓ Re-login exitoso después de logout");
        
        // Verify login count was updated
        Optional<PersistedUser> userOpt = userRepo.findByUsername("USER");
        assert userOpt.isPresent() : "User should exist";
        System.out.println("✓ Login count: " + userOpt.get().loginCount());
        assert userOpt.get().loginCount() >= 2 : "Login count should be at least 2";
        
        authService.endSession("USER");
        
        System.out.println();
    }
    
    /**
     * Test 5: Persistencia entre reinicios
     */
    private static void testPersistenceAcrossRestarts() {
        System.out.println("═══ Test 5: Persistencia Entre Reinicios ═══");
        
        RocksDBManager dbManager = RocksDBManager.getInstance();
        UserRepository userRepo = new UserRepositoryService(dbManager);
        
        // Create a test user
        String passwordHash = PasswordHasher.hash("persistTest");
        PersistedUser persistUser = PersistedUser.create("persistUser", passwordHash)
            .withMetadata("test", "value")
            .withMetadata("created_by", "test_suite");
        
        userRepo.save(persistUser);
        System.out.println("✓ Usuario de prueba guardado: " + persistUser.username());
        System.out.println("  - Metadata: " + persistUser.metadata());
        
        // Simulate restart by closing and reopening DB
        System.out.println("\nSimulando reinicio del servidor...");
        dbManager.close();
        System.out.println("✓ DB cerrada");
        
        // "Restart" - get new instance (will reopen existing DB)
        RocksDBManager newDbManager = RocksDBManager.getInstance();
        UserRepository newUserRepo = new UserRepositoryService(newDbManager);
        System.out.println("✓ DB reabierta");
        
        // Verify user still exists
        Optional<PersistedUser> afterRestart = newUserRepo.findByUsername("persistUser");
        assert afterRestart.isPresent() : "User should persist after restart";
        System.out.println("✓ Usuario recuperado después de 'reinicio'");
        System.out.println("  - Username: " + afterRestart.get().username());
        System.out.println("  - Created: " + afterRestart.get().createdAt());
        System.out.println("  - Metadata: " + afterRestart.get().metadata());
        
        // Verify metadata persisted
        assert afterRestart.get().metadata().containsKey("test") : "Metadata should persist";
        assert "value".equals(afterRestart.get().metadata().get("test")) : "Metadata values should match";
        System.out.println("✓ Metadata persistió correctamente");
        
        // Verify password still works
        boolean passwordWorks = PasswordHasher.verify("persistTest", afterRestart.get().passwordHash());
        assert passwordWorks : "Password should still work";
        System.out.println("✓ Password verificado después de 'reinicio'");
        
        // Cleanup
        newUserRepo.delete("persistUser");
        System.out.println("✓ Usuario de prueba eliminado");
        
        System.out.println();
    }
}
