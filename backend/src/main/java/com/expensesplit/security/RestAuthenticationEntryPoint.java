package com.expensesplit.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Responde 401 cuando la peticion no lleva una identidad valida.
 *
 * <p>Antes, una peticion sin token o con un token manipulado acababa en 403.
 * Ese codigo significa "se quien eres, pero no puedes"; aqui el problema es
 * justamente que no se sabe quien es. La distincion no es cosmetica: el
 * cliente reacciona distinto a cada uno. Ante un 401 renueva credenciales o
 * lleva al login; ante un 403 no tiene nada que renovar, y reintentar el
 * refresco en bucle es un fallo tipico cuando el servidor los confunde.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // El filtro JWT deja aqui el motivo concreto cuando lo conoce.
        JwtAuthFilter.AuthFailure failure = JwtAuthFilter.failureOf(request);

        // RFC 6750: una respuesta 401 sobre Bearer debe indicar el esquema y,
        // si procede, el codigo de error.
        response.setHeader("WWW-Authenticate", failure.challenge());

        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, failure.message());
    }
}
