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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que un usuario autenticado no puede tocar grupos ajenos.
 *
 * <p>Antes de este control, Spring Security solo exigia un JWT valido. Como
 * los identificadores de grupo son BIGINT secuenciales, cualquier usuario
 * registrado podia recorrer /api/groups/1, /2, /3... y leer la contabilidad
 * completa de todos los grupos de la base.
 *
 * <p>Los tests van por HTTP con MockMvc y no llamando a los servicios
 * directamente: lo que hay que demostrar es que la peticion real se rechaza,
 * incluyendo el paso por la cadena de filtros de seguridad.
 */
@AutoConfigureMockMvc
class GroupAccessControlTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    /** Ana: crea el grupo y es su administradora. */
    private String tokenAna;
    private long idAna;

    /** Mallory: usuaria legitima del sistema, ajena al grupo de Ana. */
    private String tokenMallory;

    /** Beto: miembro del grupo de Ana, con rol MEMBER. */
    private String tokenBeto;
    private long idBeto;

    private long grupoDeAna;

    @BeforeEach
    void prepararEscenario() throws Exception {
        // Cada test parte de una base limpia: al ir por HTTP no hay
        // transaccion de test que revierta los datos automaticamente.
        jdbc.execute("TRUNCATE expense_splits, expenses, settlements, " +
                "group_members, groups, users RESTART IDENTITY CASCADE");

        JsonNode ana = registrar("Ana", "ana@test.com");
        tokenAna = ana.get("token").asText();
        idAna = ana.get("userId").asLong();

        JsonNode beto = registrar("Beto", "beto@test.com");
        tokenBeto = beto.get("token").asText();
        idBeto = beto.get("userId").asLong();

        JsonNode mallory = registrar("Mallory", "mallory@test.com");
        tokenMallory = mallory.get("token").asText();

        grupoDeAna = crearGrupo(tokenAna, "Piso de Ana");
        anadirMiembro(tokenAna, grupoDeAna, idBeto);
        crearGasto(tokenAna, grupoDeAna, "Alquiler", "900.00");
    }

    @Nested
    @DisplayName("Un usuario ajeno al grupo recibe 403 en todos los endpoints")
    class UsuarioAjeno {

        @Test
        @DisplayName("no puede leer el grupo")
        void noPuedeLeerElGrupo() throws Exception {
            mvc.perform(get("/api/groups/{id}", grupoDeAna).header("Authorization", bearer(tokenMallory)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("no puede listar los gastos")
        void noPuedeListarGastos() throws Exception {
            mvc.perform(get("/api/groups/{id}/expenses", grupoDeAna).header("Authorization", bearer(tokenMallory)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("no puede consultar los balances")
        void noPuedeVerBalances() throws Exception {
            mvc.perform(get("/api/groups/{id}/balances", grupoDeAna).header("Authorization", bearer(tokenMallory)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("no puede consultar las liquidaciones sugeridas")
        void noPuedeVerLiquidaciones() throws Exception {
            mvc.perform(get("/api/groups/{id}/settlements", grupoDeAna).header("Authorization", bearer(tokenMallory)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("no puede cargar un gasto al grupo")
        void noPuedeCrearGastos() throws Exception {
            mvc.perform(post("/api/groups/{id}/expenses", grupoDeAna)
                            .header("Authorization", bearer(tokenMallory))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description":"Gasto colado","amount":50.00,"expenseDate":"2026-08-20"}
                                    """))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("no puede meter miembros")
        void noPuedeAnadirMiembros() throws Exception {
            mvc.perform(post("/api/groups/{groupId}/members/{userId}", grupoDeAna, idAna)
                            .header("Authorization", bearer(tokenMallory)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("el rechazo no deja rastro: el gasto colado no existe")
        void elGastoRechazadoNoSePersiste() throws Exception {
            mvc.perform(post("/api/groups/{id}/expenses", grupoDeAna)
                    .header("Authorization", bearer(tokenMallory))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"description":"Gasto colado","amount":50.00,"expenseDate":"2026-08-20"}
                            """));

            Integer colados = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM expenses WHERE description = 'Gasto colado'", Integer.class);
            assertThat(colados).isZero();
        }
    }

    @Nested
    @DisplayName("Los miembros del grupo si tienen acceso")
    class MiembrosLegitimos {

        @Test
        @DisplayName("la administradora lee el grupo y sus gastos")
        void adminAccede() throws Exception {
            mvc.perform(get("/api/groups/{id}", grupoDeAna).header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/groups/{id}/expenses", grupoDeAna).header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("un miembro corriente tambien accede a la informacion")
        void miembroAccede() throws Exception {
            mvc.perform(get("/api/groups/{id}/balances", grupoDeAna).header("Authorization", bearer(tokenBeto)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Separacion de roles dentro del grupo")
    class Roles {

        @Test
        @DisplayName("un MEMBER no puede anadir miembros: eso es de ADMIN")
        void memberNoPuedeAnadirMiembros() throws Exception {
            long idMallory = jdbc.queryForObject(
                    "SELECT id FROM users WHERE email = 'mallory@test.com'", Long.class);

            mvc.perform(post("/api/groups/{groupId}/members/{userId}", grupoDeAna, idMallory)
                            .header("Authorization", bearer(tokenBeto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("la ADMIN si puede")
        void adminSiPuedeAnadirMiembros() throws Exception {
            long idMallory = jdbc.queryForObject(
                    "SELECT id FROM users WHERE email = 'mallory@test.com'", Long.class);

            mvc.perform(post("/api/groups/{groupId}/members/{userId}", grupoDeAna, idMallory)
                            .header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Casos limite")
    class CasosLimite {

        @Test
        @DisplayName("un grupo inexistente devuelve 404, no 403")
        void grupoInexistente() throws Exception {
            mvc.perform(get("/api/groups/{id}", 999_999L).header("Authorization", bearer(tokenAna)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("sin token no se llega siquiera al control de pertenencia")
        void sinToken() throws Exception {
            mvc.perform(get("/api/groups/{id}", grupoDeAna))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("con un token manipulado tampoco")
        void tokenInvalido() throws Exception {
            mvc.perform(get("/api/groups/{id}", grupoDeAna)
                            .header("Authorization", "Bearer no.es.un.token"))
                    .andExpect(status().is4xxClientError());
        }
    }

    // --- utilidades ---

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode registrar(String nombre, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"password123"}
                                """.formatted(nombre, email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(body);
    }

    private long crearGrupo(String token, String nombre) throws Exception {
        String body = mvc.perform(post("/api/groups")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"test"}
                                """.formatted(nombre)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(body).get("id").asLong();
    }

    private void anadirMiembro(String token, long groupId, long userId) throws Exception {
        mvc.perform(post("/api/groups/{groupId}/members/{userId}", groupId, userId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private void crearGasto(String token, long groupId, String descripcion, String importe) throws Exception {
        mvc.perform(post("/api/groups/{id}/expenses", groupId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"%s","amount":%s,"expenseDate":"2026-08-20"}
                                """.formatted(descripcion, importe)))
                .andExpect(status().isOk());
    }
}
