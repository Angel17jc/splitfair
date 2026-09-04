package com.expensesplit.controller;

import com.expensesplit.dto.request.ChangePasswordRequest;
import com.expensesplit.dto.request.DeleteAccountRequest;
import com.expensesplit.dto.request.UpdateProfileRequest;
import com.expensesplit.dto.response.UserResponse;
import com.expensesplit.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Perfil del usuario autenticado.
 *
 * <p>Todas las rutas operan sobre "yo" y no sobre un identificador de la URL.
 * Es intencionado: un endpoint /api/users/{id} obligaria a comprobar en cada
 * llamada que ese id es el del solicitante, y esa comprobacion es
 * exactamente la que se olvida. Sin id en la ruta, no hay nada que olvidar.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getProfile(Authentication authentication) {
        return userService.getProfile(authentication.getName());
    }

    @PatchMapping("/me")
    public UserResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                        Authentication authentication) {
        return userService.updateProfile(authentication.getName(), request);
    }

    /**
     * Cambia la contrasena. Cierra todas las sesiones abiertas, incluida la
     * que ejecuta el cambio, de modo que el cliente debe autenticarse de
     * nuevo despues.
     */
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                 Authentication authentication) {
        userService.changePassword(authentication.getName(), request);
    }

    /**
     * Da de baja la cuenta.
     *
     * <p>DELETE con cuerpo: la contrasena actual no puede ir en la URL, donde
     * quedaria registrada en los logs de acceso del proxy y en el historial
     * del navegador. HTTP admite cuerpo en DELETE y nginx lo reenvia.
     *
     * <p>204 y no 200: no hay nada que devolver, y quien la ejecuta pierde el
     * acceso en ese mismo momento.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@Valid @RequestBody DeleteAccountRequest request,
                              Authentication authentication) {
        userService.deleteAccount(authentication.getName(), request);
    }
}
