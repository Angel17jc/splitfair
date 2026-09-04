package com.expensesplit.exception;

import com.expensesplit.dto.response.ErrorResponse;
import com.expensesplit.observability.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Traduce las excepciones de la aplicacion a respuestas HTTP con un formato
 * unico.
 *
 * <p>Principio que gobierna esta clase: <b>el cliente recibe lo que necesita
 * para corregir su peticion; el log recibe lo que hace falta para depurar</b>.
 * La version anterior devolvia {@code ex.getMessage()} de cualquier excepcion
 * no controlada, lo que filtraba sentencias SQL, nombres de tablas, rutas del
 * sistema de ficheros y detalles de la infraestructura a quien enviara una
 * peticion malformada.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // --- Errores esperados: el mensaje es seguro y util para el cliente ---

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // --- Errores de seguridad ---

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex,
                                                               HttpServletRequest request) {
        // Mensaje deliberadamente vago: distinguir "email inexistente" de
        // "contrasena incorrecta" permite enumerar cuentas registradas.
        log.debug("Autenticacion fallida en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Credenciales invalidas", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta accion", request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex,
                                                                HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                // RFC 6585: el cliente necesita saber cuanto esperar, o
                // reintentara de inmediato y volvera a chocar con el limite.
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.TOO_MANY_REQUESTS.value())
                        .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .build());
    }

    // --- Errores de forma de la peticion ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> fieldErrors.putIfAbsent(err.getField(), err.getDefaultMessage()));

        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("La peticion contiene campos invalidos")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                               HttpServletRequest request) {
        // El mensaje original incluye fragmentos del JSON recibido y nombres
        // de clases Java; no se propaga.
        log.debug("Cuerpo ilegible en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la peticion no es un JSON valido", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "El parametro '" + ex.getName() + "' no tiene un valor valido", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                              HttpServletRequest request) {
        // El detalle nombra tablas, columnas y restricciones: se queda en el log.
        log.warn("Violacion de integridad en {}", request.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT,
                "La operacion entra en conflicto con datos existentes", request);
    }

    /**
     * Ruta desconocida.
     *
     * <p>Sin este manejador la excepcion cae en la red de seguridad de abajo y
     * sale como <b>500</b>, con su stack trace a nivel ERROR. Se descubrio
     * escribiendo los tests de las sondas de estado, pidiendo un endpoint de
     * Actuator no publicado.
     *
     * <p>Son dos problemas distintos. El cliente recibe "ha ocurrido un error
     * interno" cuando lo cierto es que ahi no hay nada, y un frontend
     * razonable interpreta el 500 como servidor caido. Y el log acumula
     * errores que no lo son, justo en el nivel que se vigila.
     *
     * <p>No lleva traceId: el identificador existe para investigar un fallo
     * interno, y aqui no hay nada que investigar. Tampoco se registra el
     * stack trace, por el mismo motivo.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleRutaDesconocida(NoResourceFoundException ex,
                                                                HttpServletRequest request) {
        log.debug("Ruta no encontrada: {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "La ruta solicitada no existe", request);
    }

    // --- Red de seguridad ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Se reutiliza el identificador que TraceIdFilter puso al empezar la
        // peticion, en vez de generar otro: el que ve el usuario tiene que ser
        // el mismo que aparece en todas las lineas de log de esa peticion, o
        // no sirve para localizar nada. El respaldo cubre el caso de una
        // excepcion levantada antes de que el filtro llegara a ejecutarse.
        String traceId = MDC.get(TraceIdFilter.CLAVE);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }

        // El stack trace completo va al log, junto al identificador que el
        // cliente vera. Es lo que permite atender un reporte de usuario sin
        // haber expuesto nada en la respuesta.
        log.error("[{}] Error no controlado en {} {}",
                traceId, request.getMethod(), request.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("Ha ocurrido un error interno. Si el problema persiste, "
                        + "reporta este identificador: " + traceId)
                .path(request.getRequestURI())
                .traceId(traceId)
                .build());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                 HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build());
    }
}
