package com.expensesplit.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Marca cada peticion con un identificador y lo deja en el MDC, para que
 * aparezca en todas las lineas de log que produzca.
 *
 * <p>Sin el, un log de produccion es una lista de sucesos sin forma de saber
 * cuales pertenecen a la misma peticion. Con varias en vuelo a la vez —que es
 * la situacion normal— las lineas se intercalan y reconstruir que ocurrio
 * pasa a ser adivinacion.
 *
 * <p>Es el <b>mismo</b> identificador que {@code GlobalExceptionHandler}
 * devuelve al cliente cuando algo falla de forma inesperada. Ahi esta el
 * valor: un usuario reporta ocho caracteres y con ellos se localiza la traza
 * completa. Si cada uno generara el suyo, el que ve el usuario no llevaria a
 * ninguna parte.
 *
 * <p>Se genera aqui y no se acepta del cliente a proposito. Una cabecera
 * entrante es texto arbitrario: podria llegar con saltos de linea y falsear
 * entradas enteras del log, o venir repetida para mezclar peticiones
 * distintas bajo un mismo identificador.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String CLAVE = "traceId";

    /**
     * Ocho caracteres. Es un identificador para correlacionar dentro de una
     * ventana de logs, no una clave global: tiene que caber en un mensaje de
     * error y poder dictarse por telefono sin equivocarse.
     */
    private static final int LONGITUD = 8;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        MDC.put(CLAVE, UUID.randomUUID().toString().substring(0, LONGITUD));
        try {
            chain.doFilter(request, response);
        } finally {
            // Imprescindible, y no una cortesia: el contenedor reutiliza los
            // hilos entre peticiones. Sin esta limpieza, la siguiente peticion
            // que caiga en este hilo heredaria el identificador de la anterior
            // hasta que el filtro lo sobrescriba, y unas lineas de log
            // acabarian atribuidas a la peticion equivocada. Eso es peor que
            // no tener identificador, porque parece correcto.
            MDC.remove(CLAVE);
        }
    }
}
