package com.expensesplit.controller;

import com.expensesplit.dto.request.LoginRequest;
import com.expensesplit.dto.request.RefreshTokenRequest;
import com.expensesplit.dto.request.RegisterRequest;
import com.expensesplit.dto.response.AuthResponse;
import com.expensesplit.security.AuthRateLimiter;
import com.expensesplit.security.RateLimitService;
import com.expensesplit.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request,
                                   HttpServletRequest http) {
        rateLimiter.enforce(RateLimitService.Scope.REGISTER, http, request.getEmail());
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                                HttpServletRequest http) {
        // Antes de cualquier comprobacion de contrasena: sin este freno, el
        // login es fuerza bruta gratis.
        rateLimiter.enforce(RateLimitService.Scope.LOGIN, http, request.getEmail());
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
