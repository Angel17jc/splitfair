package com.expensesplit.security;

import com.expensesplit.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El limite de intentos de login sigue contando por usuario final cuando la
 * aplicacion esta detras de un proxy inverso.
 *
 * <p>En produccion nginx sirve la aplicacion y reenvia /api al backend, asi
 * que <b>todas</b> las peticiones llegan con la misma direccion de origen: la
 * del contenedor de nginx. Sin
 * {@code server.forward-headers-strategy=FRAMEWORK}, el limitador agruparia a
 * todo el mundo en un unico cupo y cinco intentos fallidos de cualquier
 * persona dejarian sin poder entrar a los demas. No seria solo una proteccion
 * debilitada: seria una denegacion de servicio contra los usuarios legitimos,
 * y ademas intermitente, porque depende de cuanta gente falle a la vez.
 *
 * <p>La estrategia se activa <b>solo en el perfil prod</b>, y no por descuido:
 * X-Forwarded-For la envia quien hace la peticion. Si el backend fuera
 * accesible directamente, bastaria rotar esa cabecera para saltarse el limite.
 * En produccion el puerto del backend no se publica y solo nginx —que la
 * reescribe— puede alcanzarlo. Ver docker-compose.prod.yml.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "server.forward-headers-strategy=FRAMEWORK",
        "app.rate-limit.login-attempts=3",
        "app.rate-limit.login-window-minutes=15",
        "app.rate-limit.register-attempts=50",
        "app.rate-limit.register-window-minutes=60"
})
class ProxyRateLimitTest extends AbstractIntegrationTest {

    /** Direccion del contenedor de nginx: la misma para todas las peticiones. */
    private static final String PROXY = "172.20.0.5";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("un usuario que agota su cupo no bloquea a los demas detras del mismo proxy")
    void elCupoSigueSiendoPorUsuarioFinal() throws Exception {
        registrar("atacante@test.com", "203.0.113.10");
        registrar("legitima@test.com", "203.0.113.99");

        // Alguien quema sus tres intentos desde su propia direccion publica.
        for (int i = 0; i < 3; i++) {
            login("atacante@test.com", "mal", "203.0.113.10")
                    .andExpect(status().isUnauthorized());
        }

        // Otra persona, otra direccion publica, el mismo proxy por delante.
        // Este es el caso que se rompe sin la estrategia de cabeceras: sin
        // ella ambas comparten la IP de nginx y esta respuesta seria 429.
        login("legitima@test.com", "password123", "203.0.113.99")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("el limite se sigue aplicando: no se ha desactivado, se ha reubicado")
    void elLimiteSigueVigente() throws Exception {
        registrar("insistente@test.com", "203.0.113.20");

        for (int i = 0; i < 3; i++) {
            login("insistente@test.com", "mal", "203.0.113.20")
                    .andExpect(status().isUnauthorized());
        }

        // Sin esta comprobacion, el test anterior tambien pasaria con el
        // limitador apagado del todo.
        login("insistente@test.com", "mal", "203.0.113.20")
                .andExpect(status().isTooManyRequests());
    }

    // --- utilidades ---

    private ResultActions registrar(String email, String clienteReal) throws Exception {
        return mvc.perform(comoDetrasDeNginx(post("/api/auth/register"), clienteReal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Test","email":"%s","password":"password123"}
                        """.formatted(email)));
    }

    private ResultActions login(String email, String password, String clienteReal) throws Exception {
        return mvc.perform(comoDetrasDeNginx(post("/api/auth/login"), clienteReal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    /**
     * Reproduce lo que ve el backend en produccion: la conexion viene de
     * nginx, y la direccion real del cliente solo esta en X-Forwarded-For,
     * que es exactamente lo que anade nginx.conf.
     */
    private MockHttpServletRequestBuilder comoDetrasDeNginx(
            MockHttpServletRequestBuilder builder, String clienteReal) {
        return builder
                .header("X-Forwarded-For", clienteReal)
                .header("X-Forwarded-Proto", "https")
                .with(request -> {
                    request.setRemoteAddr(PROXY);
                    return request;
                });
    }
}
