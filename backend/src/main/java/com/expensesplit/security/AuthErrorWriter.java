package com.expensesplit.security;

import com.expensesplit.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Serializa los errores de seguridad con el mismo formato que usa
 * GlobalExceptionHandler.
 *
 * <p>Hace falta una pieza aparte porque estos errores se producen en la
 * cadena de filtros, antes de que exista un controlador: @RestControllerAdvice
 * no llega a verlos. Sin esto, Spring devuelve su pagina de error por
 * defecto, que ademas es HTML, y el cliente recibe dos formatos distintos de
 * error segun donde falle la peticion.
 */
@Component
@RequiredArgsConstructor
public class AuthErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String message) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getWriter(), ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build());
    }
}
