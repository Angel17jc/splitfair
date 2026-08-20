package com.expensesplit.controller;

import com.expensesplit.dto.request.LoginRequest;
import com.expensesplit.dto.request.RefreshTokenRequest;
import com.expensesplit.dto.request.RegisterRequest;
import com.expensesplit.dto.response.AuthResponse;
import com.expensesplit.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Cambia un refresh token por credenciales nuevas. El token presentado
     * queda invalidado, de modo que cada uno sirve una sola vez.
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.getRefreshToken());
    }

    /**
     * Cierra la sesion. Responde 204 tanto si el token existia como si no:
     * cerrar sesion es idempotente y no debe servir para averiguar que
     * tokens son validos.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
    }
}
