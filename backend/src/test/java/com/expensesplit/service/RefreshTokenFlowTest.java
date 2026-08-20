package com.expensesplit.service;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ciclo de vida completo de los refresh token: emision, rotacion, deteccion
 * de reutilizacion y cierre de sesion.
 */
@AutoConfigureMockMvc
class RefreshTokenFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private JsonNode credenciales;

    @BeforeEach
    void registrarUsuario() throws Exception {
        credenciales = registrar("Ana", "ana@test.com");
    }

    @Nested
    @DisplayName("Emision")
    class Emision {

        @Test
        @DisplayName("el registro entrega access token, refresh token y su vigencia")
        void registroEntregaAmbosTokens() {
            assertThat(credenciales.get("accessToken").asText()).isNotBlank();
            assertThat(credenciales.get("refreshToken").asText()).isNotBlank();
            assertThat(credenciales.get("expiresIn").asLong()).isEqualTo(900);
        }

        @Test
        @DisplayName("el refresh token nunca se guarda en claro en la base")
        void seGuardaSoloElHash() {
            String enClaro = credenciales.get("refreshToken").asText();

            Integer coincidencias = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?",
                    Integer.class, enClaro);

            // Lo almacenado es el SHA-256, de 64 caracteres hexadecimales.
            assertThat(coincidencias).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT token_hash FROM refresh_tokens LIMIT 1", String.class))
                    .hasSize(64)
                    .matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("dos inicios de sesion abren familias independientes")
        void loginsIndependientes() throws Exception {
            iniciarSesion();
            iniciarSesion();

            Integer familias = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT family_id) FROM refresh_tokens", Integer.class);

            // Uno por el registro y dos por los logins: cerrar sesion en un
            // dispositivo no debe afectar a los demas.
            assertThat(familias).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Rotacion")
    class Rotacion {

        @Test
        @DisplayName("refrescar entrega credenciales nuevas")
        void refrescarEntregaCredencialesNuevas() throws Exception {
            String refreshOriginal = credenciales.get("refreshToken").asText();

            JsonNode nuevas = json.readTree(refrescar(refreshOriginal)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            assertThat(nuevas.get("refreshToken").asText()).isNotEqualTo(refreshOriginal);
            assertThat(nuevas.get("accessToken").asText()).isNotBlank();
            assertThat(nuevas.get("email").asText()).isEqualTo("ana@test.com");
        }

        @Test
        @DisplayName("el token rotado deja de servir en el acto")
        void elTokenRotadoMuere() throws Exception {
            String original = credenciales.get("refreshToken").asText();

            refrescar(original).andExpect(status().isOk());

            refrescar(original).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("el token nuevo permanece en la familia del anterior")
        void heredaLaFamilia() throws Exception {
            refrescar(credenciales.get("refreshToken").asText()).andExpect(status().isOk());

            Integer familias = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT family_id) FROM refresh_tokens", Integer.class);
            Integer tokens = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM refresh_tokens", Integer.class);

            assertThat(tokens).isEqualTo(2);
            assertThat(familias).isEqualTo(1);
        }

        @Test
        @DisplayName("el access token renovado sirve para llamar a la API")
        void elAccessTokenNuevoFunciona() throws Exception {
            JsonNode nuevas = json.readTree(refrescar(credenciales.get("refreshToken").asText())
                    .andReturn().getResponse().getContentAsString());

            mvc.perform(post("/api/groups")
                            .header("Authorization", "Bearer " + nuevas.get("accessToken").asText())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Piso","description":"test"}
                                    """))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Deteccion de reutilizacion")
    class Reutilizacion {

        @Test
        @DisplayName("reutilizar un token robado revoca la cadena entera")
        void reutilizacionRevocaLaFamilia() throws Exception {
            // El atacante se hace con una copia del token de la victima.
            String robado = credenciales.get("refreshToken").asText();

            // La victima refresca con normalidad y obtiene uno nuevo.
            JsonNode deLaVictima = json.readTree(refrescar(robado)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            String vigente = deLaVictima.get("refreshToken").asText();

            // El atacante intenta usar su copia, ya rotada.
            refrescar(robado).andExpect(status().isUnauthorized());

            // Esa senal invalida toda la familia: el token de la victima, que
            // hasta ahora era legitimo, tambien deja de servir. Es
            // deliberado: no se puede distinguir a la victima del atacante,
            // asi que se corta el acceso a ambos y la victima detecta el
            // problema al verse obligada a autenticarse de nuevo.
            refrescar(vigente).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("la revocacion no afecta a las sesiones de otros dispositivos")
        void otrasSesionesSobreviven() throws Exception {
            String sesionA = credenciales.get("refreshToken").asText();
            String sesionB = json.readTree(iniciarSesion()).get("refreshToken").asText();

            // Se compromete la sesion A.
            refrescar(sesionA).andExpect(status().isOk());
            refrescar(sesionA).andExpect(status().isUnauthorized());

            // La sesion B pertenece a otra familia y sigue viva.
            refrescar(sesionB).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Cierre de sesion")
    class Logout {

        @Test
        @DisplayName("cerrar sesion invalida el refresh token")
        void cerrarSesionInvalidaElToken() throws Exception {
            String refresh = credenciales.get("refreshToken").asText();

            cerrarSesion(refresh).andExpect(status().isNoContent());

            refrescar(refresh).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("cerrar sesion es idempotente y no delata que tokens existen")
        void cerrarSesionEsIdempotente() throws Exception {
            String refresh = credenciales.get("refreshToken").asText();

            cerrarSesion(refresh).andExpect(status().isNoContent());
            cerrarSesion(refresh).andExpect(status().isNoContent());
            cerrarSesion("token-que-nunca-existio").andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Tokens invalidos")
    class Invalidos {

        @Test
        @DisplayName("un refresh token inventado se rechaza con 401")
        void tokenInventado() throws Exception {
            refrescar("no-es-un-token-valido").andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un refresh token vacio falla la validacion del cuerpo")
        void tokenVacio() throws Exception {
            refrescar("").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.refreshToken").exists());
        }

        @Test
        @DisplayName("el access token no sirve como refresh token")
        void elAccessTokenNoEsRefreshToken() throws Exception {
            refrescar(credenciales.get("accessToken").asText())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un refresh token no vale como credencial de la API")
        void elRefreshTokenNoAutorizaPeticiones() throws Exception {
            mvc.perform(get("/api/groups/1")
                            .header("Authorization", "Bearer " + credenciales.get("refreshToken").asText()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // --- utilidades ---

    private JsonNode registrar(String nombre, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"password123"}
                                """.formatted(nombre, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String iniciarSesion() throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@test.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions refrescar(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))));
    }

    private ResultActions cerrarSesion(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))));
    }
}
