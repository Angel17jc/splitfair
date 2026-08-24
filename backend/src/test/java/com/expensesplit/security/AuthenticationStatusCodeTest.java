package com.expensesplit.security;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que la API distingue 401 de 403 y responde siempre en JSON.
 *
 * <p>La distincion no es cosmetica: el cliente reacciona distinto a cada uno.
 * Ante un 401 renueva credenciales o lleva al login; ante un 403 no tiene
 * nada que renovar. Cuando el servidor los confunde, un frontend acaba
 * reintentando el refresco en bucle contra un recurso al que simplemente no
 * tiene derecho.
 *
 * <p>El access token caduca en 1 segundo en esta clase, para poder probar el
 * caso "caducado" sin esperar.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.access-token-expiration-ms=1000")
class AuthenticationStatusCodeTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String accessToken;

    @BeforeEach
    void registrarUsuario() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ana@test.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        accessToken = json.readTree(body).get("accessToken").asText();
    }

    @Nested
    @DisplayName("401 cuando no se sabe quien hace la peticion")
    class NoAutenticado {

        @Test
        @DisplayName("sin cabecera Authorization")
        void sinCabecera() throws Exception {
            mvc.perform(get("/api/groups/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Se requiere autenticacion para acceder a este recurso"));
        }

        @Test
        @DisplayName("con un token manipulado")
        void tokenManipulado() throws Exception {
            mvc.perform(get("/api/groups/1").header("Authorization", "Bearer no.es.un.token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                    .andExpect(jsonPath("$.message").value("El token de acceso no es valido"));
        }

        @Test
        @DisplayName("con la firma alterada")
        void firmaAlterada() throws Exception {
            // Se altera el PRIMER caracter de la firma, no el ultimo.
            //
            // La firma HMAC-SHA256 son 32 bytes, que en base64url ocupan 43
            // caracteres: 43 x 6 = 258 bits para 256 utiles. Los dos ultimos
            // bits se ignoran al decodificar, de modo que cambiar el ultimo
            // caracter puede producir exactamente los mismos 32 bytes y dejar
            // el token perfectamente valido. El primer caracter siempre
            // aporta bits significativos.
            int inicioFirma = accessToken.lastIndexOf('.') + 1;
            char inicial = accessToken.charAt(inicioFirma);
            String alterado = accessToken.substring(0, inicioFirma)
                    + (inicial == 'A' ? 'B' : 'A')
                    + accessToken.substring(inicioFirma + 1);

            mvc.perform(get("/api/groups/1")
                            .header("Authorization", "Bearer " + alterado))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("con un esquema que no es Bearer")
        void esquemaIncorrecto() throws Exception {
            mvc.perform(get("/api/groups/1").header("Authorization", "Basic YWRtaW46YWRtaW4="))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un token caducado se distingue de uno invalido")
        void tokenCaducado() throws Exception {
            // El token de esta clase vive 1 segundo.
            Thread.sleep(1100);

            mvc.perform(get("/api/groups/1").header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("El token de acceso ha caducado"))
                    // El cliente necesita saber que basta con refrescar: es
                    // una situacion normal, no un intento de manipulacion.
                    .andExpect(header().string("WWW-Authenticate",
                            "Bearer error=\"invalid_token\", error_description=\"The access token expired\""));
        }
    }

    @Nested
    @DisplayName("403 cuando la identidad es valida pero no alcanza")
    class SinPermiso {

        @Test
        @DisplayName("acceder a un grupo ajeno con un token legitimo")
        void grupoAjeno() throws Exception {
            // Otra usuaria crea un grupo propio.
            String ajeno = mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Beto","email":"beto@test.com","password":"password123"}
                                    """))
                    .andReturn().getResponse().getContentAsString();

            String body = mvc.perform(post("/api/groups")
                            .header("Authorization", "Bearer " + json.readTree(ajeno).get("accessToken").asText())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Piso de Beto","description":"test"}
                                    """))
                    .andReturn().getResponse().getContentAsString();

            long grupoAjeno = json.readTree(body).get("id").asLong();

            mvc.perform(get("/api/groups/{id}", grupoAjeno)
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    // Un 403 no lleva WWW-Authenticate: no hay nada que
                    // renovar, las credenciales son correctas.
                    .andExpect(header().doesNotExist("WWW-Authenticate"));
        }
    }

    @Nested
    @DisplayName("Las rutas publicas no exigen credenciales")
    class RutasPublicas {

        @Test
        @DisplayName("el login funciona sin token")
        void loginSinToken() throws Exception {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"ana@test.com","password":"password123"}
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("un token invalido no impide usar una ruta publica")
        void rutaPublicaConTokenInvalido() throws Exception {
            // El filtro anota el fallo pero no corta: decidir corresponde a
            // la configuracion de seguridad, y esta ruta es publica.
            mvc.perform(post("/api/auth/login")
                            .header("Authorization", "Bearer basura")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"ana@test.com","password":"password123"}
                                    """))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Formato de los errores de seguridad")
    class Formato {

        @Test
        @DisplayName("un 401 usa el mismo formato que el resto de errores")
        void formatoUnificado() throws Exception {
            mvc.perform(get("/api/groups/1"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.path").value("/api/groups/1"));
        }

        @Test
        @DisplayName("no se filtran detalles internos en los errores de seguridad")
        void sinFiltraciones() throws Exception {
            String cuerpo = mvc.perform(get("/api/groups/1")
                            .header("Authorization", "Bearer no.es.un.token"))
                    .andReturn().getResponse().getContentAsString();

            org.assertj.core.api.Assertions.assertThat(cuerpo)
                    .doesNotContain("org.springframework", "io.jsonwebtoken", "Exception", "com.expensesplit");
        }
    }
}
