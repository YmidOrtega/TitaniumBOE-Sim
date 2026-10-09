package com.boe.simulator.api.middleware;

import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.persistence.model.PersistedUser;
import com.boe.simulator.server.persistence.repository.UserRepository;
import com.boe.simulator.server.persistence.util.PasswordHasher;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationFilterTest {

    private final UserRepository users = mock(UserRepository.class);
    private final Context ctx = mock(Context.class);
    private AuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        AuthenticationService authService = mock(AuthenticationService.class);
        when(authService.getUserRepository()).thenReturn(users);
        filter = new AuthenticationFilter(authService);
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void missingHeader_stopsTheRequestWith401() {
        filter.handle(ctx);

        verify(ctx).status(401);
        verify(ctx).skipRemainingHandlers();
    }

    @Test
    void wrongPassword_stopsTheRequestWith401() {
        when(users.findByUsername("TRD1")).thenReturn(Optional.of(PersistedUser.create("TRD1", PasswordHasher.hash("Pass1234"))));
        when(ctx.header("Authorization")).thenReturn(basic("TRD1", "Wrong999"));

        filter.handle(ctx);

        verify(ctx).status(401);
        verify(ctx).skipRemainingHandlers();
    }

    @Test
    void validCredentials_letTheRequestThrough() {
        when(users.findByUsername("TRD1")).thenReturn(Optional.of(PersistedUser.create("TRD1", PasswordHasher.hash("Pass1234"))));
        when(ctx.header("Authorization")).thenReturn(basic("TRD1", "Pass1234"));

        filter.handle(ctx);

        verify(ctx).attribute("username", "TRD1");
        verify(ctx, never()).skipRemainingHandlers();
        verify(ctx, never()).status(any(Integer.class));
    }
}
