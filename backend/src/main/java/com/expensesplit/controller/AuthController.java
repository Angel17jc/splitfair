package com.expensesplit.controller;

import com.expensesplit.dto.request.LoginRequest;
import com.expensesplit.dto.request.RegisterRequest;
import com.expensesplit.dto.response.AuthResponse;
import com.expensesplit.security.AuthRateLimiter;
import com.expensesplit.security.RateLimitService;
import com.expensesplit.security.RefreshCookie;
import com.expensesplit.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

/**
 * Alta, acceso y renovacion de credenciales.
 *
 * <p>El access token se devuelve en el cuerpo y el cliente lo guarda en
 * memoria. El refresh token no aparece en ninguna respuesta: viaja en una
 * cookie HttpOnly que pone este controlador y que el navegador solo envia de
 * vuelta a {@code /api/auth}. Ver {@link RefreshCookie}.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;
    private final RefreshCookie refreshCookie;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una cuenta y abre sesion")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request,
                                   HttpServletRequest http,
                                   HttpServletResponse response) {
        rateLimiter.enforce(RateLimitService.Scope.REGISTER, http, request.getEmail());
        return entregar(authService.register(request), response);
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesion")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                                HttpServletRequest http,
                                HttpServletResponse response) {
        // Antes de cualquier comprobacion de contrasena: sin este freno, el
        // login es fuerza bruta gratis.
        rateLimiter.enforce(RateLimitService.Scope.LOGIN, http, request.getEmail());
        return entregar(authService.login(request), response);
    }

    /**
     * Renueva el access token a partir de la cookie de sesion. El refresh
     * token presentado queda invalidado y se sella uno nuevo, de modo que
     * cada uno sirve una sola vez.
     *
     * <p>No lleva cuerpo: la credencial la aporta el navegador. Asi el
     * refresco tampoco depende de que el cliente haya sabido guardar el token
     * en algun sitio.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Renueva el access token con la cookie de sesion")
    public AuthResponse refresh(HttpServletRequest http, HttpServletResponse response) {
        String token = refreshCookie.read(http)
                // Sin cookie no hay nada que renovar. Es un 401 y no un 400
                // porque para el cliente significa lo mismo que un token
                // caducado: hay que volver a iniciar sesion.
                .orElseThrow(() -> new BadCredentialsException("No hay sesion que renovar"));

        return entregar(authService.refresh(token), response);
    }

    /**
     * Cierra la sesion. Responde 204 tanto si habia cookie como si no:
     * cerrar sesion es idempotente y no debe servir para averiguar que
     * tokens son validos.
     *
     * <p>La cookie se borra siempre, incluso si el token ya no existia. Si
     * solo se borrara al revocar con exito, el navegador se quedaria enviando
     * una credencial muerta en cada intento de refresco.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cierra la sesion y borra la cookie")
    public void logout(HttpServletRequest http, HttpServletResponse response) {
        refreshCookie.read(http).ifPresent(authService::logout);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString());
    }

    /**
     * Sella el refresh token en la cookie y devuelve el cuerpo. Unico camino
     * por el que salen credenciales: emitir un access token sin renovar la
     * cookie dejaria al usuario con una sesion que caduca antes de tiempo.
     */
    private AuthResponse entregar(AuthService.Credentials credenciales,
                                  HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie.issue(credenciales.refreshToken()).toString());
        return credenciales.body();
    }
}
