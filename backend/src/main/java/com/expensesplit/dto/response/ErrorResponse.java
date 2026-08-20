package com.expensesplit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Formato unico de error de la API.
 *
 * <p>Los campos opcionales se omiten cuando estan vacios, de modo que una
 * respuesta de validacion lleva {@code fieldErrors} y una de error interno
 * lleva {@code traceId}, sin claves nulas de relleno.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;

    /**
     * Identificador de correlacion. Solo se emite en los errores inesperados:
     * permite que el usuario reporte el fallo y que se localice el stack
     * trace completo en los logs sin exponerlo en la respuesta.
     */
    private final String traceId;

    /** Errores de validacion por campo. */
    private final Map<String, String> fieldErrors;
}
