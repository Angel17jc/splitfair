package com.expensesplit.service;

import com.expensesplit.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

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
    private String refrescoInicial;

    @BeforeEach
    void registrarUsuario() throws Exception {
        MockHttpServletResponse alta = registrar("Ana", "ana@test.com");
        credenciales = json.readTree(alta.getContentAsString());
        refrescoInicial = refrescoDe(alta);
    }

    @Nested
    @DisplayName("Emision")
    class Emision {

        @Test
        @DisplayName("el registro entrega access token en el cuerpo y refresh en la cookie")
        void registroEntregaAmbosTokens() {
            assertThat(credenciales.get("accessToken").asText()).isNotBlank();
            assertThat(credenciales.get("expiresIn").asLong()).isEqualTo(900);
            assertThat(refrescoInicial).isNotBlank();
        }

        @Test
        @DisplayName("el refresh token no aparece en el cuerpo de la respuesta")
        void elCuerpoNoLlevaElRefresh() {
            // Es el punto entero de la cookie HttpOnly. Si el token viniera
            // ademas en el JSON, cualquier XSS que lea la respuesta del login
            // se llevaria una credencial de treinta dias y la proteccion
            // quedaria en pura decoracion.
            assertThat(credenciales.fieldNames()).toIterable()
                    .containsExactlyInAnyOrder("accessToken", "expiresIn", "userId", "name", "email");
        }

        @Test
        @DisplayName("la cookie es HttpOnly, acotada a /api/auth y dura lo que el token")
        void laCookieEstaEndurecida() throws Exception {
            Cookie cookie = registrar("Beto", "beto@test.com").getCookie(COOKIE_REFRESCO);

            // HttpOnly es lo que impide leerla desde JavaScript; sin eso la
            // cookie no aporta nada sobre localStorage.
            assertThat(cookie.isHttpOnly()).isTrue();
            // Acotarla evita que viaje en el resto de la API, que se autentica
            // con Authorization y no la necesita.
            assertThat(cookie.getPath()).isEqualTo("/api/auth");
            // Si caducara despues que el token, el navegador seguiria enviando
            // una credencial muerta y el usuario veria un 401 inexplicable.
            assertThat(cookie.getMaxAge()).isEqualTo((int) Duration.ofDays(30).toSeconds());
        }

        @Test
        @DisplayName("el refresh token nunca se guarda en claro en la base")
        void seGuardaSoloElHash() {
            String enClaro = refrescoInicial;

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
            MockHttpServletResponse renovada = refrescar(refrescoInicial)
                    .andExpect(status().isOk())
                    .andReturn().getResponse();

            JsonNode nuevas = json.readTree(renovada.getContentAsString());

            assertThat(refrescoDe(renovada)).isNotBlank().isNotEqualTo(refrescoInicial);
            assertThat(nuevas.get("accessToken").asText()).isNotBlank();
            assertThat(nuevas.get("email").asText()).isEqualTo("ana@test.com");
        }

        @Test
        @DisplayName("el token rotado deja de servir en el acto")
        void elTokenRotadoMuere() throws Exception {
            refrescar(refrescoInicial).andExpect(status().isOk());

            refrescar(refrescoInicial).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("el token nuevo permanece en la familia del anterior")
        void heredaLaFamilia() throws Exception {
            refrescar(refrescoInicial).andExpect(status().isOk());

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
            JsonNode nuevas = json.readTree(refrescar(refrescoInicial)
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
            String robado = refrescoInicial;

            // La victima refresca con normalidad y obtiene uno nuevo.
            String vigente = refrescoDe(refrescar(robado)
                    .andExpect(status().isOk())
                    .andReturn().getResponse());

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
            String sesionA = refrescoInicial;
            String sesionB = refrescoDe(iniciarSesion());

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
            cerrarSesion(refrescoInicial).andExpect(status().isNoContent());

            refrescar(refrescoInicial).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("cerrar sesion borra la cookie del navegador")
        void cerrarSesionBorraLaCookie() throws Exception {
            Cookie borrada = cerrarSesion(refrescoInicial)
                    .andExpect(status().isNoContent())
                    .andReturn().getResponse().getCookie(COOKIE_REFRESCO);

            // Max-Age 0 es la orden de borrado. Sin ella el navegador
            // seguiria enviando una credencial ya revocada en cada refresco.
            assertThat(borrada).isNotNull();
            assertThat(borrada.getMaxAge()).isZero();
            assertThat(borrada.getValue()).isEmpty();
            // Debe repetir el path del original: si no coincide, el navegador
            // anade una segunda cookie en vez de sustituir la que habia.
            assertThat(borrada.getPath()).isEqualTo("/api/auth");
        }

        @Test
        @DisplayName("cerrar sesion es idempotente y no delata que tokens existen")
        void cerrarSesionEsIdempotente() throws Exception {
            cerrarSesion(refrescoInicial).andExpect(status().isNoContent());
            cerrarSesion(refrescoInicial).andExpect(status().isNoContent());
            cerrarSesion("token-que-nunca-existio").andExpect(status().isNoContent());

            // Y sin cookie ninguna: cerrar sesion no debe delatar si habia una.
            mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
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
        @DisplayName("refrescar sin cookie responde 401, no 400")
        void sinCookie() throws Exception {
            // Para el cliente significa lo mismo que un token caducado: hay
            // que volver a iniciar sesion. Un 400 le haria tratarlo como un
            // error de programacion suyo y no redirigir al login.
            mvc.perform(post("/api/auth/refresh"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("una cookie vacia se trata como si no hubiera sesion")
        void cookieVacia() throws Exception {
            refrescar("").andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("el access token no sirve como refresh token")
        void elAccessTokenNoEsRefreshToken() throws Exception {
            refrescar(credenciales.get("accessToken").asText())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un refresco rechazado no sella cookie nueva")
        void elRechazoNoSellaCookie() throws Exception {
            MockHttpServletResponse respuesta = refrescar("no-es-un-token-valido")
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse();

            // Sellar una cookie en un refresco fallido dejaria al navegador
            // con una credencial que nunca existio en la base.
            assertThat(respuesta.getCookie(COOKIE_REFRESCO)).isNull();
        }

        @Test
        @DisplayName("un refresh token no vale como credencial de la API")
        void elRefreshTokenNoAutorizaPeticiones() throws Exception {
            mvc.perform(get("/api/groups/1")
                            .header("Authorization", "Bearer " + refrescoInicial))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Recorrido completo")
    class RecorridoCompleto {

        @Test
        @DisplayName("registro, login, uso, refresco, uso y cierre de sesion")
        void deExtremoAExtremo() throws Exception {
            // 1. Iniciar sesion con las credenciales del registro.
            MockHttpServletResponse inicio = iniciarSesion();
            String access = json.readTree(inicio.getContentAsString()).get("accessToken").asText();
            String refresh = refrescoDe(inicio);

            // 2. El access token da acceso a la API.
            long grupo = crearGrupo(access);
            leerGrupo(access, grupo).andExpect(status().isOk());

            // 3. Refrescar entrega credenciales nuevas.
            MockHttpServletResponse renovada = refrescar(refresh)
                    .andExpect(status().isOk())
                    .andReturn().getResponse();
            String accessNuevo = json.readTree(renovada.getContentAsString())
                    .get("accessToken").asText();
            String refreshNuevo = refrescoDe(renovada);

            // 4. Las credenciales renovadas siguen dando acceso al mismo grupo.
            leerGrupo(accessNuevo, grupo).andExpect(status().isOk());

            // 5. Cerrar sesion invalida la cadena.
            cerrarSesion(refreshNuevo).andExpect(status().isNoContent());
            refrescar(refreshNuevo).andExpect(status().isUnauthorized());

            // 6. El access token emitido antes del cierre sigue siendo valido
            //    hasta que caduque: es sin estado y no se puede revocar. Esa
            //    es la contrapartida aceptada al elegir 15 minutos de vida.
            leerGrupo(accessNuevo, grupo).andExpect(status().isOk());
        }

        private long crearGrupo(String access) throws Exception {
            String body = mvc.perform(post("/api/groups")
                            .header("Authorization", "Bearer " + access)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Piso","description":"test"}
                                    """))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            return json.readTree(body).get("id").asLong();
        }

        private ResultActions leerGrupo(String access, long grupo) throws Exception {
            return mvc.perform(get("/api/groups/{id}", grupo)
                    .header("Authorization", "Bearer " + access));
        }
    }

    // --- utilidades ---

    private MockHttpServletResponse registrar(String nombre, String email) throws Exception {
        return mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"password123"}
                                """.formatted(nombre, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
    }

    private MockHttpServletResponse iniciarSesion() throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@test.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    /** Refresca como lo haria el navegador: la credencial va en la cookie. */
    private ResultActions refrescar(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(cookieDeSesion(refreshToken)));
    }

    private ResultActions cerrarSesion(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/logout").cookie(cookieDeSesion(refreshToken)));
    }
}
