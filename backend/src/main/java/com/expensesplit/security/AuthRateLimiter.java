package com.expensesplit.security;

import com.expensesplit.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Aplica el limite de peticiones a los endpoints de autenticacion.
 *
 * <p>Se invoca desde el controlador y no desde un filtro por una razon
 * practica: el email viaja en el cuerpo de la peticion, y leerlo dentro de un
 * filtro obliga a envolver el request para poder cachear el flujo de entrada
 * y que el controlador pueda volver a leerlo. En el controlador el DTO ya
 * esta deserializado, y sigue siendo antes de cualquier comprobacion de
 * contrasena, que es lo que importa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimiter {

    private final RateLimitService rateLimitService;

    /**
     * Comprueba el cupo por IP y por email. Si cualquiera de los dos esta
     * agotado, corta la peticion.
     */
    public void enforce(RateLimitService.Scope scope, HttpServletRequest request, String email) {
        String ip = clientIp(request);

        check(scope, "ip:" + ip, "Demasiados intentos desde esta direccion. "
                + "Espera unos minutos antes de volver a probar.");

        if (email != null && !email.isBlank()) {
            check(scope, "email:" + email.trim().toLowerCase(),
                    "Demasiados intentos para esta cuenta. "
                            + "Espera unos minutos antes de volver a probar.");
        }
    }

    private void check(RateLimitService.Scope scope, String key, String mensaje) {
        RateLimitService.Decision decision = rateLimitService.tryConsume(scope, key);

        if (!decision.allowed()) {
            log.warn("Limite de peticiones superado ({} sobre {}), reintentar en {}s",
                    scope, key, decision.retryAfterSeconds());
            throw new TooManyRequestsException(mensaje, decision.retryAfterSeconds());
        }
    }

    /**
     * Direccion del cliente.
     *
     * <p>Se usa getRemoteAddr() y no se lee X-Forwarded-For a mano: esa
     * cabecera la envia el propio cliente y puede falsificarse, con lo que
     * bastaria rotarla para saltarse el limite. Detras de un proxy inverso,
     * la forma correcta es configurar
     * {@code server.forward-headers-strategy=FRAMEWORK}, que hace que Spring
     * la interprete solo cuando procede y deja getRemoteAddr() correcto.
     */
    private String clientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip != null ? ip : "desconocida";
    }
}
