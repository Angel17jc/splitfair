package com.expensesplit.security;

import com.expensesplit.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el limite de peticiones sobre los endpoints de autenticacion.
 *
 * <p>Cada test usa una IP y unos emails propios: los buckets viven en memoria
 * y son compartidos por todos los tests del mismo contexto de Spring, asi que
 * sin esa separacion un test agotaria el cupo del siguiente.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.rate-limit.login-attempts=3",
        "app.rate-limit.login-window-minutes=15",
        "app.rate-limit.register-attempts=2",
        "app.rate-limit.register-window-minutes=60"
})
class AuthRateLimitTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("tras agotar los intentos se responde 429 con Retry-After")
        void agotarIntentosDevuelve429() throws Exception {
            String ip = "10.0.0.1";
            registrar("ana@test.com", ip);

            // Tres intentos fallidos consumen el cupo.
            for (int i = 0; i < 3; i++) {
                login("ana@test.com", "contrasena-incorrecta", ip)
                        .andExpect(status().isUnauthorized());
            }

            login("ana@test.com", "contrasena-incorrecta", ip)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Demasiados intentos")));
        }

        @Test
        @DisplayName("el limite se aplica aunque la contrasena sea correcta")
        void tambienConCredencialesValidas() throws Exception {
            String ip = "10.0.0.2";
            registrar("beto@test.com", ip);

            for (int i = 0; i < 3; i++) {
                login("beto@test.com", "password123", ip).andExpect(status().isOk());
            }

            // Un atacante que ya conoce la contrasena no debe poder emitir
            // sesiones sin freno.
            login("beto@test.com", "password123", ip).andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("Retry-After indica una espera positiva en segundos")
        void retryAfterEsUtil() throws Exception {
            String ip = "10.0.0.3";
            registrar("carla@test.com", ip);

            for (int i = 0; i < 3; i++) {
                login("carla@test.com", "mal", ip);
            }

            String retryAfter = login("carla@test.com", "mal", ip)
                    .andExpect(status().isTooManyRequests())
                    .andReturn().getResponse().getHeader("Retry-After");

            // Devolver 0 invitaria a reintentar de inmediato y volver a
            // chocar con el limite.
            assertThat(Long.parseLong(retryAfter)).isPositive();
        }
    }

    @Nested
    @DisplayName("Las dos dimensiones del limite")
    class DosDimensiones {

        @Test
        @DisplayName("por IP: frena probar muchas cuentas desde una sola maquina")
        void limitePorIp() throws Exception {
            String ip = "10.0.1.1";

            // Password spraying: una contrasena habitual contra cuentas
            // distintas, sin repetir email jamas.
            for (int i = 0; i < 3; i++) {
                login("victima" + i + "@test.com", "Password123", ip);
            }

            // El cupo por email nunca se habria agotado, porque cada intento
            // usa uno diferente. Es el limite por IP el que corta.
            login("victima99@test.com", "Password123", ip)
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("por email: frena una botnet contra una sola cuenta")
        void limitePorEmail() throws Exception {
            registrar("objetivo@test.com", "10.0.2.1");

            // Fuerza bruta distribuida: cada intento desde una IP distinta,
            // de modo que el limite por IP nunca se activa.
            for (int i = 0; i < 3; i++) {
                login("objetivo@test.com", "intento" + i, "10.0.2." + (100 + i));
            }

            login("objetivo@test.com", "otro-intento", "10.0.2.200")
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("una IP agotada no afecta a otra distinta")
        void ipsIndependientes() throws Exception {
            registrar("diego@test.com", "10.0.3.1");

            for (int i = 0; i < 4; i++) {
                login("diego@test.com", "mal", "10.0.3.1");
            }

            // Otro usuario legitimo desde otra red no debe quedar bloqueado
            // por culpa del anterior.
            registrar("elena@test.com", "10.0.3.99");
            login("elena@test.com", "password123", "10.0.3.99")
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Registro")
    class Registro {

        @Test
        @DisplayName("tiene su propio cupo, independiente del de login")
        void cupoIndependiente() throws Exception {
            String ip = "10.0.4.1";

            registrar("uno@test.com", ip).andExpect(status().isCreated());
            registrar("dos@test.com", ip).andExpect(status().isCreated());

            // El cupo de registro es de 2 en esta clase.
            registrar("tres@test.com", ip).andExpect(status().isTooManyRequests());

            // El de login sigue intacto: son contadores separados.
            login("uno@test.com", "password123", ip).andExpect(status().isOk());
        }
    }

    // --- utilidades ---

    private ResultActions registrar(String email, String ip) throws Exception {
        return mvc.perform(desde(post("/api/auth/register"), ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Test","email":"%s","password":"password123"}
                        """.formatted(email)));
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(desde(post("/api/auth/login"), ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    /** Fija la IP de origen: MockMvc usa 127.0.0.1 para todo por defecto. */
    private MockHttpServletRequestBuilder desde(MockHttpServletRequestBuilder builder, String ip) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }
}
