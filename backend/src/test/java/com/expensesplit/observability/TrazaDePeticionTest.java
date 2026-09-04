package com.expensesplit.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.expensesplit.AbstractIntegrationTest;
import com.expensesplit.dto.response.ErrorResponse;
import com.expensesplit.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * El identificador de peticion: que llegue al log, que sea el mismo que ve el
 * usuario, y que no se quede pegado al hilo.
 *
 * <p>Es la pieza que convierte un reporte de usuario —"me dio error"— en una
 * traza concreta. Si falla cualquiera de las tres cosas, el identificador
 * sigue apareciendo y aparentando funcionar, que es lo que lo hace facil de
 * romper sin enterarse.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // Un solo intento, para que el segundo login provoque el 429 y con el
        // la unica linea de log garantizada dentro de una peticion. Una
        // contrasena incorrecta, por si sola, no registra nada.
        "app.rate-limit.login-attempts=1",
        "app.rate-limit.login-window-minutes=15"
})
class TrazaDePeticionTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private GlobalExceptionHandler manejador;

    private ListAppender<ILoggingEvent> capturados;
    private Logger raiz;

    @BeforeEach
    void engancharCapturaDeLogs() {
        raiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        capturados = new ListAppender<>();
        capturados.start();
        raiz.addAppender(capturados);
    }

    @AfterEach
    void soltarCapturaDeLogs() {
        raiz.detachAppender(capturados);
        MDC.clear();
    }

    @Test
    @DisplayName("las lineas de log de una peticion llevan su identificador")
    void elLogLlevaElIdentificador() throws Exception {
        // Agotar el cupo deja una linea de aviso en el log, dentro de la
        // peticion. Es el camino mas corto a un log garantizado sin provocar
        // un error inesperado ni depender del nivel de traza configurado.
        intentarLogin();
        intentarLogin();

        List<ILoggingEvent> deLaPeticion = capturados.list.stream()
                .filter(e -> e.getMDCPropertyMap().containsKey("traceId"))
                .toList();

        // Sin identificador, un log de produccion es una lista de sucesos sin
        // forma de saber cuales pertenecen a la misma peticion.
        assertThat(deLaPeticion)
                .as("ninguna linea de log de la peticion llevaba traceId")
                .isNotEmpty();
        assertThat(deLaPeticion.getFirst().getMDCPropertyMap().get("traceId"))
                .hasSize(8);
    }

    @Test
    @DisplayName("el identificador no sobrevive a la peticion")
    void seLimpiaAlTerminar() throws Exception {
        intentarLogin();

        // MockMvc atiende la peticion en este mismo hilo, igual que el
        // contenedor reutiliza los suyos entre peticiones. Si el filtro no
        // limpiara el MDC, la siguiente peticion que cayera en este hilo
        // heredaria este identificador y unas lineas quedarian atribuidas a
        // la peticion equivocada: peor que no tenerlo, porque parece correcto.
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("el identificador que ve el usuario es el que esta en el log")
    void laRespuestaReutilizaElDelLog() {
        MDC.put("traceId", "abc12345");

        ResponseEntity<ErrorResponse> respuesta = manejador.handleUnexpected(
                new IllegalStateException("fallo simulado"),
                new MockHttpServletRequest("GET", "/api/groups"));

        // Si el manejador generase uno nuevo, el usuario reportaria ocho
        // caracteres que no aparecen en ninguna linea de log y la traza seria
        // imposible de localizar. Es el unico punto donde el identificador
        // sale al exterior, asi que es donde tiene que coincidir.
        assertThat(respuesta.getBody().getTraceId()).isEqualTo("abc12345");
        assertThat(respuesta.getBody().getMessage()).contains("abc12345");
    }

    private void intentarLogin() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"nadie@test.com","password":"incorrecta"}
                        """));
    }
}
